# Legado with MD3（本地阅读精简版）

## 📖 介绍

本项目是 [HapeLee/legado-with-MD3](https://github.com/HapeLee/legado-with-MD3)（其上游为
[gedoor/legado](https://github.com/gedoor/legado)）的 **本地阅读精简 fork**，基线为上游 tag
`3.26.16-beta.37`。

Fork 只保留 **本地目录 / 文件的纯阅读能力**，移除了所有在线内容与在线规则相关功能，作为一款离线使用的本地阅读器继续维护。项目遵循 **GPL-3.0** 许可证，原作者与上游项目归属见文末致谢。

> [!NOTE]
> 本 fork 与上游不再保持同步，功能范围以本仓库说明为准。包名为 `io.github.dasoops.reader`
> （代码 namespace 仍为 `io.legado.app`），可与原版同时安装。

## ✅ 保留功能

- **本地导入：** 支持本地 TXT、EPUB、MOBI、PDF 文件的导入与解析，可扫描本地目录。
- **阅读器：** 目录、章节、进度、书签、翻页模式（覆盖 / 仿真 / 滑动 / 滚动）。
- **阅读设置：** 字体、背景、行距、段距、加粗、简繁转换、屏幕方向等。
- **本地规则：** 替换规则、TXT 目录规则、高亮 / 标签规则（仅处理本地文本）。
- **书架与记录：** 书架、分组、书籍备注、阅读记录 / 统计。
- **数据管理：** WebDAV 备份与同步。

## 🚫 已移除功能

本 fork **不提供**下列功能，且不会恢复：

- 书源 / 在线书、在线搜索、在线缓存、发现 / 探索、在线漫画；
- RSS 订阅；
- 词典 / 翻译、AI 对话；
- 源登录、内置浏览器、扫码导入；
- 内嵌 Web 服务与 Web 前端；
- 在线朗读 / TTS、有声书 / 音频播放。

首次启动时书架为空，需要自行导入本地文件。

## 🛠️ 构建

需要 JDK 21。Windows 下执行：

```powershell
.\gradlew.bat :app:assembleAppDebug
```

产物位于 `app/build/outputs/apk/`。

## 📥 下载

本 fork 仅通过本仓库发布：

- Releases：<https://github.com/dasoops/legado-with-MD3/releases>

## ❤️ 致谢

本项目基于以下开源项目：

- [gedoor/legado](https://github.com/gedoor/legado)——原始阅读项目。
- [HapeLee/legado-with-MD3](https://github.com/HapeLee/legado-with-MD3)——Material Design 3
  重构版，本 fork 直接上游。
- 以及 MD3 版本所依赖的诸多开源库与设计灵感（MaterialKolor、Reorderable、komikku、Gramophone
  等）。

向上游作者与所有贡献者致谢。

## ⚠️ 用户协议与免责声明

> **【特别提示】**
> 在下载、安装或使用本软件前，请您务必仔细阅读并充分理解本协议及免责声明的全部内容。您一旦下载、安装或使用本软件，即视为已阅读、理解并同意接受本声明的全部内容。

1. 本软件是一款运行于本地的离线阅读工具，仅用于读取用户自行导入的本地文档（TXT / EPUB / MOBI /
   PDF），并进行文本排版与展示。
2. 本软件不提供、不内置、不预置任何在线书源、订阅源、内容解析规则或第三方内容服务，也不提供内容访问、下载、存储或传播能力。
3. 用户的阅读内容、阅读记录及相关数据均保存在用户本地设备或用户自行配置的 WebDAV 空间中，开发者不收集、不上传、不存储上述数据。
4. 用户应确保其导入、处理与阅读的文件来源合法，并遵守所在地法律法规及版权规范；因使用本软件产生的任何风险与责任，由用户自行承担。
5. 本软件按“现状”提供，开发者不对因使用或无法使用本软件造成的任何直接或间接损失承担责任。

---