# Liquid Music

面向 Android 的无损音乐播放器，以柔白模糊背景、玻璃浮层和大封面构成 Apple Music 风格的界面。项目使用 [`real_liquid_glass`](https://pub.dev/packages/real_liquid_glass)，但不是 Apple 官方产品，也不包含商业音乐服务的抓取、破解或 DRM 绕过功能。

## 已实现

- 本地导入 FLAC、ALAC/M4A、WAV、AIFF、APE、OGG、Opus、AAC 和 MP3。
- 读取音频标题、艺人、专辑、封面、时长、码率、采样率与内嵌歌词。
- 连接 Navidrome、Subsonic 和兼容 OpenSubsonic 的自建服务器。
- 远程播放请求原始格式，不主动转码；支持离线保存原始音频。
- 主页、资料库、搜索、专辑详情、歌单、收藏、歌词、队列、随机与重复播放。
- 个人歌单，以及服务器端歌单与收藏同步。
- Android 后台播放、媒体通知、锁屏/耳机/车机媒体控制。
- Android 14+ USB DAC 独占 / bit-perfect 输出（需设备和厂商音频驱动支持）。
- Telegram Android 风格的紧凑底栏，以及受 [Bunpod](https://github.com/kamranbekirovyz/bunpod) 视觉语言启发的原创播放按钮与专辑卡片。
- Bunpod 同款 Material 3 Fade Forwards 页面切换节奏；播放器与专辑详情使用独立路由。
- 点击“歌词”后由封面 Hero 动画丝滑进入沉浸式歌词播放器；标题和封面收纳在右上角，支持 LRC、Apple Music 风格的逐行高亮、模糊和自动滚动。
- Now Playing 与歌词页使用 Apple Music 风格的无底色大号实心播放/暂停控制。
- 支持应用内下载并安装更新；同版本 APK 持久缓存并校验包名、版本与 SHA-256，开启安装权限后不会重复下载。
- 可在设置页配置阿里云 OSS/ECS `latest.json` 镜像，镜像优先且 GitHub Releases 自动回退。

## 音乐来源

本地音乐通过 Android 系统文件选择器授权，导入后复制到应用私有目录。第三方源需要用户提供自己的服务器地址、用户名与密码；密码存放在 Android 安全存储中，网络鉴权使用 Subsonic 的随机盐令牌方式。

局域网 HTTP 服务器可以连接，但公开网络建议使用有效的 HTTPS 证书。实际能否输出无损音频还取决于源文件、服务器配置、Android 设备解码器及蓝牙/有线输出链路。

USB 独占使用 Android 14 的 preferred mixer attributes。系统只会在 USB DAC 与设备音频 HAL 公布 bit-perfect 能力时启用；不支持时应用会显示真实原因，不会模拟成功状态。

## 本地构建

```bash
flutter pub get
flutter analyze
flutter test
flutter build apk --release
```

没有 `android/key.properties` 时，本地 Release 使用 debug 签名。正式版本由 GitHub Actions 从仓库 Secrets 恢复固定 Release 密钥，因此可在应用内覆盖升级。

## 发布

推送 `v*` 标签会触发 [release.yml](.github/workflows/release.yml)，构建签名 APK 并创建 GitHub Release。当前 Android 应用 ID 保持为 `io.github.admin0330.real_liquid_glass_demo`，以兼容已安装的旧测试版。

每个 Release 同时生成 APK、`.sha256` 和 `latest.json`。将这三个文件放在阿里云同一 HTTPS 目录，然后在应用“设置 → 阿里云更新服务器”填写 `latest.json` 地址即可。清单中的 `apk_url` 使用相对路径，因此同步到 OSS、CDN 或 ECS 后无需改写：

```json
{
  "version": "2.4.0",
  "apk_url": "liquid-music-v2.4.0.apk",
  "sha256": "64位SHA-256",
  "size": 55900000,
  "notes": "版本说明"
}
```

代码采用 MIT 许可证；用户应只连接自己拥有或获授权使用的音乐内容。
