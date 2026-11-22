package com.relight.portrait
import android.content.ContentValues
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.*
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.*

val Gold=Color(0xFFF5B942)
class MainActivity:ComponentActivity(){
 override fun onCreate(b:Bundle?){super.onCreate(b);val eng=RelightEngine(this)
  setContent{MaterialTheme(colorScheme=darkColorScheme(primary=Gold,background=Color(0xFF0D0D12))){App(eng)}}}

 @Composable fun App(eng:RelightEngine){
  val scope=rememberCoroutineScope()
  var src by remember{mutableStateOf<Bitmap?>(null)};var ana by remember{mutableStateOf<Analysis?>(null)}
  var out by remember{mutableStateOf<Bitmap?>(null)};var cur by remember{mutableIntStateOf(1)}
  var light by remember{mutableStateOf(PRESETS[1].light.copy())};var protect by remember{mutableFloatStateOf(.8f)};var chroma by remember{mutableFloatStateOf(.6f)}
  var tab by remember{mutableIntStateOf(0)};var cmp by remember{mutableStateOf(false)};var split by remember{mutableFloatStateOf(.5f)};var busy by remember{mutableStateOf("")}
  fun rerender(){val a=ana?:return;scope.launch{out=withContext(Dispatchers.Default){eng.relight(a,light,protect,chroma)}}}
  val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){u->u?:return@rememberLauncherForActivityResult
   scope.launch{busy="识别人物与背景…";src=withContext(Dispatchers.IO){load(u)};ana=withContext(Dispatchers.Default){eng.analyze(src!!,1080)};busy="";rerender()}}
  Column(Modifier.fillMaxSize().background(Color(0xFF0D0D12)).padding(12.dp)){
   Row(verticalAlignment=Alignment.CenterVertically){Text("AI 人像光影重塑",color=Color.White,fontSize=18.sp,modifier=Modifier.weight(1f))
    TextButton({picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}){Text("上传照片")}}
   Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF17171F))
     .pointerInput(cmp){detectDragGestures{c,_->if(cmp)split=(c.position.x/size.width).coerceIn(0f,1f)}},contentAlignment=Alignment.Center){
    if(src==null)Text("点击右上角上传半身照或全身照\n只改变光线，不改变人物",color=Color.Gray)
    src?.let{Image(it.asImageBitmap(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)}
    out?.let{o->Image(o.asImageBitmap(),null,Modifier.fillMaxSize().drawWithContent{
      if(cmp)clipRect(left=size.width*split){this@drawWithContent.drawContent()} else drawContent()},contentScale=ContentScale.Fit)}
    if(busy.isNotEmpty())Text(busy,color=Color.White,modifier=Modifier.align(Alignment.BottomStart).padding(8.dp))
   }
   TabRow(tab,containerColor=Color.Transparent){listOf("光影预设","自定义光线","人物保护").forEachIndexed{i,t->Tab(tab==i,{tab=i}){Text(t,Modifier.padding(10.dp))}}}
   Box(Modifier.height(240.dp)){when(tab){
    0->LazyVerticalGrid(GridCells.Fixed(3),verticalArrangement=Arrangement.spacedBy(6.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),contentPadding=PaddingValues(top=8.dp)){
      itemsIndexed(PRESETS){i,p->Column(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF1C1C25))
        .border(2.dp,if(i==cur)Gold else Color.Transparent,RoundedCornerShape(10.dp)).clickable{cur=i;light=p.light.copy();rerender()}.padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally){
        Text("%02d %s".format(i+1,p.zh),color=Color.White,fontSize=12.sp);Text(p.en,color=Color.Gray,fontSize=10.sp)}}}
    1->Column(Modifier.verticalScroll(rememberScrollState())){
      val S:@Composable (String,Float,ClosedFloatingPointRange<Float>,(Float)->Unit)->Unit={l,v,r,f->Row(verticalAlignment=Alignment.CenterVertically){Text(l,color=Color.LightGray,fontSize=13.sp,modifier=Modifier.width(72.dp))
       Slider(v,{f(it);rerender()},valueRange=r,modifier=Modifier.weight(1f))}}
      S("光线方向",light.az,-180f..180f){light=light.copy(az=it)}
      S("光源高度",light.el,-80f..85f){light=light.copy(el=it)}
      S("亮度",light.intensity,0f..2f){light=light.copy(intensity=it)};S("色温",light.kelvin,1500f..10000f){light=light.copy(kelvin=it,color=null)}
      S("环境光",light.ambient,0f..1f){light=light.copy(ambient=it)};S("阴影",light.shadow,0f..1f){light=light.copy(shadow=it)}
      S("轮廓光",light.rim,0f..1.5f){light=light.copy(rim=it)};S("背景亮度",light.bg,.1f..1.5f){light=light.copy(bg=it)}
      S("光线衰减",light.falloff,0f..1f){light=light.copy(falloff=it)};S("柔和过渡",light.soft,0f..1f){light=light.copy(soft=it)}
      S("墙面投影",light.cast,0f..1f){light=light.copy(cast=it)};S("背景光晕",light.glow,0f..1f){light=light.copy(glow=it)}
      S("暗角",light.vig,0f..1f){light=light.copy(vig=it)};S("光影效果",light.patternAmt,0f..1f){light=light.copy(patternAmt=it)}}
    else->Column{Text("脸型锁定",color=Color.LightGray);Slider(protect,{protect=it;rerender()})
      Text("肤色保真",color=Color.LightGray);Slider(chroma,{chroma=it;rerender()})
      Text("只对原图像素做光照增益，不重新生成人物，所以身份、五官、脸型、发型、纹理、服装和背景都不会变。"+(if(CloudRelight.enabled())"\n已开启云端 AI：只迁移光照低频，细节仍然用原图。" else ""),color=Color.Gray,fontSize=11.sp)}
   }}
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
    OutlinedButton({cmp=!cmp},Modifier.weight(1f)){Text(if(cmp)"关闭对比" else "原图对比")}
    if(CloudRelight.enabled())OutlinedButton({val s=src?:return@OutlinedButton;scope.launch{busy="云端 AI 重塑中…"
      runCatching{withContext(Dispatchers.IO){CloudRelight.run(s,PRESETS[cur])}}.onSuccess{out=it}.onFailure{toast(it.message?:"失败")};busy=""}},Modifier.weight(1f)){Text("云端AI")}
    Button({val s=src?:return@Button;scope.launch{busy="按原图分辨率导出…"
      val full=withContext(Dispatchers.Default){eng.relight(eng.analyze(s,4096),light,protect,chroma)};save(full,true);busy="";toast("已保存到相册 ${full.width}×${full.height}")}},Modifier.weight(1f)){Text("高清导出")}
   }}
 }
 private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
 private fun load(u:Uri):Bitmap{val bmp=contentResolver.openInputStream(u)!!.use{BitmapFactory.decodeStream(it)}
  val rot=contentResolver.openInputStream(u)!!.use{ExifInterface(it).rotationDegrees}
  return if(rot==0)bmp else Bitmap.createBitmap(bmp,0,0,bmp.width,bmp.height,Matrix().apply{postRotate(rot.toFloat())},true)}
 private fun save(b:Bitmap,png:Boolean){val cv=ContentValues().apply{put(MediaStore.Images.Media.DISPLAY_NAME,"relight_${System.currentTimeMillis()}.${if(png)"png" else "jpg"}")
   put(MediaStore.Images.Media.MIME_TYPE,if(png)"image/png" else "image/jpeg");put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Relight")}
  contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cv)?.let{u->contentResolver.openOutputStream(u)?.use{b.compress(if(png)Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,96,it)}}}
}
