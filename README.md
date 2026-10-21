# MXCloud 启动器（本地版）

Minecraft **Fabric 1.21.1** 启动器：安装游戏版本、管理账号与 Mod、启动游戏。

本版本已剥离原工程中的**云载下发与 DLL 注入链路**：启动器不再联网验证授权、
不向服务端领取票据、不下载/解密/注入任何 mod 包，也没有 `auth.dll` / `core.dll`
原生层。启动游戏的过程就是拼一条标准的 `java` 命令行。

---

## 下载

安装包与单文件版都在 **Releases** 页面：

<https://github.com/DPisgod/Open-MXaddon-Launcher/releases>

| 文件 | 说明 |
|---|---|
| `MXCloud-Setup-1.0.1.exe` | 安装包（推荐）：安装向导、开始菜单/桌面快捷方式、完整性校验、自带卸载程序 |
| `MXCloud-Launcher-1.0.1-portable.zip` | 免安装：解压后直接双击 `MXCloud启动器.exe` |

两者都自带精简 Java 21 运行时，不需要另装 Java。首次启动后在主页版本列表选
「添加版本…」一键安装 Minecraft 1.21.1 + Fabric，之后即可点「启动游戏」。

每次启动都会在后台检查本仓库的最新正式 GitHub Release。发现更高版本且已上传
`MXCloud-Setup-<版本>.exe` 时，提供「取消」和「立即更新」：取消后继续使用当前版本；
立即更新会直接从 GitHub 下载安装包，校验文件大小及 GitHub 提供的 SHA-256（如有），
打开安装向导并退出启动器。安装目录预设为当前启动器目录，保留游戏、账号与配置。
下载中也可取消；检查失败、没有 Release 或已经是最新版本时，正常使用启动器。
发布时请使用 `v主版本.次版本.修订号` 标签，并将安装包版本与标签保持一致。

### 主页更新公告

直接编辑仓库 `main` 分支根目录的 [`ANNOUNCEMENT.txt`](ANNOUNCEMENT.txt) 即可更新公告，
使用 UTF-8 纯文本（最多 32 KB），无需重新发布启动器。用户下次启动时会在后台读取该文件，
公告作为浮动卡片显示在主页壁纸上，可以拖动标题栏移动、滚动查看长文本或点右上角关闭。
关闭状态和位置保存在本机 `.min/announcement.properties` 中；关闭过的相同内容不会再次显示，
实际文本变更后会重新显示。仅修改换行格式或首尾空白不会重复弹出。
清空或删除仓库公告文件可停止展示公告；网络异常时正常使用启动器。

---

## 一、架构

```
┌──────────────────────── 用户侧 ────────────────────────┐
│  MXCloud启动器.exe                                     │
│    └─ 自解压到 %TEMP%\MXCloud\<内容哈希>\              │
│         └─ 自带 JRE 21 + launcher jar（Swing 界面）    │
│              └─ GameLauncher：拼 java 命令启动游戏     │
│                     └─ Minecraft（Fabric / 原版）      │
└────────────────────────────────────────────────────────┘
                             │
                             ▼ 仅访问公开源
              Mojang（版本清单 / 依赖 / 资源）
              FabricMC meta（加载器 profile）
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
VersionInstaller.java  一键安装 Minecraft + Fabric + 资源（SHA-1 校验）
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
pwsh -File launcher-java\打包.ps1        # → launcher-java\dist + dist-shell\MXCloud启动器.exe
pwsh -File installer\build-installer.ps1  # → artifacts\installer\MXCloud-Setup-<版本>.exe
```

产物：

| 文件 | 说明 |
|---|---|
| `launcher-java\dist\` | 运行目录（jar + JNA + 自带 JRE） |
| `launcher-java\dist-shell\MXCloud启动器.exe` | 单文件版（内嵌运行时，双击即用） |
| `artifacts\installer\MXCloud-Setup-<版本>.exe` | 安装包（开始菜单/桌面快捷方式、完整性校验、卸载） |

## 四、启动流程

1. 选择版本 → 读取 `.min/versions/<版本>/<版本>.json`。
2. 依赖：版本 jar + 版本清单中的 Windows 依赖（缺一个都直接报错，不做静默降级）。
3. 原生库：优先用 `<版本>-natives`，否则从 `natives-windows` 依赖包中解出 DLL。
4. Java：优先 `runtime\bin\javaw.exe`（自带），其次启动器所在 JVM，最后 `JAVA_HOME`。
5. 参数：`-Xms/-Xmx` + G1、`-Djava.library.path`、`--username/--uuid/--accessToken`、
   `--gameDir/--assetsDir/--assetIndex` 等；离线账号使用标准离线 UUID。
6. 日志：`<游戏目录>/logs/launcher-game.log`。

## 五、许可

原工程为私人项目。本版本仅保留启动游戏所需功能，供本地使用。
