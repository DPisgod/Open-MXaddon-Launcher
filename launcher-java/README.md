# launcher-java

Open-MXaddon-Launcher的界面与启动逻辑，全部为纯 Java 实现。

## 组成

| 文件 | 作用 |
|---|---|
| `Main.java` | 入口：单实例、主题、字体、打开主页 |
| `LauncherFrame.java` | 主页：版本列表、账号切换、壁纸、启动/结束游戏 |
| `GameLauncher.java` | **启动核心**：解析版本 JSON、拼 java 命令行、拉起游戏进程 |
| `VersionInstaller.java` | 官方多版本安装与隔离实例、原生库、资源、重试、缓存及哈希校验 |
| `DownloadCatalog.java` / `LoaderInstaller.java` | 官方版本与 Fabric / Quilt / Forge / NeoForge 清单和安装 |
| `ContentCatalog.java` / `ContentBrowserDialog.java` | Modrinth / CurseForge 搜索、文件版本、依赖与资源安装 |
| `ModpackInstaller.java` / `GameJava.java` | 整合包清单导入与游戏 Java 自动下载 |
| `VersionScanner.java` / `VersionManager.java` | 版本扫描、删除（移入回收目录） |
| `MicrosoftLogin.java` | 微软设备码 OAuth 登录（正版账号） |
| `AccountStore.java` / `Session.java` / `SessionStore.java` | 账号保存（DPAPI 加密）与当前会话 |
| `SkinFetcher.java` | 正版皮肤头像 / 离线 PNG 皮肤导入 |
| `ModManager.java` / `VersionSettingsDialog.java` | 本地 mods 管理：添加、禁用、卸载、定位 |
| `Appearance*.java` / `Wallpaper*.java` | 浅色/深色/跟随系统、本地图片与视频壁纸 |
| `MemoryAllocation.java` / `MemoryPanel.java` | 内存分配策略 |
| `UiTheme.java` / `WindowChrome.java` | 主题绘制与无边框窗口 |

本版本**没有** DLL 调用、没有云载下载、没有授权票据：`GameLauncher` 只做
「读本地版本 → 拼命令行 → 启动进程」。

## 目录约定

```
<启动器目录>\
  runtime\                    自带精简 JRE（jlink 生成）
  mxaddon-launcher.jar        启动器本体
  jna-*.jar                   JNA 依赖
  .minecraft\                 游戏与启动器配置目录（旧 .min 自动迁移）
    versions\<版本>\<版本>.json|.jar
    libraries\  assets\  logs\
    session.properties  accounts.dat  launcher.properties  appearance.properties
```

## 构建

```powershell
pwsh -File build.ps1              # jar + jlink 运行时 + 单文件 exe
pwsh -File build.ps1 -SkipShell   # 只生成 dist\
```

依赖（JNA 5.14.0）会自动下载到 `lib\`。单文件版本需要 MSVC Build Tools，
脚本通过 `shell\build_shell.ps1` 编译 `shell\OMLLauncher.cpp`。

## 说明

- 设置提供 Java 检测与切换列表，置顶“自动选择”；自动模式优先实例运行时，再匹配本机相同主版本，保留手动指定路径。
- 界面风格平铺为六个单选项：浅色、深色、霓虹赛博、中国红、樱花粉、基佬紫；点击圆点立即应用并保存。
- 个性化中的 UI 透明度跟随滑块实时预览，松开鼠标后保存；拖动过程不写配置，保存失败恢复原值。
- 游戏与资源直接访问官方 Mojang、加载器发布源、Modrinth / CurseForge 与 Adoptium；CurseForge Key 仅本机加密保存。
- 游戏进程日志：`<游戏目录>\logs\launcher-game.log`。
- 游戏目录：`.minecraft`；每个新实例独立存放于 `versions/<实例>/instance`。
