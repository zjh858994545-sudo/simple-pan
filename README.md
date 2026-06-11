# SimplePan

SimplePan 是一个基于 Android Kotlin + Jetpack Compose 的简易个人网盘客户端。项目围绕“文件树浏览、文件管理、上传、最近转存、TXT 阅读器、视频打开、分享与 DeepLink”等核心链路实现，主要用于课程设计、功能演示和技术答辩。

## 1. 项目功能概览

- 网盘首页：空间卡片、最近转存、最近浏览、上传入口。
- 文件页：文件树浏览、返回上一级、文件类型筛选、综合排序。
- 管理模式：长按进入管理模式，支持勾选、全选、分享、删除、移动、重命名。
- 上传能力：通过系统文件选择器选择本地文件，校验 100MB 大小限制，复制到 App 私有目录并写入 Room。
- 文件打开：TXT 文件进入自研阅读器，视频文件通过 FileProvider 调用系统播放器打开。
- TXT 阅读器：基于 Compose TextMeasurer 的测量分页，支持字号调整、滑动翻页、分页缓存和性能日志。
- 分享与 DeepLink：生成 token-only 分享链接，复制到剪贴板，App 回到前台后识别分享链接并进入分享预览页。
- 传输页与搜索页：展示真实传输记录，支持网盘文件搜索。

## 2. 技术栈

- 语言：Kotlin
- UI：Jetpack Compose + Material3
- 架构：MVI + ViewModel + UseCase + Repository 分层
- 本地数据库：Room
- 异步与响应式数据：Kotlin Coroutines + Flow
- 依赖注入：Hilt
- 导航：Navigation Compose
- 文件选择：SAF / Activity Result API
- 文件共享：FileProvider
- 日志：Timber

## 3. 运行环境依赖

建议使用 Android Studio 打开和运行本项目。

| 环境 | 要求 |
| --- | --- |
| Android Studio | 建议使用较新版本，需支持 Android Gradle Plugin 9.2.1 |
| JDK | 建议使用 Android Studio 自带 JDK，JDK 17 或更高版本 |
| Gradle | 项目已内置 Gradle Wrapper 9.4.1 |
| Android Gradle Plugin | 9.2.1 |
| Kotlin | 2.2.10 |
| Android SDK | compileSdk 36.1，targetSdk 36，minSdk 24 |
| 设备 | Android 7.0 及以上真机或模拟器 |
| 网络 | 首次 Gradle Sync 需要访问 Google Maven、Maven Central、Gradle Plugin Portal |

主要版本配置位置：

- `gradle/libs.versions.toml`
- `build.gradle.kts`
- `app/build.gradle.kts`
- `gradle/wrapper/gradle-wrapper.properties`

## 4. 环境搭建指南

### 4.1 使用 Android Studio 打开项目

1. 打开 Android Studio。
2. 选择 `Open`。
3. 选择项目根目录：`F:\simplepan`。
4. 等待 Gradle Sync 完成。
5. 如果提示缺少 Android SDK Platform 36，按 Android Studio 提示安装即可。

### 4.2 检查 SDK 配置

Android Studio 会自动生成本机专属的 `local.properties` 文件，里面记录 Android SDK 路径，例如：

```properties
sdk.dir=F\:\\AndroidStudioSDK
```

这个文件只和本机环境有关，不需要手动提交到仓库。

### 4.3 真机调试准备

1. 手机打开开发者选项。
2. 开启 USB 调试。
3. 用数据线连接电脑。
4. 手机弹出授权时选择允许。
5. Android Studio 顶部设备栏选择你的真机。

### 4.4 模拟器调试准备

1. Android Studio 打开 `Device Manager`。
2. 创建一个 Android 7.0 及以上的模拟器。
3. 启动模拟器。
4. 顶部设备栏选择该模拟器。

## 5. 项目启动说明

### 5.1 Android Studio 方式

这是最推荐的运行方式。

1. 顶部运行配置选择 `app`。
2. 顶部设备栏选择真机或模拟器。
3. 点击绿色三角形 `Run`。
4. 等待编译、安装、自动启动 App。

### 5.2 命令行方式

在项目根目录执行：

```powershell
.\gradlew.bat assembleDebug
```

安装到已连接设备：

```powershell
.\gradlew.bat installDebug
```

运行单元测试：

```powershell
.\gradlew.bat testDebugUnitTest
```

如果使用 macOS 或 Linux：

```bash
./gradlew assembleDebug
./gradlew installDebug
./gradlew testDebugUnitTest
```

## 6. APK 生成说明

### 6.1 生成 debug APK

Android Studio 菜单：

`Build` -> `Build Bundle(s) / APK(s)` -> `Build APK(s)`

生成后点击右下角提示中的 `locate`，默认路径一般为：

```text
app/build/outputs/apk/debug/app-debug.apk
```

debug APK 适合课程验收、真机测试和功能演示。

### 6.2 生成 release APK

如果需要正式签名包：

`Build` -> `Generate Signed Bundle / APK...` -> `APK`

然后创建或选择 keystore，选择 `release` 构建即可。

## 7. 验证方式

常用验证命令：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

手动验收主链路：

1. 启动 App，查看网盘首页。
2. 进入文件页，测试文件夹进入和返回上一级。
3. 切换全部、图片、视频、文档筛选。
4. 长按文件进入管理模式，测试勾选、全选、重命名、移动、删除、分享。
5. 点击右下角 `+` 上传本地文件，观察文件页和首页最近转存是否刷新。
6. 点击 TXT 文件进入阅读器，测试分页、字号调整、滑动翻页和底部控件显示隐藏。
7. 点击视频文件，确认可以拉起系统播放器。
8. 分享文件后复制分享链接，退出并重新打开 App，确认剪贴板识别并进入分享预览页。
9. 在分享预览页点击保存到网盘，确认文件页和首页最近转存刷新。

## 8. 日志查看

项目使用 Timber 输出调试日志。TXT 阅读器性能日志的 tag 是：

```text
TxtReaderPerf
```

在 Android Studio 中查看：

1. 打开 `Logcat`。
2. 选择当前运行设备。
3. 选择 App 进程 `com.example.simple_pan`。
4. 搜索 `TxtReaderPerf`。

可以观察到类似日志：

```text
read success fileId=... chars=139749 costMs=29
paginate success generation=... chars=139749 pages=525 costMs=2975
pagination cache hit generation=... pages=525 lookupCostMs=0
```

这些日志用于对比 TXT 阅读器优化前后的性能表现。

## 9. 常见问题

### 9.1 Gradle Sync 失败

优先检查：

- Android Studio 是否能访问网络。
- 是否安装了对应 Android SDK。
- Gradle JDK 是否选择 Android Studio bundled JDK。

### 9.2 找不到真机

优先检查：

- 手机是否开启 USB 调试。
- 手机是否弹出并允许调试授权。
- 数据线是否支持数据传输。
- Android Studio 设备栏是否选择了正确设备。

### 9.3 运行时数据不是最新

Room 会保留本地数据库数据。如果修改了 mock 数据或初始化逻辑，建议在手机设置里清除 App 数据后重新启动。

路径一般为：

`系统设置` -> `应用` -> `SimplePan` -> `存储` -> `清除数据`

### 9.4 TXT 阅读器首次分页较慢

最终版阅读器使用 Compose `TextMeasurer` 做真实排版分页，首次打开长文本需要测量全文。项目已经加入分页缓存，相同文件、字号和页面尺寸再次打开时会明显变快。
