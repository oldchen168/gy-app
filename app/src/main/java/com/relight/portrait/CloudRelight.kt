package com.relight.portrait
import android.graphics.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/** 可选云端重打光（例如自建 IC-Light 服务）。
 * 约定接口：POST multipart {image, prompt, preserve_identity=true} → 返回 JPG/PNG。
 * 云端结果可能改动细节，所以默认把它当作“光照参考”：只取 光照比值 = 云端结果的低频 / 原图的低频，再乘回原图，五官和纹理仍然是原始像素。 */
object CloudRelight{
 private val http=OkHttpClient.Builder().readTimeout(120,TimeUnit.SECONDS).build()
 fun enabled()=BuildConfig.RELIGHT_API_URL.isNotBlank()
 fun run(src:Bitmap,preset:Preset):Bitmap{
  val bos=ByteArrayOutputStream();src.compress(Bitmap.CompressFormat.JPEG,95,bos)
  val body=MultipartBody.Builder().setType(MultipartBody.FORM)
   .addFormDataPart("image","in.jpg",bos.toByteArray().toRequestBody("image/jpeg".toMediaType()))
   .addFormDataPart("preset",preset.en)
   .addFormDataPart("preserve_identity","true").build()
  val req=Request.Builder().url(BuildConfig.RELIGHT_API_URL).header("Authorization","Bearer ${BuildConfig.RELIGHT_API_KEY}").post(body).build()
  http.newCall(req).execute().use{r->check(r.isSuccessful){"云端错误 ${r.code}"}
   val res=BitmapFactory.decodeStream(r.body!!.byteStream())
   return Bitmap.createScaledBitmap(res,src.width,src.height,true)} // 服务器已做光照迁移
 }
 /** 只迁移光照（低频比值），保留原图高频细节 = 身份保护 */
 fun transferLight(orig:Bitmap,lit:Bitmap):Bitmap{
  val w=orig.width;val h=orig.height;val n=w*h;val a=IntArray(n);val b=IntArray(n);orig.getPixels(a,0,w,0,0,w,h);lit.getPixels(b,0,w,0,0,w,h)
  val r=maxOf(3,maxOf(w,h)/80);val out=IntArray(n){0xFF shl 24}
  for(sh in intArrayOf(16,8,0)){
   val o=RelightEngine.blur(FloatArray(n){((a[it] shr sh) and 255)+1f},w,h,r);val l=RelightEngine.blur(FloatArray(n){((b[it] shr sh) and 255)+1f},w,h,r)
   for(i in 0 until n){val v=(((a[i] shr sh) and 255)*(l[i]/o[i])).toInt().coerceIn(0,255);out[i]=out[i] or (v shl sh)}}
  return Bitmap.createBitmap(out,w,h,Bitmap.Config.ARGB_8888)}
}
