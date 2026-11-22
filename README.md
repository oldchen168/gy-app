# AI 人像光影重塑 · Android 原生工程

1. 用 Android Studio（Koala 或更新版本）打开这个文件夹，等待 Gradle 同步完成。
2. 下载 `selfie_multiclass_256x256.tflite`，放到 `app/src/main/assets/`（下载地址见该目录里的 README.txt）。
3. 点 Run，或选择 Build → Build APK(s)，就能生成 APK。
4. （可选）云端 AI：在 `app/build.gradle.kts` 里填写 `RELIGHT_API_URL` 和 `RELIGHT_API_KEY`，接你自己部署的 IC-Light 服务。云端结果只用来迁移光照的低频部分，五官细节仍然是原图像素。

## 人物特征保护原理
- **本地模式**：MediaPipe 把画面分成背景、头发、身体皮肤、脸部皮肤、衣服几类，再用蒙版推算近似法线，计算逐像素光照增益，最后乘到原像素上。整个过程不重新生成像素，所以身份、五官、纹理和构图都不会变。
- **脸部保护**：限制面部的极端明暗，防止阴影把脸型“改”掉。**肤色保真**：减少光色对皮肤色相的影响。
- **云端模式**：用 光照比值 = 结果低频 / 原图低频 来迁移光照，丢掉 AI 可能改动过的高频细节。

## 一键云端编译 APK（不用装 Android Studio）
1. 注册或登录 GitHub，新建一个仓库（Private 也可以）。
2. 解压本压缩包，把 PortraitRelight 文件夹里的全部内容（包括 .github 文件夹）上传到仓库根目录。
3. 打开仓库的 Actions 页，等待 “Build APK” 运行完成（约 5–10 分钟）。
4. 点进这次运行，在页面底部的 Artifacts 里下载 PortraitRelight-apk，解压得到 app-debug.apk。
5. 把 apk 发到安卓手机，打开后允许“安装未知来源应用”即可安装。
