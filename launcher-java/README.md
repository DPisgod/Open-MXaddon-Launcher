# launcher-java

MXCloud 启动器的界面与启动逻辑，全部为纯 Java 实现。

## 组成

| 文件 | 作用 |
|---|---|
| `Main.java` | 入口：单实例、主题、字体、打开主页 |
| `LauncherFrame.java` | 主页：版本列表、账号切换、壁纸、启动/结束游戏 |
| `GameLauncher.java` | **启动核心**：解析版本 JSON、拼 java 命令行、拉起游戏进程 |
| `VersionInstaller.java` | 一键安装 Minecraft 1.21.1 + Fabric 0.19.3 + 资源（SHA-1 校验、重试、缓存） |
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
  .min\                       游戏根目录
    versions\<版本>\<版本>.json|.jar
    libraries\  assets\  logs\
    session.properties  accounts.dat  launcher.properties  appearance.properties
```

## 构建

```powershell
pwsh -File 打包.ps1              # jar + jlink 运行时 + 单文件 exe
pwsh -File 打包.ps1 -SkipShell   # 只生成 dist\
```

依赖（JNA 5.14.0）会自动下载到 `lib\`。单文件版本需要 MSVC Build Tools，
脚本通过 `shell\build_shell.ps1` 编译 `shell\MXCloudLauncher.cpp`。

## 说明

- 启动器只访问公开源：Mojang 版本清单/资源、FabricMC meta、微软 OAuth。
- 游戏进程日志：`<游戏目录>\logs\launcher-game.log`。
- 启动器日志：`.min\logs\launcher.log`。
