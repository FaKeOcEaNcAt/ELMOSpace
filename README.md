# 艾莫空间

![Android](https://img.shields.io/badge/Android-WebView-3DDC84?logo=android&logoColor=white) ![Kotlin](https://img.shields.io/badge/Kotlin-Android-7F52FF?logo=kotlin&logoColor=white) ![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?logo=openjdk&logoColor=white) ![Gradle](https://img.shields.io/badge/Gradle-Build-02303A?logo=gradle&logoColor=white) ![License](https://img.shields.io/badge/License-Apache%202.0-blue) ![Status](https://img.shields.io/badge/Status-Beta-orange)

艾莫空间是一个面向 `gf2-bbs.exiliumgf.com` 移动端网页的非官方 Android WebView 客户端，用于改善移动端浏览体验。项目目前仍处于测试阶段，功能、界面和兼容性都可能继续调整。

当前版本：`V0.1.19-Build26080623147`

最新版本下载：

- [直达最新 Beta 测试版 Release](https://github.com/FaKeOcEaNcAt/ELMOSpace/releases/tag/Beta%E6%B5%8B%E8%AF%95%E7%89%88V0.1.19)
- [直接下载当前版本 APK](https://github.com/FaKeOcEaNcAt/ELMOSpace/releases/download/Beta%E6%B5%8B%E8%AF%95%E7%89%88V0.1.19/ElmoSpace-Beta-V0.1.19-Build2608062314.apk)
- [查看全部 Releases](https://github.com/FaKeOcEaNcAt/ELMOSpace/releases)

## 项目状态

- 本项目是同人软件、非官方软件，不代表目标网站或其运营方。
- App 只封装并优化官方网页的移动端访问体验，不提供绕过登录、验证码、风控、权限或付费校验的能力。
- 登录、账号、帖子、消息、签到等网页数据仍由官方网站和官方接口提供。
- 当前版本仍为测试版，可能存在页面适配不完整、深色模式覆盖不完整、部分国产系统后台限制导致定时任务不稳定等情况。

## 下载方式

如果你只是想安装使用，不需要下载源码。

推荐下载位置：

1. 打开 [最新 Beta 测试版 Release](https://github.com/FaKeOcEaNcAt/ELMOSpace/releases/tag/Beta%E6%B5%8B%E8%AF%95%E7%89%88V0.1.19)。
2. 在页面下方找到 `Assets`。
3. 下载其中的 `.apk` 安装包。
4. 在 Android 手机上安装该 APK。

当前版本也可以直接下载：

[ElmoSpace-Beta-V0.1.19-Build2608062314.apk](https://github.com/FaKeOcEaNcAt/ELMOSpace/releases/download/Beta%E6%B5%8B%E8%AF%95%E7%89%88V0.1.19/ElmoSpace-Beta-V0.1.19-Build2608062314.apk)

如果你没有使用过 GitHub，可以按下面的方式找：

- 手机浏览器打开 [项目仓库主页](https://github.com/FaKeOcEaNcAt/ELMOSpace) 后，先找页面里的 `Releases` 或 `发行版`。
- 如果看不到，可以点页面右上角或浏览器菜单，切换为桌面版网页再找。
- 下载时请选择 `.apk` 文件，不要下载 `Source code`，源码压缩包不能直接安装。

## 安装提示

- Android 可能会提示“未知来源应用”或“禁止安装未知应用”，这是手动安装 APK 的正常系统提示。
- 如果信任该安装包来源，可以在系统提示中允许当前浏览器或文件管理器安装应用。
- 如果不确定安装包来源，请不要安装第三方转发的 APK，建议只从本项目 GitHub Releases 下载。

## 主要功能

- 原生底部导航：主页、关注、发布、消息、我的。
- App 设置页：签到、显示、网络、权限、电源管理、安全、交互等设置项。
- 自动签到：支持打开 App 自动签到和定时签到相关能力。
- 深色模式：支持开启、关闭、跟随系统，但当前仍不是全站完整适配。
- 增强点赞交互：点赞时可显示自选表情包特效。
- 浏览历史：记录本地浏览过的帖子，并支持筛选已点赞的帖文，数据仅保存在本机。
- 移动数据提示和统计：可查看 App 使用移动网络时的大致流量统计。

## 当前测试版更新重点

`V0.1.16 Build2607212327` 主要包含以下调整：

- 修复首页点赞贴文后未进入历史记录“已点赞的帖文”筛选的问题。
- 历史记录筛选器强调色调整为 App 设置内统一橙色。
- 设置页开关、选择点赞交互特效、深色模式和定时签到时间选择控件统一为橙色强调色。
- 优化部分设置项高度和开关轨道可见性，减少设置页显示拥挤感。
- “签到时间”文案调整为“定时签到执行时间”。

## 隐私说明

App 原生层不会主动收集、上传或共享你的个人信息，也不会采集设备型号、硬件标识等设备信息。

需要注意：

- 官方网页自身的登录状态、Cookie、localStorage 和接口请求由官方网页控制。
- 浏览历史、设置项、流量统计等 App 功能数据保存在本机 App 私有目录中。
- 如果设备已 Root 或 Bootloader/OEM 锁定已解除，高权限程序理论上可能读取本机 App 数据，请注意设备安全。

## 给开发者

这是一个 Android WebView 壳 App 项目。上传或提交代码前，请确认以下内容没有进入 Git：

- 构建产物：`.gradle/`、`build/`、`app/build/`、`*.apk`、`*.aab`
- 本地配置：`local.properties`、`.idea/`
- 签名文件：`*.jks`、`*.keystore`、`*.p12`、`*.pem`、`keystore.properties`
- 调试材料：`_diagnostics/`
- 官方网页本地分析缓存：`_app.js`、`_vendors.js`、`_gf2_home.html`

## 免责声明

本项目仅用于移动端网页访问体验优化。网页内容、账号服务、社区规则、接口可用性均以官方网站为准。使用本 App 时仍需遵守目标网站的用户协议、社区规则和隐私政策。

## 开源协议

本项目代码采用 [Apache License 2.0](LICENSE) 开源。

除特别说明外，本仓库中由项目作者编写的 Android Activity 界面代码、WebView 壳层代码、本地设置/历史记录/流量统计/定时签到等原生功能代码，以及用于本地体验优化的注入脚本代码，版权归项目作者及后续贡献者所有。

Copyright © 2026 FaKeOcEaNcAt. All Rights Reserved.

需要注意：

- Apache License 2.0 仅适用于本仓库中原创或有权授权的代码与文档。
- 目标网站的官方网页、官方接口、官方前端脚本、官方图片素材、角色素材、社区内容和用户生成内容不属于本项目开源授权范围。
- 如果文档或代码中提到官方前端 `app.js`、轮播图 bug、页面结构或接口行为，这些内容仅用于兼容性分析、本地 WebView 适配或问题反馈，不代表本项目拥有官方前端代码版权，也不改变官方代码和素材的权利归属。
- 项目依赖的 Android、Gradle、Kotlin、Java、GitHub 等第三方工具、平台、库和服务仍分别适用其各自的许可协议、商标规则和使用条款。

## 商标归属声明

本项目为非官方同人软件，与《少女前线2：追放》、其官方网站、官方社区及其运营方不存在官方隶属、授权或合作关系。

《少女前线2：追放》及相关名称、角色、素材、社区内容等权利归上海暗冬网络科技有限公司及其关联权利方、相应权利人所有。

Android 是 Google LLC 的商标。GitHub 是 GitHub, Inc. 的商标。其他商标、服务标志、产品名称和公司名称均归其各自权利人所有。

## 表情包与肖像素材说明

增强点赞交互中使用的表情包素材涉及第三方创作者作品或人物肖像。项目一开始因为没打算开源，所以在版权处理问题上有所欠佳，我在此道歉，联系侵删。

当前素材归属说明如下：

- `Q版热浪回想-M4A1`：版权归 Pixiv 画师“[荒漠大鳄鱼](https://www.pixiv.net/users/21883757)”所有，BiliBili 主页：[荒漠大鳄鱼](https://space.bilibili.com/10855192)。
- `伟大领袖翀将军`：涉及“[黄翀](https://baike.baidu.com/item/%E7%BE%BD%E4%B8%AD/67741771)”先生的肖像权。
- `高兴的翀将军`：涉及“[黄翀](https://baike.baidu.com/item/%E7%BE%BD%E4%B8%AD/67741771)”先生的肖像权。
- `尼沙皇Nikita`：涉及 “[Nikita Buyanov](https://baike.baidu.com/item/%E5%B0%BC%E5%9F%BA%E5%A1%94%C2%B7%E5%B8%83%E4%BA%9A%E8%AF%BA%E5%A4%AB/66980211)” 先生的肖像权，X 账号：[nikgeneburn](https://x.com/nikgeneburn)。
- `神秘番茄大王`相关两项素材：版权均归 Pixiv 画师“[恶人Zevil](https://www.pixiv.net/users/113431759)”所有，BiliBili 主页：[恶人Zevil](https://space.bilibili.com/291277379)。
- `可爱的Doro索普`：版权归 Pixiv 画师“[ShinManyNight](https://www.pixiv.net/users/105723917)”所有。

如相关权利人认为本项目使用方式不当，请通过 GitHub Issue 联系处理。
