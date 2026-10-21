# 下载中心

下载区沿用 Open-MXaddon-Launcher 的主题和组件。主页面使用横向分类导航、并列版本卡片、可搜索的版本库；资源页采用紧凑筛选区和结果/详情分栏，支持浅色、深色和 1000×740 最小窗口。

游戏元数据来自 Mojang 官方 manifest；加载器由 Fabric/Quilt 官方 Meta、Forge/NeoForge 官方 Maven 提供。愚人节使用已知特殊版本标识，不把四月一日发布的普通快照误分类。

资源由 Modrinth / CurseForge 官方 API 提供。全部来源按各平台每次 20 项分页并交错展示，保留来源；某个平台不可用时显示提示。类别来自平台标签清单，数据包使用 Modrinth 的 datapack loader 和 ZIP 文件筛选。数据包安装到具体存档，不会创建一个无效的全局 datapacks 目录。

OptiFine 使用用户自行从官网下载的 JAR，不随程序分发 OptiFine。安装在独立 JVM 与新建实例的暂存目录完成，先核对 JAR 声明的 Minecraft 版本，再导入官方安装器生成的版本配置、启动参数与依赖库。当前仅支持独立安装，与 Fabric/Quilt/Forge/NeoForge 互斥。

## 参考与资料

少量参考 [PCL 下载页面源码](https://github.com/Meloong-Git/PCL/tree/main/Plain%20Craft%20Launcher%202/Pages/PageDownload) 中的兼容状态展示和资源分页思路，感谢 PCL 作者。界面和 Java 下载适配由本项目实现。

- [Mojang 版本清单](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)
- [Modrinth 搜索 API](https://docs.modrinth.com/api/operations/searchprojects/)
- [CurseForge 官方 API](https://docs.curseforge.com/rest-api/)
- [OptiFine 官网下载](https://optifine.net/downloads)

## 验证

ContentCatalogTest 覆盖双来源、分页、鉴权隔离、依赖和数据包 ZIP 筛选。DownloadCenterTest 覆盖分类、关键词、六个导航入口、存档路径、主题与最小尺寸。DownloadCenterLiveUiTest 通过真实页面按钮搜索和安装 Modrinth 模组、数据包。OptiFineLocalLiveTest 使用从官网获取的本地 JAR 与一次性原版副本验证补丁、参数、版本配置和依赖库导入。
