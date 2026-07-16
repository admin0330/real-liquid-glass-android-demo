# Liquid Music Android：Phase 0 项目审计与迁移基线

> 审计日期：2026-07-15
> 审计分支：`native-compose-rewrite`
> 审计性质：迁移前基线，不代表后续原生实现已经完成

## 1. 执行摘要

当前仓库是一套可运行的 Flutter 音乐播放器原型，而目标产品是以 Kotlin、Jetpack Compose、Room、Hilt 与 AndroidX Media3 重建的生产级 Android 本地音乐播放器。二者在技术栈、数据模型、播放生命周期、系统集成、导航结构和视觉实现上均存在根本差异，因此不适合继续在现有 Flutter 页面上逐项打补丁。

迁移采用“保留升级身份，替换应用内部实现”的策略：

- 保留现有 `applicationId`、正式签名和递增的 `versionCode`，确保已安装用户可以覆盖升级。
- 保留应用更新能力，但应用更新是唯一允许的运行时网络用途，不得扩展为在线音乐能力。
- 删除 Subsonic、Navidrome、OpenSubsonic 等远程音乐源及相关凭据、下载、搜索和播放代码。
- 用 MediaStore + Room 重建本地曲库，用 Media3 `MediaLibraryService`、ExoPlayer 和 MediaSession 重建播放链路。
- 用 Compose 自定义设计系统重建 Liquid Glass、五栏导航、迷你播放器、沉浸式播放页、歌词和动效。
- 在原生版本满足构建、数据迁移、播放和验收门槛之前，不替换当前稳定发布分支。

Phase 0 的产物仅是本审计与迁移决策。下文所有“目标”“建议”“应”均为待实施事项，不能视为已完成能力。

## 2. 审计范围与证据入口

本次审计覆盖：

- Gradle、Flutter 和 Android 构建配置；
- 依赖、包结构与平台通道；
- 当前页面、导航、播放器、曲库、歌词、播放列表、更新与 USB 音频实现；
- 与目标产品要求的差距；
- 迁移兼容性、安全、性能与发布风险。

主要证据入口：

| 领域 | 当前证据文件 | 结论摘要 |
| --- | --- | --- |
| Flutter 入口与大部分 UI | `lib/main.dart` | 单文件约 3,220 行，页面和状态耦合度高；当前仅四个主导航页 |
| 全局业务状态 | `lib/services/music_controller.dart` | `ChangeNotifier` 集中管理本地、远端、播放列表、下载、USB 和搜索，职责过多 |
| 本地导入 | `lib/services/local_library_service.dart` | 依赖文件选择器，复制文件至应用私有目录，JSON 存入 SharedPreferences |
| 当前播放 | `lib/services/playback_controller.dart` | 基于 `just_audio` / `audio_service`，没有目标要求的 Media3 服务和 MediaSession 架构 |
| 远端音乐源 | `lib/services/subsonic_service.dart`、`lib/services/download_service.dart` | 存在 Subsonic/Navidrome 访问、远端下载等目标明确排除的能力 |
| 数据模型 | `lib/models/music_models.dart` | 本地与远端模型混合，持久化边界不清晰 |
| 应用更新 | `lib/github_update_service.dart`、`android/app/src/main/.../MainActivity.kt` | Flutter MethodChannel 与原生 Activity 混合承载下载、安装和平台能力 |
| Android 配置 | `android/app/build.gradle.kts` | Flutter Gradle 插件；现有 `applicationId` 为升级兼容的关键资产 |
| Android 清单 | `android/app/src/main/AndroidManifest.xml` | 存在全局 `usesCleartextTraffic="true"`；没有完整的分版本本地媒体权限方案 |
| 依赖 | `pubspec.yaml` | `real_liquid_glass` 已声明但 UI 实际主要使用 `BackdropFilter` 与半透明背景 |

## 3. 当前工程状态

### 3.1 构建与技术栈

当前工程状态：

- Flutter/Dart 应用，版本为 `2.4.9+18`；
- Android Gradle Plugin `9.0.1`；
- Kotlin Android 插件 `2.3.20`；
- Java/Kotlin 字节码目标为 17；
- Android SDK 值来自 Flutter 配置，当前环境实际可用 SDK 36；
- 应用命名空间和 `applicationId` 均为 `io.github.admin0330.real_liquid_glass_demo`；
- Android Gradle Wrapper 位于 Flutter 子目录且被忽略，仓库根目录没有可供干净克隆直接执行的、受版本控制的 Wrapper。

当前核心 Flutter 依赖包括：

- `just_audio`、`just_audio_background`、`audio_service`、`audio_session`；
- `file_picker`、`audio_metadata_reader`、`path_provider`；
- `shared_preferences`、`flutter_secure_storage`；
- `real_liquid_glass`。

目标栈要求 Kotlin + Compose + Hilt + Room + Media3，因此这些 Flutter 依赖不能作为最终架构基础。迁移完成后应移除 Flutter 工具链、Dart 源码、Flutter Gradle 插件、无用依赖和生成目录。

### 3.2 架构

当前架构以 Flutter Widget、`ChangeNotifier` 和 SharedPreferences 为中心：

- `MusicController` 同时负责初始化、曲库、远端连接、收藏、搜索、下载、播放列表、歌词和 USB 音频状态；
- 播放状态、页面状态和持久化状态缺少清晰的领域边界；
- 没有 Hilt 依赖注入；
- 没有 Room 数据库、DAO、迁移测试或事务化播放列表关系；
- 没有 Clean Architecture 的 domain/data/feature 分层；
- 错误多以可变字符串或静默捕获表示，难以提供一致、可恢复的 UI 状态。

该结构适合原型验证，不适合继续承载系统媒体服务、后台生命周期、数据库迁移和复杂动画。

### 3.3 本地曲库

当前本地音乐流程不是 MediaStore 自动曲库：

1. 用户通过文件选择器手动选择音频；
2. 应用把音频复制到自己的私有 `library` 目录；
3. 提取元数据与封面后，把完整记录序列化到 `local_music_library_v2`；
4. 启动时从 SharedPreferences 反序列化，并过滤已经不存在的文件。

主要缺口：

- 没有启动后自动扫描 MediaStore；
- 没有 Android 13+ `READ_MEDIA_AUDIO` 与 Android 8–12 `READ_EXTERNAL_STORAGE` 的分版本授权流程；
- 没有 MediaStore 变化观察、增量刷新或稳定主键策略；
- 没有 Room 查询、索引、排序和关系表；
- 对被移动、删除、权限撤销和损坏的文件缺少完整的状态建模；
- 当前私有复制文件不会天然出现在 MediaStore，新版本必须专门迁移，不能假设重新扫描即可找回。

### 3.4 播放与系统集成

当前播放基于 Flutter 音频插件，队列主要存在于内存状态中。目标要求的以下链路尚未建立：

- Media3 ExoPlayer；
- 独立的 `MediaLibraryService` / 前台媒体播放服务；
- MediaSession 与系统媒体浏览；
- 一致的通知、锁屏、蓝牙耳机和外部控制状态；
- 由持久化 ID 恢复队列与当前播放位置；
- 对不支持格式、文件丢失、解码失败和音频焦点变化的统一处理。

当前代码还存在 URI 缺失、初始化异常后 loading 状态不恢复、内存队列中的收藏值变旧等风险。原生迁移必须以服务为播放状态真源，UI 不能自行维护另一套播放器事实。

### 3.5 页面、导航与真实数据

当前主导航为首页、资料库、搜索、设置四项，与目标的五项导航不一致：

1. Listen Now；
2. Browse；
3. Radio；
4. Library；
5. Search。

当前首页部分内容由曲库前几项拼装，不能等同于真实播放历史或“为你推荐”。资料库没有完整的歌曲/专辑/艺人/播放列表分层、列表/网格切换和四种排序。搜索没有统一覆盖歌曲、专辑、艺人和本地播放列表。现有页面中的分享、更多、专辑收藏、版权说明等行为还包含占位或非持久化实现。

Browse 和 Radio 在目标产品中必须继续存在，但语义需要本地化：

- Browse：按本地元数据、最近添加、年代、风格、专辑等规则浏览设备曲库；
- Radio：从本地曲库生成可重复、可解释的智能混合队列，例如“最常播放”“久未播放”“某艺人电台”“随机无损混合”；
- 两者都不得访问 Apple、Subsonic 或任何第三方音乐服务。

### 3.6 视觉系统与动效

当前界面虽然使用模糊、透明和渐变，但尚未形成目标要求的可复用 Liquid Glass 引擎：

- `real_liquid_glass` 在依赖中声明，但没有形成实际组件体系；
- 主要效果来自 `BackdropFilter`、透明填充和模糊背景圆形；
- 没有统一的 `LiquidGlassSurface` 参数、材质层级、色调、阴影、角半径和动态光照契约；
- 没有从专辑封面 Palette 提取颜色并驱动背景、玻璃着色和深浅色对比度的状态管线；
- 没有 Compose SharedTransition 驱动的迷你播放器到全屏播放页共享过渡；
- 部分持续重绘的模糊背景存在掉帧和功耗风险。

最终实现不能用“半透明矩形”冒充真实玻璃。API 31+ 可使用平台渲染能力；API 26–30 必须有经过验证的背板捕获/模糊降级方案，并在低端设备上限制采样分辨率和刷新频率。

### 3.7 播放列表、歌词、主题与设置

当前播放列表只覆盖创建和添加，数据存在 `personal_playlists_v2` JSON 中，缺少重命名、删除、移除歌曲、稳定顺序和事务约束。

当前歌词能力存在以下问题：

- 没有完整的本地 `.lrc` 发现、解析、冲突选择和持久化映射流程；
- 切歌时可能残留旧歌词状态；
- 远距离 seek 后的滚动定位不稳定；
- 内置歌词文件可能涉及版权，不应未经许可提交到公开仓库。

当前主要采用硬编码浅色风格，没有完成 Light/Dark/System 三态主题，也没有将默认随机、默认循环、重新扫描和开源许可做成生产设置。

### 3.8 更新与 Android 平台代码

应用更新能力具有可复用价值，但当前实现与 `MainActivity` 耦合，Activity 同时承担更新安装、下载和 USB 音频平台能力，职责过重。已发现的更新风险包括：

- README 声称校验下载大小，但原生实现没有完整落实；
- 清单全局允许明文流量，扩大了攻击面；
- 下载文件需要原子写入、完整性校验、重复下载复用和失败恢复；
- 更新检查状态、下载状态和安装授权应从 Activity 拆出；
- 更新链路不能成为任意网络请求或远程音乐接入的入口。

## 4. 要求差距矩阵

| 能力 | 当前状态 | 目标状态 | 差距/优先级 | 完成证据要求 |
| --- | --- | --- | --- | --- |
| 语言与 UI | Flutter/Dart | Kotlin + Jetpack Compose | 根本重写 / P0 | 仓库无 Flutter 运行依赖；原生模块可干净构建 |
| 架构 | Widget + `ChangeNotifier` 集中状态 | Clean Architecture + MVVM + Flow | 根本重写 / P0 | domain/data/feature 边界、单向状态与测试 |
| 依赖注入 | 无 Hilt | Hilt | 缺失 / P0 | Application、Service、ViewModel 和 Repository 均由 Hilt 组装 |
| 数据库 | SharedPreferences JSON | Room + DAO + Migration | 缺失 / P0 | schema、DAO 测试、迁移测试、事务播放列表 |
| 曲库发现 | 手工文件选择与私有复制 | MediaStore 自动扫描与刷新 | 缺失 / P0 | 权限授权后自动出现真实设备音频；删除/移动可刷新 |
| 播放引擎 | `just_audio` / `audio_service` | Media3 ExoPlayer | 根本重写 / P0 | MP3/FLAC/WAV/M4A/OGG 实机播放与 seek |
| 后台播放 | Flutter 插件能力 | Media3 前台服务 + MediaSession | 架构不符 / P0 | 通知、锁屏、蓝牙、熄屏与进程生命周期测试 |
| 导航 | 4 个页签 | 5 个浮动玻璃页签 | 缺失 / P1 | Listen Now/Browse/Radio/Library/Search 均可达且状态稳定 |
| Listen Now | 局部拼装内容 | 历史驱动的真实分区 | 部分占位 / P1 | 真实最近播放、最近添加、艺人、收藏与本地推荐 |
| Browse | 无对应完整页 | 本地规则浏览 | 缺失 / P1 | 不联网，所有卡片可追溯到 Room/MediaStore 数据 |
| Radio | 无本地电台模型 | 本地智能混合队列 | 缺失 / P1 | 可解释的本地队列生成与可重复播放 |
| Library | 基础内容 | 歌曲/专辑/艺人/播放列表；列表/网格；四种排序 | 大幅缺失 / P1 | 每种视图、排序和空状态的 UI/仪器测试 |
| Search | 基础本地/远端混合 | 仅本地四类即时搜索 | 边界错误 / P1 | 去抖或结构化 Flow；不会被旧查询覆盖；无结果状态 |
| 播放列表 | 创建、添加 | 完整 CRUD、移除、顺序 | 缺失 / P0 | Room 事务与重启后持久化测试 |
| 歌词 | 局部内置/解析逻辑 | 本地 `.lrc`、同步、高亮、平滑滚动 | 不完整 / P1 | 多时间戳、offset、无歌词、seek、切歌测试 |
| Liquid Glass | 透明 + `BackdropFilter` | 可复用真实背板模糊材质 | 未达标 / P1 | 参数 API、API 26/31+ 对比图、性能数据 |
| 专辑动态视觉 | 手工渐变 | Palette 驱动且平滑过渡 | 缺失 / P1 | 封面切换、深浅色对比度和无封面降级测试 |
| 迷你/全屏播放器 | 基础页面切换 | SharedTransition + 沉浸式播放页 | 未达标 / P1 | 连续展开/收起无闪烁、不卡顿、状态不丢失 |
| 主题 | 主要硬编码浅色 | Light/Dark/System + 专辑动态色 | 缺失 / P1 | 三态切换、重启持久化与可读性测试 |
| 设置 | 部分更新/音频设置 | 外观、播放、曲库、关于 | 不完整 / P2 | 每项有真实效果并持久化 |
| 错误处理 | 静默捕获与字符串状态 | 类型化错误 + 可恢复 UI | 高风险 / P0 | 拒绝权限、文件丢失、解码失败、数据库迁移无崩溃 |
| 性能 | 未建立测量基线 | 目标 60 FPS | 无证据 / P1 | Macrobenchmark/JankStats 或 Perfetto 结果与实机滚动证据 |
| 应用更新 | GitHub/镜像 + MethodChannel | 独立、安全、可恢复的更新模块 | 可迁移但需加固 / P1 | HTTPS、哈希/大小校验、重复下载复用、安装流程测试 |
| 构建发布 | Flutter 构建链 | 根目录 Gradle Wrapper 与三道构建门禁 | 缺失 / P0 | `clean`、`assembleDebug`、`assembleRelease` 全部通过 |

## 5. 产品与网络边界

### 5.1 允许的数据来源

音乐功能只能读取和生成以下本地数据：

- Android MediaStore 中、用户授权可读的音频；
- 从旧版本迁移后仍存在于本应用私有目录的合法本地音频；
- 用户通过 Android 系统文件选择器明确导入并授权的文件；
- 音频内嵌元数据、内嵌封面和设备上的本地封面；
- 同目录或用户明确关联的本地 `.lrc`；
- Room 中的本地播放列表、收藏、播放历史和设置。

Coil 仅用于 `content://`、`file://`、资源或内存中的本地图片，不允许通过专辑/艺人名称联网补图。

### 5.2 明确排除

必须删除或禁止：

- Apple ID、Apple 账户、Apple Music 订阅、iCloud Music Library；
- Apple Music 在线目录、API、DRM、Radio 服务、Replay 云统计；
- Subsonic、Navidrome、OpenSubsonic 或其他第三方音乐服务器；
- 在线歌曲搜索、串流、远端收藏、远端播放列表和远端音乐下载；
- 任何把 Browse 或 Radio 重新解释为在线内容入口的实现。

### 5.3 唯一允许的网络用途：应用更新

应用更新不属于音乐内容能力，可以保留，边界如下：

- 只请求预配置的更新清单和 APK 地址；
- 国内阿里云地址可作为主更新源，GitHub Release 可作为发布存档或显式备用源；
- 生产环境默认只允许 HTTPS；不得继续全局设置 `usesCleartextTraffic="true"`；
- 若历史服务器暂时只能提供 HTTP，必须先升级服务器为 HTTPS。不能用放宽全局网络安全作为长期方案；
- 清单至少包含版本号、`versionCode`、APK 大小、SHA-256 和下载 URL；
- 已完整下载且哈希匹配的 APK 必须复用，避免用户重复下载；
- 下载使用临时文件与原子改名，支持断点/失败恢复时也必须在安装前重新校验；
- 仅在用户明确操作后发起安装，并正确处理 `REQUEST_INSTALL_PACKAGES` 授权；
- 不提供任意 URL 输入、WebView、音乐 API 或通用下载器能力。

建议在自动化测试中通过网络代理或严格模式证明：正常浏览、搜索、播放、歌词、Browse 和 Radio 不产生任何音乐网络请求。

## 6. 原生版本基线建议

以下版本是 2026-07-15 审计时的建议基线。Phase 1 应通过官方发布页和依赖解析再次核验，并提交版本锁或 Version Catalog；未经验证不得仅为了“最新”采用 RC、Beta 或 Alpha。

| 项目 | 建议基线 | 说明 |
| --- | --- | --- |
| Android Gradle Plugin | `9.0.1` | 与现有工程一致，支持 SDK 36.x；配合 Gradle 9.1 |
| Gradle Wrapper | `9.1.0` | 必须在仓库根目录受版本控制 |
| Kotlin | `2.3.20` | 与当前插件基线一致；Compose Compiler 使用对应官方配置 |
| 构建 JDK | JDK 21 | 编译产物的 Java/Kotlin 目标保持 17；CI 与本机固定同一 JDK 大版本 |
| `compileSdk` / `targetSdk` | 36 / 36 | 当前可用的最新稳定 Android SDK 基线 |
| `minSdk` | 26 | Android 8.0，符合产品要求 |
| Compose BOM | `2026.06.00` | Compose 依赖统一由 BOM 管理，不逐个混配版本 |
| Navigation Compose | `2.9.8` | 使用稳定版 Navigation Compose |
| Room | `2.8.4` | 使用稳定版 Room 2；不采用仍非稳定的 Room 3 |
| Media3 | `1.10.1` | ExoPlayer、Session、UI 等组件保持同一版本 |
| Dagger Hilt | `2.60.1` | 应用、Service、Repository、ViewModel 注入 |
| AndroidX Hilt | `1.3.0` | 避免需要更高 compileSdk/AGP 的预发布组合 |
| KSP | `2.3.9` | 为 Room/Hilt 生成代码；必须实际验证与 Kotlin 组合 |
| Coil Compose | 最新已核验稳定 3.x | Phase 1 锁定具体版本；仅加载本地图片 |
| Palette | `1.0.0` | 从封面提取色板；结果需要缓存 |
| Coroutines / Lifecycle / DataStore | 官方稳定版并锁定 | Phase 1 在 Version Catalog 中记录具体解析版本 |

构建约束：

- 仓库根目录必须提供 `gradlew`、`gradlew.bat` 和 `gradle/wrapper/*`；
- `repositoriesMode` 应限制仓库来源，默认只使用 Google Maven、Maven Central 和 Gradle Plugin Portal；
- 正式签名配置从本地/CI Secret 注入，任何 keystore、密码和服务器密钥不得提交；
- Debug 与 Release 都必须使用相同的核心功能路径，不能用 Debug 专属假数据掩盖缺口；
- Release 不得在缺少正式签名时静默回退 debug 签名。

## 7. 目标包结构与职责

第一阶段可采用单一 `:app` Gradle 模块以降低迁移复杂度，但源码必须按下列包边界组织；当编译隔离或团队并行确有收益时再拆成独立 Gradle 模块，不能在没有边界的情况下把所有代码重新堆入 `app`。

```text
io.github.admin0330.liquidmusic
├── app
│   ├── LiquidMusicApplication
│   └── MainActivity
├── core
│   ├── common
│   │   ├── dispatcher
│   │   ├── result
│   │   └── logging
│   ├── ui
│   │   ├── state
│   │   ├── formatter
│   │   └── reusable
│   └── designsystem
│       ├── color
│       ├── theme
│       ├── typography
│       ├── motion
│       ├── glass
│       │   └── LiquidGlassSurface
│       └── artwork
├── data
│   ├── local
│   │   ├── database
│   │   ├── dao
│   │   ├── entity
│   │   └── migration
│   ├── media
│   │   ├── MediaScanner
│   │   ├── MediaStoreObserver
│   │   └── LocalLyricsResolver
│   └── repository
├── domain
│   ├── model
│   ├── repository
│   └── usecase
├── player
│   ├── MusicService
│   ├── PlayerManager
│   ├── session
│   ├── notification
│   └── queue
├── feature
│   ├── home
│   ├── browse
│   ├── radio
│   ├── library
│   ├── search
│   ├── player
│   ├── playlist
│   └── settings
└── navigation
    ├── LiquidMusicNavHost
    ├── MainDestination
    └── FloatingGlassTabBar
```

边界规则：

- `domain` 不依赖 Android UI、Room、MediaStore、Media3 或 Hilt 实现；
- `data` 实现领域仓库，Room Entity 不直接暴露给 feature；
- `player` 是唯一控制 ExoPlayer/MediaSession 的层，feature 通过稳定接口发送命令并观察状态；
- feature 的 ViewModel 组合 use case，不直接操作 DAO、ContentResolver 或 ExoPlayer；
- `core/designsystem` 不读取业务仓库，只接收已经计算好的视觉状态；
- Browse 和 Radio 的本地规则属于 domain use case，不能在 Composable 内临时拼数组；
- 应用更新放入独立的 update 包或后续模块，与 music data/player 完全隔离。

升级兼容方面建议：

- `applicationId` 暂时继续使用 `io.github.admin0330.real_liquid_glass_demo`；
- Kotlin `namespace` 与源码包可改为 `io.github.admin0330.liquidmusic`；
- 更换 `applicationId` 会使系统视为全新应用，除非用户明确接受失去覆盖升级和旧数据，否则不得更换。

## 8. 数据模型基线

Room 至少需要以下持久化事实：

- `TrackEntity`：MediaStore 标识、volume、content URI、标题、艺人、专辑、时长、mime/格式、路径提示、封面键、添加/修改时间、收藏、可用状态；
- `PlaylistEntity`：ID、名称、创建/修改时间；
- `PlaylistSongCrossRef`：播放列表 ID、歌曲 ID、稳定位置；
- `ListeningHistoryEntity`：歌曲 ID、播放时间、完成度或有效播放阈值；
- 必要的 album/artist 投影通过 SQL 查询生成，除非后续证明独立实体更适合一致性和性能；
- 设置优先使用 DataStore，不把主题等设置塞入 Room。

稳定 ID 不能只依赖显示名称。MediaStore 曲目建议以 `volumeName + _ID` 为主键组成部分，并保存 URI；重扫时结合 URI、文件大小、时长和修改时间判定变化。旧私有文件使用独立命名空间，例如 `legacy:<sha1>`，避免与 MediaStore ID 冲突。

## 9. 分阶段迁移计划

每一阶段都必须保持可构建、可审查，并在进入下一阶段前留下对应测试或人工证据。

### Phase 0：审计与决策冻结

产物：

- 当前实现和差距清单；
- 产品/网络边界；
- 技术版本基线；
- 数据兼容、签名和发布风险；
- 分阶段验收证据定义。

退出条件：本文件经过确认；当前稳定分支未被未验证的原生壳替换。

### Phase 1：原生构建骨架

工作项：

- 在仓库根目录建立受版本控制的 Gradle Wrapper、Version Catalog、`:app` 模块；
- 配置 Kotlin、Compose、Hilt、Room/KSP、Media3、Coil、Navigation；
- 建立 Application、Compose MainActivity、主题和最小导航壳；
- 保留现有 `applicationId`，接入正式签名变量和递增版本号；
- 配置 API 26–36 的基本 Manifest 与资源。

退出证据：空数据下应用可启动；Debug/Release 编译通过；不存在 Flutter 运行时依赖于新入口的情况。

### Phase 2：领域、Room 与 MediaStore 曲库

工作项：

- 定义领域模型、仓库接口、Room schema/DAO；
- 实现分版本权限和 MediaStore 首次/增量扫描；
- 实现空曲库、拒绝权限、文件消失和重扫；
- 建立排序、专辑/艺人聚合、收藏和历史记录；
- 加入 DAO、Repository 和数据库迁移测试。

退出证据：真实设备授权后自动显示本地曲目；文件删除后可恢复一致；无权限时不崩溃且有可操作提示。

### Phase 3：Media3 播放服务

工作项：

- 实现 ExoPlayer、MediaSession、MediaLibraryService 与前台通知；
- 实现播放、暂停、seek、上一首、下一首、队列、随机、循环；
- 对 MP3、FLAC、WAV、M4A、OGG 做实机样本验证；
- 处理音频焦点、耳机拔出、蓝牙/锁屏/通知控制、文件丢失和解码失败；
- 恢复合理的队列和播放状态。

退出证据：熄屏和切到后台后继续稳定播放；系统各入口状态一致；异常曲目不会导致服务崩溃。

### Phase 4：设计系统与动态视觉

工作项：

- 建立间距、圆角、字体、色彩、阴影、触摸反馈和 motion token；
- 实现参数化 `LiquidGlassSurface`；
- 实现封面 Palette 缓存、动态渐变和深浅色适配；
- 为 API 26–30 与 API 31+ 建立经过测量的模糊路径；
- 避免持续全分辨率模糊和无边界重绘。

退出证据：组件截图/预览、无封面降级、深浅色可读性、实机帧时间和内存结果。

### Phase 5：五栏导航与内容页面

工作项：

- 实现浮动胶囊 Liquid Glass Tab Bar、动态高亮和 spring 动画；
- 完成 Listen Now、Browse、Radio、Library、Search；
- 所有卡片和列表只使用真实本地数据；
- Library 完成列表/网格与四类排序；
- Search 覆盖歌曲、专辑、艺人和播放列表；
- 完成播放列表 CRUD、移除和顺序。

退出证据：五栏状态恢复正确；导航不覆盖内容；旋转/重组/后台恢复后无状态错乱；不存在假数据或空点击。

### Phase 6：迷你播放器、沉浸播放页与歌词

工作项：

- 浮动迷你播放器位于导航上方，正确预留内容 inset；
- SharedTransition 展开为全屏播放器；
- 300dp 以上封面、呼吸动画、播放控制、队列、随机和循环；
- 本地 `.lrc` 解析、同步滚动、当前行高亮、seek 和切歌；
- 统一按钮反馈、页面切换、颜色和玻璃出现动画。

退出证据：连续快速展开/返回不卡死、不闪屏、不重叠；歌词不会挡住控制；无歌词与损坏歌词均可恢复。

### Phase 7：设置、旧数据迁移、更新与错误收口

工作项：

- 完成 Light/Dark/System、默认随机/循环、重扫、版本和开源许可；
- 执行一次性 Flutter SharedPreferences 到 Room/DataStore 的迁移；
- 拆分并加固应用更新模块；
- 保留并隔离经验证的 USB 音频能力；
- 建立统一错误模型、日志脱敏和用户恢复动作。

退出证据：覆盖安装保留合法本地数据；远端凭据和远端入口不再可用；更新包校验与复用通过测试。

### Phase 8：移除遗留与质量收口

工作项：

- 删除 Dart/Flutter、Subsonic/Navidrome、远端下载和未使用资源；
- 删除 TODO、假数据、演示页、占位动作和未使用依赖；
- 完成单元、Room、仪器、UI、Macrobenchmark 和泄漏检查；
- 做 TalkBack、字号放大、44dp 以上触摸目标和对比度检查。

退出证据：代码搜索和依赖分析无遗留；关键测试矩阵通过；没有已知 P0/P1 崩溃或数据损坏问题。

### Phase 9：构建、签名与最终验收

必须执行：

```powershell
.\gradlew.bat clean
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

在类 Unix CI 中对应：

```bash
./gradlew clean
./gradlew assembleDebug
./gradlew assembleRelease
```

退出证据：三条命令从干净克隆通过；APK 使用预期证书；版本号可覆盖升级；11 项最终产品验收全部有记录。

## 10. 旧 Flutter 数据迁移方案

### 10.1 可识别的旧数据

当前已发现的 SharedPreferences / Secure Storage 键：

| 键 | 内容 | 迁移决策 |
| --- | --- | --- |
| `local_music_library_v2` | 私有复制曲目 JSON，含路径、元数据、封面和收藏字段 | 校验文件存在后导入 Room；标记来源为 legacy private |
| `personal_playlists_v2` | 个人播放列表及歌曲 JSON | 在曲目映射成功后事务导入 Playlist/PlaylistSong；保留顺序 |
| `offline_tracks_v2` | 从远端下载的离线曲目 JSON | 只迁移仍存在且可作为合法本地文件播放的副本；删除远端 URL/服务器关系 |
| `subsonic_server`、`subsonic_user` | 远端服务器信息 | 不迁移；迁移后清理 |
| `subsonic_password` | Flutter Secure Storage 中的远端密码 | 不迁移；在能够安全识别时清理，不记录到日志 |
| `update_manifest_url_v1` | 更新镜像清单地址 | 仅当地址满足白名单与 HTTPS 策略时迁移到 DataStore，否则回退内置可信地址 |

Flutter SharedPreferences 在 Android 上可能使用特定 preference 文件与 `flutter.` 前缀。Phase 7 必须先用旧版本样本数据确认实际文件名和键名，不能仅凭 Dart 常量推断。

### 10.2 迁移算法

1. 在 Room 第一次可用且旧数据尚未标记迁移时，读取旧 preference 快照；
2. 将 JSON 解析为版本化 DTO，任何单条损坏记录只隔离该记录，不中断整批迁移；
3. 对每个本地路径执行规范化、应用私有目录边界检查、存在性和可读性检查；
4. 计算稳定 legacy ID，并以路径、文件大小、时长和可用元数据与 MediaStore 条目去重；
5. 在 Room 事务中写入曲目、收藏、播放列表及顺序；
6. 对远端离线记录只保留本地文件事实，不保留服务器 ID、鉴权、远端封面 URL或重新下载能力；
7. 记录成功、跳过和失败数量，但日志不得包含密码、完整服务器 URL 或用户私有绝对路径；
8. 只有事务成功后写入 `flutter_v2_migration_complete` 标记；
9. 重复启动必须幂等，不得重复生成曲目或播放列表；
10. 在用户确认新库正确或经过安全的延迟窗口后，再清除已成功迁移的旧 JSON。远端凭据应尽快清理。

没有持久化来源的数据不能伪造迁移。当前没有可靠的完整播放历史，因此新版本的 Recently Played 应从迁移后的首次有效播放开始积累，而不是用曲库顺序伪装历史。

### 10.3 回滚与覆盖安装

- 原生版本发布前先用当前正式 APK 创建包含本地歌曲、收藏和播放列表的升级样本；
- 在同一应用 ID 与正式证书下覆盖安装候选 APK；
- 迁移前不破坏旧文件，确保候选版本失败时仍有取证与恢复空间；
- 数据库 schema 必须提供显式 Migration，生产构建禁止 `fallbackToDestructiveMigration`；
- 每次 schema 变更均保存 schema 导出并增加迁移测试；
- 若正式签名不匹配，停止发布，不能通过卸载旧版规避升级验证。

## 11. 关键风险与缓解措施

| 风险 | 影响 | 缓解与门禁 |
| --- | --- | --- |
| 更换 `applicationId` 或签名 | 无法覆盖安装，旧数据丢失 | 固定现有应用 ID；正式证书指纹纳入发布检查；`versionCode` 必须大于 18 |
| 原生壳过早替换稳定版本 | 用户拿到不可播放或不可迁移版本 | 在独立迁移分支开发，全部门禁通过后再合并/发布 |
| Flutter 私有曲目不在 MediaStore | 升级后用户以为歌曲丢失 | 一次性读取旧 JSON 和私有路径，验证后导入 Room，单独标识 legacy 来源 |
| 远端功能残留 | 违反严格本地产品定义并扩大安全面 | 删除模型、服务、UI、凭据和下载代码；增加网络负向测试 |
| 全局明文网络 | APK/清单可能遭篡改 | 移除全局 cleartext；更新仅 HTTPS；安装前 SHA-256 与大小双校验 |
| 更新包重复下载 | 国内网络环境下浪费流量 | 版本化缓存、临时文件、断点策略、完整性校验后复用 |
| 歌词版权 | 公开仓库侵权风险 | 不提交来源不明的完整歌词；仅解析用户设备上的 `.lrc` 或经许可资源 |
| API 26–30 缺少现代平台模糊 | 玻璃效果退化或掉帧 | 设计真实背板采样降级；缓存、降采样、限制刷新；按 API/性能分级 |
| 动态封面全屏模糊 | 掉帧、发热、内存抖动 | Palette 和位图缓存；低分辨率背板；只在状态变化时过渡；测量后设上限 |
| Compose 重组范围过大 | 滚动和歌词卡顿 | 稳定数据模型、分页/Flow 查询、细粒度状态、基准测试和 Layout Inspector |
| MediaStore ID/URI 变化 | 播放列表引用失效 | 保存 volume + ID + URI 与辅助指纹；重扫时做可解释的重新关联 |
| 前台服务/通知权限差异 | 后台播放中断 | API 26–36 实机/模拟器矩阵；正确声明媒体播放 service type 与通知流程 |
| Room 迁移失败 | 启动崩溃或数据损坏 | 导出 schema、全版本 MigrationTestHelper、事务和备份/错误恢复 UI |
| 格式“支持”仅停留在扩展名 | 特定编码实际无法解码 | 用真实 MP3/FLAC/WAV/M4A/OGG 样本测试 Media3，报告设备编解码限制 |
| USB 独占平台代码耦合 | 设备兼容性与崩溃风险 | 从 Activity 拆分；能力探测；失败回退标准音频路径；不影响普通播放 |
| AI 原型中的 TODO/占位遗留 | 功能看似存在但不可用 | 发布前执行代码搜索、行为测试和依赖分析，禁止假数据与空点击 |

## 12. 最终验收与证据清单

“功能看起来存在”不是完成证据。最终候选版本应保存以下可复现材料：

### 12.1 构建与制品证据

- 干净克隆后的 `clean`、`assembleDebug`、`assembleRelease` 完整成功日志；
- `apksigner verify --verbose --print-certs` 输出，证书与既有正式版本一致；
- APK 的文件大小与 SHA-256；
- `aapt dump badging` 或等价结果，证明 applicationId、minSdk、targetSdk、versionCode/versionName 正确；
- Release 构建没有 debug 签名回退、测试服务器、密钥或开发日志。

### 12.2 自动化证据

- domain use case 单元测试；
- Room DAO 与从每个历史 schema 的迁移测试；
- MediaStore 扫描、去重、删除/移动和权限状态测试；
- 播放列表 CRUD、顺序与幂等旧数据迁移测试；
- LRC parser 的多时间戳、offset、空行、损坏输入和 seek 测试；
- Navigation 与关键 Compose UI 测试；
- 更新清单、哈希错误、下载中断、缓存复用和安装授权测试；
- 网络负向测试，证明音乐功能没有访问外部服务。

### 12.3 设备矩阵

至少覆盖：

- API 26：最低版本、存储权限、玻璃降级和前台服务；
- API 30：分区存储与后台行为；
- API 33：`READ_MEDIA_AUDIO`、通知权限；
- API 35/36：最新系统、前台服务和 edge-to-edge；
- 至少一台真实 Android 设备完成 FLAC、蓝牙、锁屏、通知和性能测试。

### 12.4 11 项产品验收

| # | 场景 | 必须记录的证据 |
| --- | --- | --- |
| 1 | 安装 APK | 干净安装与同签名覆盖升级都成功；数据不因升级消失 |
| 2 | 打开应用 | 首屏无崩溃；冷启动、拒绝权限和数据库迁移均有确定状态 |
| 3 | 自动扫描本地音乐 | 授权后真实 MediaStore 音频出现；新增/删除后刷新正确 |
| 4 | Apple Music 风格首页 | Listen Now 使用真实历史/本地数据；无假卡片和空点击 |
| 5 | 播放本地歌曲 | 五种目标格式用真实样本验证；播放/暂停/seek/上下首正常 |
| 6 | 打开沉浸式播放器 | 动态封面背景、300dp 以上封面和控制在深浅主题下可读 |
| 7 | 使用迷你播放器 | 与导航不重叠；共享展开/收起连续、状态一致 |
| 8 | 创建播放列表 | 创建、重命名、删除、添加、移除、排序在重启后仍正确 |
| 9 | 显示歌词 | 本地 `.lrc` 同步、高亮、平滑滚动、seek、切歌和无歌词状态正确 |
| 10 | 切换深浅模式 | Light/Dark/System 即时生效并在重启后保持；动态色对比度合格 |
| 11 | 保持流畅动画 | 页面、Tab、按钮、玻璃、颜色和播放器过渡达到帧时间门槛，无明显卡顿 |

### 12.5 性能门槛

“60 FPS”应以测量定义，而不是主观描述：

- 关键路径至少包括冷启动、Library 长列表滚动、Tab 连续切换、迷你播放器展开/返回、歌词自动滚动、连续切歌与动态色过渡；
- 使用 Macrobenchmark、JankStats 或 Perfetto 记录帧时间、卡顿比例、CPU、GPU 与内存；
- 对目标 60 Hz 设备，关键交互帧预算以 16.67 ms 为参考，并记录 P50/P90/P95 与 jank；
- 性能不达标时优先降低背板采样分辨率、缓存 Palette/位图、缩小重组范围和停止不可见动画，不能仅隐藏动画；
- 内存与服务测试需包含连续切换大量高分辨率封面、后台数小时播放和多次打开/关闭播放页。

## 13. Phase 0 决策记录

截至本次审计，已确定：

1. 采用原生 Kotlin/Compose 全量迁移，不继续扩展 Flutter 架构；
2. 当前正式发布线在原生候选通过门禁前保持不变；
3. 保留现有 `applicationId`、正式签名链和版本递增关系；
4. 音乐内容严格本地，删除所有 Subsonic/Navidrome/Apple 在线能力；
5. Browse 与 Radio 保留为设备本地、由真实曲库和历史生成的功能；
6. 应用更新网络可以保留，但必须与音乐域隔离并使用受限、可校验的 HTTPS 链路；
7. 旧 Flutter 私有曲目和个人播放列表需要一次性、幂等、可测试的迁移；
8. 未经许可的内置完整歌词不得进入公开源码；
9. Liquid Glass 必须是可复用、可降级且有性能证据的设计系统，不接受简单 alpha 背景替代；
10. 只有通过干净构建、签名验证、数据升级、系统播放、11 项产品验收和性能测量后，原生迁移才可标记完成。
