# Liquid Music Android

[![Native Android CI](https://github.com/admin0330/liquid-music-android/actions/workflows/ci.yml/badge.svg)](https://github.com/admin0330/liquid-music-android/actions/workflows/ci.yml)
[![CodeQL](https://github.com/admin0330/liquid-music-android/actions/workflows/codeql.yml/badge.svg)](https://github.com/admin0330/liquid-music-android/actions/workflows/codeql.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-2ea44f.svg)](LICENSE)

Liquid Music Android 是一个原生 Kotlin 本地音乐播放器。它将 Apple Music iOS 26 的空间、层次、动态专辑色和 Liquid Glass 视觉语言带到 Android，但只管理设备上的音乐、播放列表和本地歌词。

它不是 Apple Music 客户端，也不会连接 Apple 账号、Apple Music API、在线曲库、DRM、iCloud Music Library、Subsonic 或 Navidrome。网络功能仅用于主页内嵌的个人博客和用户主动触发的应用更新；音乐扫描、搜索与播放仍完全在设备本地完成。

> 本项目与 Apple Inc.、Apple Music 或 Bunpod 没有隶属、合作或认可关系。Apple Music 和 iOS 是 Apple Inc. 的商标。界面为独立实现；仓库不包含 Apple/Bunpod 源码、素材、商业音乐、封面或歌词。

## 当前版本

- 版本：`3.0.1`（`versionCode 20`）
- 最低系统：Android 8.0 / API 26
- 目标系统：Android API 37
- 正式包名：`io.github.admin0330.real_liquid_glass_demo`
- Debug 包名：`io.github.admin0330.real_liquid_glass_demo.dev`
- 项目仓库：<https://github.com/admin0330/liquid-music-android>
- 正式 APK：<https://github.com/admin0330/liquid-music-android/releases>

## 功能

### 本地资料库

- 启动后通过 MediaStore 自动扫描设备音乐。
- 监听 MediaStore 变化；运行期间新增、删除或移动音频后会自动防抖刷新。
- 支持 MP3、FLAC、WAV、M4A 和 OGG。
- Room 保存歌曲索引、收藏、播放历史、歌词和播放列表元数据。
- 资料库包含歌曲、专辑、艺人和播放列表。
- 每个分区都支持列表/网格视图，以及最近添加、最近播放、名称和艺人排序。
- 搜索只在本机执行，可检索并打开歌曲、专辑、艺人和播放列表。
- 缺少权限、空资料库、MediaStore 故障、文件移动和不支持的解码格式都有可见状态，不会用假数据填充页面。

### 播放

- AndroidX Media3 ExoPlayer、MediaSession 和前台播放服务。
- 播放、暂停、拖动进度、上一首、下一首、随机、列表循环和单曲循环。
- 可查看、跳转、移除和调整播放队列；同一歌曲可作为独立条目重复出现。
- 播放队列、当前位置、随机和循环状态可在进程重建后恢复。
- 通知栏、锁屏、蓝牙耳机和系统媒体按钮控制。
- 音频焦点、拔出耳机自动暂停、WakeLock，以及 MediaController 断线重连。
- 播放失败、文件缺失、解码失败和缓冲状态会在 UI 中显示。

### Liquid Glass 界面

- 自定义 `LiquidGlassSurface`：真实背景模糊、透明度、色调、圆角、阴影、噪点和共享动态高光。
- 16dp 页面边距、12dp 同级间距及 4/8/12/16/24dp 节奏；触控目标不小于 44dp。
- Palette 从当前专辑封面提取主色，平滑驱动全局渐变和玻璃色调。
- 四个浮动玻璃导航页：主页、新发现、资料库、搜索。
- Dock 和 mini player 的高度被计入内容安全区，不会覆盖列表。
- mini player → 沉浸播放器，以及专辑卡片 → 专辑页使用 Compose SharedTransition。
- 页面、Dock 选中态、按钮按压、封面呼吸、颜色和歌词切换均使用统一 motion token。
- 浅色、深色和跟随系统三种外观。

### 博客首页与专辑

- 主页在受限 Android WebView 中内嵌个人博客 [Ym1r World](https://ym3861.cn/blog)。
- 玻璃工具栏提供网页后退、刷新和应用设置入口；页面状态可在导航切换后恢复。
- 仅 `https://ym3861.cn` 与 `https://www.ym3861.cn` 在应用内加载，普通外站链接交给系统浏览器，文件、脚本和 Intent URL 会被拒绝。
- 专辑详情包含封面、艺人、年份、无损标记、曲序、播放和随机播放。

### 播放列表与收藏

- 创建、重命名和删除播放列表。
- 添加/移除歌曲、允许重复条目，并原子重排顺序。
- 收藏或取消收藏本地歌曲；收藏会参与首页推荐和喜爱专辑计算。
- 删除播放列表不会删除原始音乐文件。

### 本地歌词

- 解析标准 `.lrc` 时间标签、多个时间标签和全局 offset。
- 自动发现与音频同名的旁路歌词；Android 8–9 使用旧 MediaStore 路径，Android 10+ 使用 scoped storage 查询。
- 也可以通过系统文件选择器手动绑定本地歌词，支持 UTF-8 与 GB18030。
- 纯歌词页面按行同步、平滑居中滚动、透明度/字重/缩放/模糊过渡，并可点按跳转。
- 顶部下拉返回播放器；系统返回键会按“队列 → 歌词 → 播放器”层级退出。
- 歌词下方预留固定控制区，不会遮挡播放按钮。

### USB DAC

- Android 14+ 在系统和 DAC 提供 `MIXER_BEHAVIOR_BIT_PERFECT` 时，可请求 USB bit-perfect mixer 属性。
- 设置会持久化；DAC 重新连接后自动再次请求。
- Android 13 及更低版本或不支持的设备会明确显示限制，不会谎报“独占成功”。

这项能力使用 Android 官方音频 mixer API，并不绕过厂商驱动。是否真正保持采样率/位深仍取决于 Android 版本、设备固件、USB DAC 和当前输出路由。

### 安全应用内更新

- 设置页由用户手动检查、下载、取消和安装更新；不会静默安装。
- 默认清单：<https://ym3861.cn/liquid-music-updates/latest.json>
- 只允许 HTTPS、拒绝 URL 凭据、明文降级和跨源重定向。
- 限制清单大小并严格验证 JSON schema。
- APK 先写入私有 `.part` 文件；取消或校验失败会删除临时文件。
- 安装前验证声明大小、Content-Length、SHA-256、包名、`versionCode`、`versionName` 和当前正式签名证书。
- `fsync` 后原子提交缓存；同版本已验证 APK 会复用，不会重复下载。
- 最终仍由 Android 系统安装器展示确认界面；若未授权“安装未知应用”，会先打开对应系统设置页。

更新下载不支持 Range 断点续传。主动取消后下一次会重新下载；已经完整校验的同版本 APK 才会复用。

## 不包含的功能

- Apple ID、Apple 账号、Apple Music 订阅、Apple Music API 或在线目录
- iCloud Music Library、DRM、Apple Radio 服务、Replay 云统计
- Subsonic、Navidrome、OpenSubsonic 或其他第三方音乐服务器
- 在线音乐下载、在线歌词、在线封面或云端播放列表
- 预置音乐、商业歌词、商业封面、演示曲目或假推荐

## 技术栈

| 层 | 技术 |
|---|---|
| Language | Kotlin 2.3 |
| UI | Jetpack Compose + Material 3 foundation |
| Architecture | Clean Architecture + MVVM |
| Dependency injection | Hilt |
| Database | Room + schema export |
| Playback | AndroidX Media3 ExoPlayer + MediaSessionService |
| Images | Coil（仅本地 URI） |
| Dynamic color | AndroidX Palette |
| Glass blur | Haze + 自定义 Liquid Glass system |
| Async | Kotlin Coroutines + Flow |
| Navigation | Navigation Compose + SharedTransition |
| Preferences | DataStore |

依赖版本集中在 [`gradle/libs.versions.toml`](gradle/libs.versions.toml)。Gradle Wrapper 固定为 9.5.0，并在 `gradle-wrapper.properties` 中校验官方 SHA-256。

## 工程结构

```text
app/src/main/java/io/github/admin0330/liquidmusic/
├── app/                    # Application、Activity、根状态与权限
├── core/
│   ├── audio/              # USB bit-perfect 控制
│   ├── designsystem/       # tokens、theme、glass、components
│   ├── lyrics/             # LRC parser
│   ├── preferences/        # DataStore
│   └── ui/                 # 通用页面与音乐组件
├── data/
│   ├── legacy/             # v2 Flutter 数据的幂等迁移
│   ├── local/database/     # Room entities、DAO、schema
│   ├── media/              # MediaStore scanner/observer
│   └── repository/         # 本地实现
├── domain/                 # models、repository contracts、use cases
├── feature/                # home/library/search/player/playlist/settings
├── navigation/             # 四栏导航与详情路由
├── player/                 # Media3 service、controller、queue/history
└── update/                 # HTTPS 更新、校验、缓存、系统安装器
```

架构决策与旧版迁移审计见：

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- [`docs/PHASE0_AUDIT.md`](docs/PHASE0_AUDIT.md)
- [`docs/RELEASE.md`](docs/RELEASE.md)

## 开发环境

需要：

- JDK 21
- Android SDK Platform 37
- Android SDK Build Tools（由 AGP 选择兼容版本）
- Git

Android Studio 中直接打开仓库根目录即可。命令行需设置 `ANDROID_HOME` 或 `ANDROID_SDK_ROOT`，并让 `JAVA_HOME` 指向 JDK 21。

### 构建与测试

Linux/macOS：

```bash
./gradlew clean
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew assembleRelease
```

Windows PowerShell：

```powershell
.\gradlew.bat clean
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

Debug APK 输出到：

```text
app/build/outputs/apk/debug/app-debug.apk
```

未配置正式 keystore 时，`assembleRelease` 只生成未签名产物，适合验证 R8 构建是否通过，绝不能作为正式更新发布。Debug 包使用 `.dev` applicationId，也不能覆盖正式版。

### 正式签名

在仓库根目录创建不会提交的 `key.properties`：

```properties
storeFile=C:/absolute/private/path/liquid-music-release.jks
storePassword=<keystore password>
keyAlias=<release alias>
keyPassword=<key password>
```

正式发行必须继续使用历史证书，否则 Android 会拒绝覆盖安装：

```text
SHA-256: 621185c90ce4a8d95d531bc4ac936b0f54c029dddf910c60e0074342047fb523
```

不要提交 `key.properties`、JKS、密码、服务器密钥或 GitHub Actions Secret。签名证书不能在不破坏升级链的情况下随意轮换。

## 从 2.4.9 升级

正式包继续使用原 applicationId，因此使用同一证书签名的 3.0.1 可以覆盖升级 2.4.9 或 3.0.0。

首次启动会幂等迁移：

- 仍然存在的本地/离线文件
- 播放列表及重复歌曲条目
- 收藏
- 已保存的本地歌词
- HTTPS 更新清单地址

旧 Subsonic/Navidrome 服务器地址、用户名和密码不会迁移，并会在可识别时清理。迁移成功标记只在事务完成后写入；失败不会删除旧数据，也不会阻止新的 MediaStore 扫描。

## GitHub 与阿里云的职责

本项目只使用一个 GitHub 仓库：

1. `admin0330/liquid-music-android`：公开源码、CI、CodeQL、Dependabot、正式签名 APK、GitHub Release 与镜像部署入口。
2. `ym3861.cn`：国内更新镜像；每个签名 Release 成功后自动同步版本 APK、校验文件和 `latest.json`，应用默认直接从这里检查和下载。

仓库源码和 Git 历史不包含发行 keystore、密码或阿里云凭据。普通 CI 只产生 `.dev` Debug 工件和未签名 Release 验证产物；只有受保护的 `v*` 标签发布工作流可以读取 GitHub Actions 加密 Secrets 并签出正式 APK。签名 Release 成功后，复用工作流只接收四个阿里云 Secret 并原子更新镜像；不会把签名 Secret 传给部署任务。完整发布顺序、安全检查和回滚方式见 [`docs/RELEASE.md`](docs/RELEASE.md)。

### Combined 更新清单

为同时兼容 2.4.9 与 3.x，线上 `latest.json` 必须精确包含下面九个字段，不能追加其他键：

```json
{
  "versionCode": 20,
  "versionName": "3.0.1",
  "apkUrl": "https://ym3861.cn/liquid-music-updates/liquid-music-v3.0.1.apk",
  "sha256": "<64 lowercase hexadecimal characters>",
  "size": 12345678,
  "changelog": "更新说明",
  "version": "3.0.1",
  "apk_url": "liquid-music-v3.0.1.apk",
  "notes": "更新说明"
}
```

约束：

- `versionName` 必须等于 `version`。
- `changelog` 必须等于 `notes`。
- `apkUrl` 与以清单 URL 为基准解析后的 `apk_url` 必须指向同一 HTTPS URL。
- APK 文件名、哈希和大小必须在上传后保持不可变。
- 先上传并验证版本 APK，最后原子替换 `latest.json`。

## 权限与隐私

| 权限 | 用途 |
|---|---|
| `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` | 读取 MediaStore 中的本地音频 |
| `POST_NOTIFICATIONS` | Android 13+ 播放通知；首次播放时请求 |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | 后台持续播放 |
| `WAKE_LOCK` | 播放期间避免解码被休眠中断 |
| `MODIFY_AUDIO_SETTINGS` | 音频焦点和 Android 14+ USB mixer 属性 |
| `INTERNET` | 加载主页个人博客，以及用户手动检查和下载应用更新 |
| `REQUEST_INSTALL_PACKAGES` | 打开 Android 系统安装器安装已验证 APK |

音乐、播放历史、播放列表和歌词都保存在设备本地。应用不包含统计 SDK、广告 SDK、账号系统或遥测。更新请求只发送普通 HTTPS 请求；不会上传音乐文件或资料库内容。

## 测试范围

当前 JVM 测试覆盖：

- LRC 时间标签、多标签、offset 和当前行选择
- 新版、旧版和 combined 更新清单
- 非 HTTPS、URL 凭据、跨源/降级重定向
- 清单字段严格性、大小限制和 URL 一致性
- 下载取消、缓存复用、Content-Length、文件大小和 SHA-256
- APK 包名/签名/版本失败映射与原子文件提交

GitHub Actions 会在每次 push/PR 执行 `clean`、单元测试、lint、Debug 构建和 Release R8 构建，并只上传带 `.dev` 包名的 Debug APK。CodeQL 每周及每次主分支更新分析 Kotlin/Java 代码。

## 已知限制

- 不在线获取歌词、封面或元数据；MediaStore 元数据不完整时会显示“未知艺人/专辑”。
- 某些厂商系统会限制后台播放或 USB 路由，需要用户在系统电池设置中允许后台运行。
- USB bit-perfect 只在 Android 14+ 且系统报告支持时可用，不能保证所有 DAC/ROM。
- 更新下载不做 Range 续传；取消会删除 `.part`。
- 正式 APK 必须在持有历史 keystore 的受保护发布环境中构建。
- 仓库不附带音频或歌词，因此 UI/播放验收需使用贡献者自己的合法本地文件。

## 贡献

请先阅读 [`CONTRIBUTING.md`](CONTRIBUTING.md)。提交前至少运行：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

不要在 issue、日志或 PR 中上传受版权保护的完整音乐/歌词、keystore、密码、真实服务器配置或个人 MediaStore 数据。安全问题请按 [`SECURITY.md`](SECURITY.md) 私下报告。

## 许可证

项目自身使用 [MIT License](LICENSE)。第三方依赖保持各自许可证，详见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) 和应用内“设置 → 开源许可证”。
