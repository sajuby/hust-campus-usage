# 华中科技大学校园用量助手

苦于华科各种查询入口不统一，我搓了一个校园生活查询应用，集中查看电费、能源费、校园网套餐与城市出行路线。工程同时提供电脑端设计工作台，方便在手机尺寸下预览页面、调整配色与布局，再将配置应用到 Android 调试版。

目前为开发版本，应用版本为 `0.5.1`。已适配华中科技大学网安基地的水电能源查询与校园网套餐入口；主校区水电费通过独立的官方页面查询。

## 直接下载安装 APK

如果只想直接使用，无需下载源码或配置开发环境，下载 APK 后在 Android 手机上安装即可。

- [直接下载 APK：0.5.1 校园网登录修复版](https://github.com/sajuby/hust-campus-usage/releases/download/v0.5.1-preview.1/hust-campus-usage-0.5.1-preview.1.apk)
- [查看版本页面及下载附件](https://github.com/sajuby/hust-campus-usage/releases/tag/v0.5.1-preview.1)
- [下载 SHA-256 校验文件](https://github.com/sajuby/hust-campus-usage/releases/download/v0.5.1-preview.1/hust-campus-usage-0.5.1-preview.1.apk.sha256)

APK 在仓库中的位置：**Releases → v0.5.1-preview.1 → hust-campus-usage-0.5.1-preview.1.apk**。安装包存放在 Releases 附件中，源码目录不包含 APK。

支持 **Android 12（API 31）及以上**。下载后在手机打开 APK，按系统提示允许当前浏览器或文件管理器安装应用。安装完成后，在“我的”中连接自己的学校账户即可查询；安装包不附带任何人的账户、登录状态或账单记录。

当前为 `0.5.1-dev` 调试签名的开发预览版，安装后名称为“校园用量助手·开发版”。主校区水电费仍使用官方查询页面。

可直接覆盖安装本仓库 Releases 的 `0.5.0` 预览版，保留登录、缓存和图片设置。更早的其他签名本地测试包不能直接覆盖；需要卸载时，请先保留所需数据，卸载会清除本地登录和自定义图片设置。

`0.5.1` 修复了校园网业务会话过期后出现 HTTP 500 却不恢复认证的问题，并迁移旧版 Cookie 路径。统一认证仍有效时尝试恢复一次；需要重新登录时打开官方连接页。学校业务服务本身故障时仍保留缓存，不反复登录。说明见 [校园网会话修复](docs/NETWORK_SESSION_FIX.md)。

## 功能

- 用量查询：电费、能源费支持切换日期、查看日/月数据和历史趋势；点击“更新”主动请求服务端数据。
- 统计状态：区分有效零用量、尚未发布、查询失败和本地缓存。按次日早上 08:00 作为统计预计发布时间提示，实际是否可查以官方返回为准。
- 校园网：展示套餐、余额、到期时间和流量分类。查询使用本人通过学校官方认证建立的会话。
- 保持登录：水电能源可选择记住登录；会话失效时最多尝试一次恢复。校园网业务会话失效时通过官方入口尝试恢复一次，需要验证时打开官方连接页；会话有效期由学校系统决定。
- 校区切换：网安基地使用已适配的查询功能；主校区打开官方水电费页面，不混用两个校区的缓存。
- 路线查询：通过定位或手动设置起终点查询城市通路线，展示路线分段、预计耗时与到达时间。
- 地铁资料：内置武汉地铁公开线网及首末班参考资料。首末班摘要并非完整班次表，不能保证赶上最后一次换乘。
- 主题与外观：黑色人物主题，支持从设备选择首页、趋势页、个人页三张图片及背景图片；趋势页图片保持原比例完整显示。
- 本地设计工作台：浏览器预览手机页面，编辑设计参数、文案与模块顺序，保存或撤销，并同步配置到已安装的 Android 调试版。

## 工程结构

```text
android/           Android 原生应用、单元测试和 Gradle Wrapper
ui-preview/        React + Vite 本地设计工作台
design/            共用设计配置和默认美术资源
shared/            TypeScript 模型、统计规则及公开地铁资料
tests/             设计配置测试和 Playwright 界面测试
scripts/           设计同步、资源处理及发布文件检查
docs/              架构、隐私和验证说明
```

Android 使用 Kotlin、Jetpack Compose、Room、OkHttp 与 Android Keystore。电脑预览使用 React、TypeScript 和 Vite。预览中的账单、余额和路线为虚构演示，不会连接学校账户；真实查询在 Android 应用中进行。

## 电脑预览与界面编辑

使用 Node.js 22.18 或更新版本及 npm，在仓库根目录执行：

```sh
npm ci
npm run dev
```

打开终端显示的本地地址，默认是 `http://127.0.0.1:5173`。设计参数保存在 `design/project.json`，`design/defaults.json` 提供初始值。工作台只监听本机地址。

浏览器中上传的预览图片保存在该浏览器的本地存储中；Android 中选择的图片保存在该应用的私有目录中，两者各自独立。图片替换不会自动提交进仓库。

## 构建 Android 调试版

用 Android Studio 打开 `android/`，安装 JDK 17、Android SDK Platform 36 和对应构建工具，完成 Gradle 同步。通过设备管理器创建 Android 12（API 31）或更高版本的模拟器，也可连接已开启 USB 调试的手机。

也可以配置 `JAVA_HOME`、`ANDROID_HOME`，在 `android/` 目录执行：

```sh
# Windows
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug

# macOS / Linux
sh ./gradlew :app:testDebugUnitTest :app:assembleDebug
```

APK 输出到 `android/app/build/outputs/apk/debug/app-debug.apk`。调试包标识为 `dev.campususage.app.debug`。工程目前限制正式 Release 构建，发布签名和正式发行流程尚未配置。

在设备中安装调试版并打开应用后，工作台可使用“应用到 Android”同步设计。同步需要 SDK 中的 `adb`，通过 `ANDROID_HOME`、`ANDROID_SDK_ROOT` 或系统 PATH 查找；存在多个设备时设置 `CAMPUS_DEVICE_SERIAL`。也可执行 `npm run sync:android -- --apply`。同步的是设计配置，不包含账户会话和个人照片。

## 首次连接

在“我的”中连接水电能源与校园网。请在设备的登录页面自行输入账户信息或完成学校统一认证。选择保持登录时，必要的凭据与会话通过 Android Keystore 加密保存在应用私有存储中。

主校区请进入官方水电费页面按官方流程登录、绑定房间；当前工程没有主校区原生账单解析适配。路线查询只在主动使用时请求定位权限，也支持手动选择地点。当前实现不需要配置高德 Key。

## 验证

```sh
npm test
npm run build
npx playwright install chromium
npm run test:ui
python scripts/audit_release.py
```

如果使用本机已安装的 Chrome，可设置 `CAMPUS_CHROME_PATH` 为浏览器可执行文件路径。详细验证范围见 [测试说明](docs/TESTING.md)。公开源码副本的验证结果见 [脱敏记录](docs/SANITIZATION.md)。

## 数据与隐私

仓库不包含个人账户、房间绑定、登录会话、真实账单、定位历史、个人照片、设备备份或签名密钥。演示数据和账号测试夹具为虚构样例，第三方地铁公开资料保留来源信息。运行时数据的位置与清理方式见 [隐私说明](docs/PRIVACY.md)。

工程没有自建账户数据上传服务。真实查询会直接访问对应学校或出行服务；服务可用性、认证方式和统计时效可能随提供方调整。此项目为个人开发工具，与学校及相关服务运营方无隶属关系。

## 资源与第三方说明

默认插画仅用于展示当前界面，允许在本地主题设置中替换。资源来源与使用范围见 [资源说明](design/assets/README.md)。坐标转换等第三方组件的原始许可声明保留在 [第三方声明](android/app/src/main/assets/third-party-notices.txt)，公开地铁资料的出处记录在 [source.json](shared/metro/source.json)。

更多实现说明见 [工程架构](docs/ARCHITECTURE.md)。
