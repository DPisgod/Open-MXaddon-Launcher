# Open-MXaddon-Launcher

Minecraft Java 版启动器：安装官方清单中的正式版、快照 / 预发布、历史 Beta / Alpha，
选择 Fabric、Quilt、Forge、NeoForge，并下载模组、光影、材质与整合包。

本版本已剥离原工程中的**云载下发与 DLL 注入链路**：启动器不再联网验证授权、
不向服务端领取票据、不解密或注入私有 mod 包，也没有 `auth.dll` / `core.dll`
原生层。启动游戏的过程就是拼一条标准的 `java` 命令行。

---

## 下载

安装包与单文件版都在 **Releases** 页面：

<https://github.com/DPisgod/Open-MXaddon-Launcher/releases>

| 文件 | 说明 |
|---|---|
| `OML-Setup-1.3.10.exe` | 安装包（推荐）：安装向导、开始菜单/桌面快捷方式、完整性校验、自带卸载程序 |
| `OML-Launcher-1.3.10-portable.zip` | 免安装：解压后直接双击 `OML启动器.exe` |

两者都自带精简 Java 21 运行时，不需要另装 Java。首次启动后在主页版本列表选
「添加版本…」选择游戏版本和加载器；勾选自动下载 Java 会另行下载游戏所需的完整 Java，
历史版本通常使用 Java 8。安装到独立实例，不覆盖已有游戏目录。

OML 是 Open-MXaddon-Launcher 的简写。旧版 MXCloud 用户首次升级请手动下载 OML 安装包，
安装到原目录即可保留游戏、存档、账号与配置；旧版更新器无法识别新的 OML 安装包名称。
旧版本的内存配置文件会自动迁移为 `OML-settings.properties`。

### 游戏、加载器与资源下载

顶部「下载」现为主界面内的下载中心：横向切换游戏版本、Mod、整合包、数据包、资源包和光影包。游戏库提供最新正式/预览版本卡片、正式/预览/远古/愚人节分类及版本号搜索；配置安装时自动检查各加载器的兼容版本。

资源支持全部来源合并浏览、类型标签、排序、重置条件，以及简介、下载量、更新时间和项目图标。数据包只选择数据包 ZIP，安装到目标实例具体世界的 saves/<存档>/datapacks；也可仅下载文件。CurseForge 仍需在本机配置自己的 API Key，未配置时全部来源会提示并展示 Modrinth。

OptiFine 提供官网入口和本地 JAR 安装：先从官网下载与游戏版本匹配的安装包，在游戏配置中选择本地 JAR，启动器将在独立实例中完成安装。当前本地 OptiFine 独立安装与其他模组加载器互斥。


- 主页版本列表 → **添加版本…**：选择正式版、快照、历史 Beta / Alpha；可安装原版，或选择兼容的 Fabric / Quilt / Forge / NeoForge 版本，包括加载器测试版。
- 选择 Fabric 时可勾选**自动下载 Fabric API**。找不到匹配的 API 时会明确报错，可取消此选项后安装游戏。
- **仅下载加载器**：Fabric / Quilt 保存所选加载器 JAR，Forge / NeoForge 保存官方安装器 JAR。安装游戏时选择加载器则自动安装并配置。
- 顶部 **下载**：选择 Modrinth / CurseForge、模组 / 光影包 / 材质资源包 / 整合包；按 Minecraft 与加载器筛选，支持 Beta / Alpha 文件、分页和必需依赖下载。
- 模组、光影与材质分别安装到目标实例的 `mods`、`shaderpacks`、`resourcepacks`；光影仍需要游戏内适用的 Iris / OptiFine 等渲染支持。
- 整合包支持 Modrinth `.mrpack` 与 CurseForge `manifest.json` ZIP，可在线下载或本地导入；按清单安装游戏、加载器、文件和客户端覆盖配置。服务器专用文件不安装，未完成的实例不会进入版本列表。
- CurseForge 需要官方 API Key：在下载页的 **CurseForge 密钥** 中填写，使用 Windows DPAPI 加密保存。也可设置 `CURSEFORGE_API_KEY` 环境变量。未配置时 Modrinth 正常可用；作者未开放第三方下载的文件会提示到原项目页下载，不猜测下载地址。

### 玩家玩偶

右上角设置 → **玩家玩偶** → 开启显示并应用。自动使用已登录正版账号，也可输入正版 Java 玩家名或 UUID，获取皮肤和当前启用的官方披风；支持纤细手臂、皮肤外层及旧版皮肤。左键挥手、跳跃或转圈，按住拖动，滚轮平滑转身，右键打开设置、刷新或隐藏。可保存大小、置顶、披风开关和位置，获取失败时尝试缓存，没有识别到正版账号皮肤时显示内置原版 Steve；玩偶随启动器退出。

列表与页面支持滚轮平滑滚动，直接拖动滚动条仍即时定位。

### `.minecraft` 数据目录

游戏与配置现在位于启动器旁的 `.minecraft`。首次启动自动迁移旧 `.min`，保留账号、
存档、游戏版本、配置和公告关闭记录。两目录同时存在时合并不冲突的文件；已有同名实例或文件
保留原内容，旧内容移入 `.minecraft/.min-migration-backup-*`，可以手动找回。
每个游戏实例位于 `.minecraft/versions/<实例>/instance/`，拥有独立模组、材质、光影和存档。

每次启动都会在后台检查本仓库的最新正式 GitHub Release。发现更高版本且已上传
`OML-Setup-<版本>.exe` 时，提供「取消」和「立即更新」：取消后继续使用当前版本；
立即更新会直接从 GitHub 下载安装包，校验文件大小及 GitHub 提供的 SHA-256（如有），
打开安装向导并退出启动器。安装目录预设为当前启动器目录，保留游戏、账号与配置。
下载中也可取消；检查失败、没有 Release 或已经是最新版本时，正常使用启动器。
发布时请使用 `v主版本.次版本.修订号` 标签，并将安装包版本与标签保持一致。

### 内置壁纸

默认壁纸为 Windows10，另有水灵灵、线条风、刀客、动漫女孩四张动态壁纸，以及红色力量静态壁纸。
六张壁纸和媒体解码组件均随包内置，无需下载；原默认风景壁纸已移除。进入顶部「个性化」中的壁纸设置即可预览并应用。
用户自行导入的壁纸及已保存的选择继续保留。图标使用 OML 字母，界面名称使用 Open-MXaddon-Launcher。

### 主页更新公告

直接编辑仓库 `main` 分支根目录的 [`ANNOUNCEMENT.txt`](ANNOUNCEMENT.txt) 即可更新公告，
使用 UTF-8 纯文本（最多 32 KB），无需重新发布启动器。用户下次启动时会在后台读取该文件，
公告作为浮动卡片显示在主页壁纸上，可以拖动标题栏移动、滚动查看长文本或点右上角关闭。
关闭状态和位置保存在本机 `.minecraft/announcement.properties` 中；关闭过的相同内容不会再次显示，
实际文本变更后会重新显示。仅修改换行格式或首尾空白不会重复弹出。
清空或删除仓库公告文件可停止展示公告；网络异常时正常使用启动器。

---

## 一、架构

```
┌──────────────────────── 用户侧 ────────────────────────┐
│  OML启动器.exe                                     │
│    └─ 自解压到 %TEMP%\OML\<内容哈希>\              │
│         └─ 自带 JRE 21 + launcher jar（Swing 界面）    │
│              └─ GameLauncher：拼 java 命令启动游戏     │
│                     └─ Minecraft（Fabric / 原版）      │
└────────────────────────────────────────────────────────┘
                             │
                             ▼ 仅访问公开源
              Mojang（版本清单 / 依赖 / 资源）
              Fabric / Quilt Meta、Forge / NeoForge Maven
              Modrinth / CurseForge（资源清单与下载）
              Eclipse Adoptium（游戏 Java 运行时）
              Microsoft（正版账号 OAuth，可选）
              GitHub Releases（启动器检查更新 / 安装包）
```

启动器自身不产生任何自有后端请求。

## 二、目录

| 目录 | 内容 |
|---|---|
| `launcher-java/` | 启动器全部代码（Java 21 + Swing + JNA）与打包脚本 |
| `installer/` | NSIS 安装包脚本与安装完整性检查器 |
| `docs/` | 外观、安装包说明 |
| `website/`、`server-backend/`、`mod-auth/` | 原工程的官网 / 服务端 / mod 授权代码，**与启动器无关**，本版本未使用 |

`launcher-java/src/main/java/dev/mx/launcher/` 关键类：

```
Main.java              入口（单实例、主题、字体）
GitHubUpdater.java     GitHub Release 检查、安装包下载与完整性校验
LauncherUpdateDialog  可取消的更新提示、下载进度与安装向导
LauncherFrame.java     主页：版本列表、账号、壁纸、启动/停止
GameLauncher.java      纯 Java 启动：读版本 JSON → 拼命令行 → 拉起游戏进程
VersionInstaller.java  多版本安装、资源 / 原生库、重试、缓存与校验
DownloadCatalog       官方游戏 / 加载器清单
ContentCatalog        Modrinth / CurseForge 资源与依赖
ContentBrowserDialog  资源搜索、下载与整合包导入
ModpackInstaller      mrpack / CurseForge 清单及覆盖配置
GameJava              自动下载匹配版本的游戏 Java
VersionScanner/Manager 版本扫描与删除
AccountStore/Session   正版（微软 OAuth）/ 离线账号
MicrosoftLogin.java    微软设备码登录
ModManager             本地 mods 目录管理（添加/禁用/卸载）
Appearance/Wallpaper*  浅色/深色/跟随系统与本地动态壁纸
MemoryAllocation       内存分配（自动 / 跟随全局 / 自定义）
```

## 三、构建

前置：JDK 21（需 `javac` / `jar` / `jlink`）、可选 MSVC Build Tools（编自解压壳）、
可选 NSIS 3（打安装包）。JNA 依赖由脚本自动下载。

```powershell
# 一键构建（在仓库根目录）
.\一键构建.cmd

# 或分步执行
pwsh -File launcher-java\build.ps1        # → launcher-java\dist + dist-shell\OML启动器.exe
pwsh -File installer\build-installer.ps1  # → artifacts\installer\OML-Setup-<版本>.exe
```

产物：

| 文件 | 说明 |
|---|---|
| `launcher-java\dist\` | 运行目录（jar + JNA + 自带 JRE） |
| `launcher-java\dist-shell\OML启动器.exe` | 单文件版（内嵌运行时，双击即用） |
| `artifacts\installer\OML-Setup-<版本>.exe` | 安装包（开始菜单/桌面快捷方式、完整性校验、卸载） |

## 四、启动流程

1. 选择版本 → 读取 `.minecraft/versions/<版本>/<版本>.json`。
2. 依赖：版本 jar + 版本清单中的 Windows 依赖（缺一个都直接报错，不做静默降级）。
3. 原生库：优先用 `<版本>-natives`，否则从 `natives-windows` 依赖包中解出 DLL。
4. Java：优先使用设置中的指定路径，否则使用实例中下载的完整运行时，再使用兼容的启动器运行时。
5. 参数：`-Xms/-Xmx` + G1、`-Djava.library.path`、`--username/--uuid/--accessToken`、
   `--gameDir/--assetsDir/--assetIndex` 等；离线账号使用标准离线 UUID。
6. 日志：`<游戏目录>/logs/launcher-game.log`。

下载与整合包规范参照 [Modrinth 官方 API](https://docs.modrinth.com/api/)、
[CurseForge 官方 API](https://docs.curseforge.com/rest-api/)、
[Fabric Meta](https://github.com/FabricMC/fabric-meta)、
[Modrinth mrpack 格式](https://support.modrinth.com/en/articles/8802351-modrinth-modpack-format-mrpack)。
本实现未复制 PCL 的源码。

## 五、皮肤玩偶（桌宠）

主页上站着一个 3D 皮肤玩偶，跟着当前账号换皮肤：

- **正版账号**：加载官方皮肤与披风（sessionserver 的 textures 属性），细臂（slim / Alex）模型自动识别。
- **离线账号**：优先用你为该账号导入的本地皮肤；没有识别到皮肤时使用内置 Minecraft 原版 Steve 皮肤。
- **立体多层渲染**：头 8³、身体 8×12×4、四肢 4×12×4，按皮肤 UV 取六个面；皮肤自带的帽子 / 外套 / 袖子 / 裤子作为外层向外鼓 0.6 像素叠加，披风单独一层贴在后背；定向光 + 画家算法排序，带呼吸浮动与待机摆动。
- **交互**：左键拖动移动，右键或按住 Shift 拖动旋转，滚轮缩放，双击复位；位置、朝向、缩放写入 `appearance.properties`，下次启动恢复。
- **开关**：顶栏「个性化」→「皮肤玩偶」勾选框，随时开关，关闭后立即隐藏并记住设置。
开启独立桌面玩偶后，主页玩偶暂时隐藏；关闭桌面玩偶后恢复主页显示。主页玩偶右键点击可打开桌面玩偶设置。

## 六、许可

原工程为私人项目。本版本仅保留启动游戏所需功能，供本地使用。
