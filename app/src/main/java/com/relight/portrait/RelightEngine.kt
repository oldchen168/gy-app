package com.relight.portrait
import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import kotlin.math.*

/** 人物分析结果：分割蒙版 + 近似法线。所有重打光只是对原像素乘以光照增益，不生成新像素 → 保护身份/五官/纹理。 */
class Analysis(val w:Int,val h:Int,val px:IntArray,val mask:FloatArray,val face:FloatArray,val hair:FloatArray,val nx:FloatArray,val ny:FloatArray,val edge:FloatArray,val segOK:Boolean)

class RelightEngine(ctx:Context){
 private val seg:ImageSegmenter? = try{
  ImageSegmenter.createFromOptions(ctx,ImageSegmenter.ImageSegmenterOptions.builder()
   .setBaseOptions(BaseOptions.builder().setModelAssetPath("selfie_multiclass_256x256.tflite").build())
   .setOutputConfidenceMasks(true).setOutputCategoryMask(false).build())}catch(e:Exception){null}

 fun analyze(src:Bitmap,maxSide:Int):Analysis{
  val s=min(1f,maxSide.toFloat()/max(src.width,src.height))
  val bmp=Bitmap.createScaledBitmap(src,(src.width*s).roundToInt(),(src.height*s).roundToInt(),true).copy(Bitmap.Config.ARGB_8888,false)
  val w=bmp.width;val h=bmp.height;val n=w*h;val px=IntArray(n);bmp.getPixels(px,0,w,0,0,w,h)
  var mask=FloatArray(n){1f};var face=FloatArray(n);var hair=FloatArray(n);var ok=false
  seg?.let{ try{ val r=it.segment(BitmapImageBuilder(bmp).build()); val cm=r.confidenceMasks().get()
   fun up(i:Int):FloatArray{val m=cm[i];val mw=m.width;val mh=m.height;val buf=com.google.mediapipe.framework.image.ByteBufferExtractor.extract(m).asFloatBuffer()
    return FloatArray(n){k->val y=k/w;val x=k%w;buf.get(min(mh-1,y*mh/h)*mw+min(mw-1,x*mw/w))}}
   val bg=up(0);face=up(3);hair=up(1);mask=FloatArray(n){1f-bg[it]};ok=true }catch(_:Exception){} }
  mask=blur(mask,w,h,2);face=blur(face,w,h,3)
  val R=max(4,max(w,h)/40);val vol=blur(mask,w,h,R)
  val lum=FloatArray(n){val c=px[it];(((c shr 16) and 255)*.3f+((c shr 8) and 255)*.59f+(c and 255)*.11f)/255f}
  val lb=blur(lum,w,h,max(2,R/2))
  val nx=FloatArray(n);val ny=FloatArray(n);val edge=FloatArray(n)
  for(y in 1 until h-1)for(x in 1 until w-1){val i=y*w+x;val gx=vol[i+1]-vol[i-1];val gy=vol[i+w]-vol[i-w]
   nx[i]=-gx*R*1.6f-(lb[i+1]-lb[i-1])*R*.6f;ny[i]=-gy*R*1.6f-(lb[i+w]-lb[i-w])*R*.6f;edge[i]=min(1f,hypot(gx,gy)*R*1.2f)}
  return Analysis(w,h,px,mask,face,hair,nx,ny,edge,ok)
 }

 fun relight(a:Analysis,L:Light,protect:Float,chroma:Float):Bitmap{
  val w=a.w;val h=a.h;val n=w*h;val out=IntArray(n)
  val az=Math.toRadians(L.az.toDouble()).toFloat();val el=Math.toRadians(L.el.toDouble()).toFloat()
  val lx=sin(az)*cos(el);var ly=-cos(az)*cos(el);var lz=sin(abs(el))*.9f+.1f
  if(el<0){ly=abs(cos(az))*cos(el)*.3f+sin(-el);lz=.35f}
  val reach=.55f+.45f*cos(el);val lpx=.5f+sin(az)*reach*.75f
  val lpy=.42f+(if(el<0).55f else -cos(az)*reach*.6f-sin(el)*.25f)
  val lc=L.color?:kelvin(L.kelvin);val rc=L.rimColor?:lc;val bc=L.bgColor?:floatArrayOf(1f,1f,1f)
  val ac=L.ambColor?:FloatArray(3){.75f+.25f*bc[it]}
  val I=L.intensity;val amb=L.ambient;val sh=L.shadow;val rim=L.rim;val fo=L.falloff;val soft=L.soft;val cast=L.cast;val glow=L.glow;val vig=L.vig
  val lin=FloatArray(256){(it/255.0).pow(2.2).toFloat()}
  val G=FloatArray(n);val RM=FloatArray(n);val PT=FloatArray(n);val AT=FloatArray(n);val gam=1+sh*2.2f
  for(y in 0 until h)for(x in 0 until w){val i=y*w+x;val u=x.toFloat()/w;val v=y.toFloat()/h
   var ax=a.nx[i];var ay=a.ny[i];val ln=sqrt(ax*ax+ay*ay+1f);ax/=ln;ay/=ln;val az2=1f/ln
   var d=ax*lx+ay*ly+az2*lz;d=max(0f,(d+soft*.6f)/(1+soft*.6f));d=d.pow(gam)
   val side=(.5f+((u-.5f)*lx+(v-.45f)*ly)*1.6f).coerceIn(0f,1f);d*=.35f+.65f*side.pow(.6f+sh)
   val dist=hypot(u-lpx,(v-lpy)*.9f);val at=1f/(1f+(dist*fo*3.2f).pow(2));AT[i]=at
   val pt=1+(pattern(L.pattern,u,v,L.az)-1)*L.patternAmt;PT[i]=pt
   G[i]=d*pt*at*I*1.6f
   val back=max(0f,-(ax*lx+ay*ly));RM[i]=(back*a.edge[i]*1.6f+a.hair[i]*back*.5f+a.hair[i]*a.edge[i]*.4f)*rim*(.5f+.5f*at)}
  // 脸型锁定
  val fr=max(6,max(w,h)/18);val Gs=blur(G,w,h,fr);val fz=blur(a.face,w,h,max(3,fr/2))
  var fm=0f;var fc=0;for(i in 0 until n)if(a.face[i]>.5f){fm+=Gs[i];fc++};fm=if(fc>0)fm/fc else 1f
  for(i in 0 until n){val f=min(1f,fz[i]*1.4f)*protect;if(f<=0f)continue
   var g=G[i]*(1-f*.7f)+Gs[i]*f*.7f;val lo=fm*(1-.6f*f)+amb*.05f;val hi=fm*(1+.8f*f)+.08f
   g=if(g<lo)g+(lo-g)*f*.8f else if(g>hi)g-(g-hi)*f*.6f else g;G[i]=g;RM[i]*=1-f*.97f}
  // 墙面投影
  var csh=FloatArray(n)
  if(cast>0f){val sx=(-lx*w*.07f*(1+cast)).roundToInt();val sy=(max(-.02f,ly)*-h*.05f).roundToInt()
   for(y in 0 until h)for(x in 0 until w){val x0=x+sx;val y0=y+sy;if(x0 in 0 until w&&y0 in 0 until h)csh[y*w+x]=a.mask[y0*w+x0]}
   csh=blur(csh,w,h,max(3,(w/60f*(1+soft*3)).roundToInt()))}
  val ch=FloatArray(3)
  for(y in 0 until h)for(x in 0 until w){val i=y*w+x;val m=a.mask[i];val u=x.toFloat()/w;val v=y.toFloat()/h
   val vg=max(0f,1-vig*(hypot(u-.5f,(v-.5f)*.8f)*1.6f).pow(2))
   val dB=hypot(u-lpx,(v-lpy)*.85f);val halo=exp(-(dB/(.25f+.35f*(1-fo))).pow(2))*glow
   val bgLight=(L.bg*(.25f+amb*.6f)+halo*I*1.3f+AT[i]*I*.35f*(1-vig*.5f))*(1+(PT[i]-1)*.9f)*vg*(1-cast*csh[i]*.85f)
   val c=a.px[i];ch[0]=lin[(c shr 16) and 255];ch[1]=lin[(c shr 8) and 255];ch[2]=lin[c and 255];val Y=ch[0]*.3f+ch[1]*.59f+ch[2]*.11f
   var px=0xFF shl 24
   for(k in 0..2){val keep=chroma*a.face[i]*.55f;val kc=lc[k]*(1-keep)+keep;val acc=ac[k]*(1-keep*.5f)+keep*.5f
    val subj=ch[k]*(amb*.7f*acc*(.75f+.25f*vg)+G[i]*kc)*1.2f+RM[i]*rc[k]*(.25f+Y*1.5f)
    val bgv=ch[k]*bgLight*(bc[k]*.6f+lc[k]*.4f*min(1f,halo*2+.3f))
    var o=subj*m+bgv*(1-m);o=o/(1+o*.18f)
    px=px or ((min(1f,o*1.1f).toDouble().pow(1/2.2)*255).toInt().coerceIn(0,255) shl (16-8*k))}
   out[i]=px}
  return Bitmap.createBitmap(out,w,h,Bitmap.Config.ARGB_8888)
 }

 private fun pattern(p:Pattern,u:Float,v:Float,az:Float):Float=when(p){
  Pattern.DAPPLE->{val f=sin(u*23+sin(v*11)*2)*sin(v*19+cos(u*13)*2)+sin((u+v)*41)*.4f;if(f>.15f)1f else .25f}
  Pattern.BLINDS->{val q=(u*cos(az*.3f*PI.toFloat()/180)+v*1.3f+u*.4f)*28;if(q-floor(q)<.55f)1f else .12f}
  Pattern.SPOT->max(.05f,1-(hypot(u-.5f,(v-.38f)*1.2f)/.33f).pow(2))
  Pattern.FLASH->max(.5f,1.15f-hypot(u-.5f,v-.45f)*.9f)
  Pattern.FLICKER->.8f+.2f*sin(u*9+v*5)
  Pattern.NONE->1f}

 companion object{
  fun kelvin(k0:Float):FloatArray{val k=k0/100;val r:Double;val g:Double;val b:Double
   if(k<=66){r=255.0;g=99.47*ln(k.toDouble())-161.12;b=if(k<=19)0.0 else 138.52*ln(k-10.0)-305.04}
   else{r=329.7*(k-60.0).pow(-.1332);g=288.12*(k-60.0).pow(-.0755);b=255.0}
   return floatArrayOf(r,g,b).map{(it/255).coerceIn(0.0,1.0).toFloat()}.toFloatArray()}
  fun blur(src:FloatArray,w:Int,h:Int,r:Int):FloatArray{val a=src.copyOf();val t=FloatArray(a.size);val d=2f*r+1
   repeat(2){for(y in 0 until h){var s=0f;val o=y*w;for(x in -r..r)s+=a[o+x.coerceIn(0,w-1)];for(x in 0 until w){t[o+x]=s/d;s+=a[o+min(w-1,x+r+1)]-a[o+max(0,x-r)]}}
    for(x in 0 until w){var s=0f;for(y in -r..r)s+=t[y.coerceIn(0,h-1)*w+x];for(y in 0 until h){a[y*w+x]=s/d;s+=t[min(h-1,y+r+1)*w+x]-t[max(0,y-r)*w+x]}}}
   return a}
 }
}
