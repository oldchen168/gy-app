plugins{id("com.android.application");id("org.jetbrains.kotlin.android");id("org.jetbrains.kotlin.plugin.compose")}
android{
 namespace="com.relight.portrait"; compileSdk=34
 defaultConfig{applicationId="com.relight.portrait";minSdk=26;targetSdk=34;versionCode=1;versionName="1.0"
  // 可选：云端 IC-Light 等重打光服务地址与密钥（留空=纯本地）
  buildConfigField("String","RELIGHT_API_URL","\"\"")
  buildConfigField("String","RELIGHT_API_KEY","\"\"")}
 buildFeatures{compose=true;buildConfig=true}
 compileOptions{sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17}
 kotlinOptions{jvmTarget="17"}
 androidResources{noCompress+="tflite"}
}
dependencies{
 implementation(platform("androidx.compose:compose-bom:2024.09.00"))
 implementation("androidx.compose.ui:ui");implementation("androidx.compose.material3:material3")
 implementation("androidx.activity:activity-compose:1.9.2")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
 implementation("com.google.mediapipe:tasks-vision:0.10.14")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 implementation("androidx.exifinterface:exifinterface:1.3.7")
}
