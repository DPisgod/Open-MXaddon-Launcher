# F 主页与 H 壁纸工坊

启动器主页采用全幅壁纸和底部悬浮启动栏。顶部包含品牌、「主页 / 壁纸」切换、主题胶囊、设置以及内置的最小化、最大化/还原、关闭按钮，不再显示 Windows 系统标题栏。账号入口集中在左下角，点击头像、名字或账号类型均向上展开列表。每次启动完成后「启动游戏」继续可用，支持多开。上方圆形电源按钮显示当前客户端数量，立即结束本启动器创建的全部游戏及其子进程，保留其他进程。版本安装、删除与 Mod 管理见 `launcher-versions.md`。

## 游戏账号与皮肤头像

官网登录、游戏账号添加和微软验证码窗口均使用主题 Surface 背景、自绘按钮及明确的表单行高。移除旧版原生 GridLayout/BoxLayout 表单的白色背景和挤压布局，输入、提示、标题与关闭按钮在浅深色下均可见。官网登录保留失败重试与自动绑机；验证码未就绪时禁止打开浏览器。LoginDialogVisualTest 在 Windows 原生外观下显示三个真实窗口，验证浅深色及 100%/125% 绘制、圆角、空输入校验、模拟错误/成功、密码缓冲清理和账号操作，不发送真实登录请求。

上拉列表中可点击已添加的正版 / 离线账号直接切换；底部只保留添加账号入口。各行右侧有独立皮肤入口和删除按钮。删除需确认，只移除启动器本机保存的信息；删除当前账号后选择剩余账号，删完则显示「添加游戏账号」，不会重新生成被删账号。多账号列表通过当前 Windows 用户的 DPAPI 加密保存至 `accounts.dat`，沿用原来 `session.properties` 的当前会话兼容格式。正版会话到期仍需重新登录。

账号及配置集中保存在启动器旁的 `.minecraft/`：`accounts.dat`、`device-session.dat`、`session.properties`、`launcher.properties`、`appearance.properties`。首次读写前自动移动旧版根目录中的同名文件，原始字节不变，DPAPI 缓存仍由原 Windows 用户读取；之后只从 `.minecraft` 读取和写入。目标已有同名配置时以目标为准，把旧文件保存到 `.minecraft/.launcher-migration-<唯一标识>/`，避免覆盖或丢失。迁移失败时阻止主窗口启动并提示具体路径问题。皮肤缓存 `skins/`、壁纸 `wallpapers/`、可选的 `server.properties` 保持原路径。

首次运行不再生成 Player。旧版没有显式添加标记的离线 Player 会清理，已有正版账号及其他离线账号保留；新版手动添加的同名账号保留。官网授权成功后若没有游戏账号，自动打开添加窗口；取消后仍可稍后点击账号区添加。微软设备码登录在打开浏览器前自动复制 user_code，剪贴板不可用时提示手动复制，不复制访问令牌。

离线名称支持 1–16 个汉字、英文字母、数字或下划线；超长或含非法字符时明确提示，不再静默删掉中文或改成 Player。Java 到原生 DLL 已显式传递 UTF-8，Rust 保留名称并用于离线 UUID 与启动参数。

正版头像优先按 UUID 获取 Mojang 官方皮肤并裁出脸部及头饰层，可点击刷新。离线英文名尝试匹配同名官方皮肤；中文名或没有官方档案的离线账号，可点击该行皮肤图标导入 Minecraft PNG 皮肤，为该账号设置皮肤大头。64×64、64×32 与等比例高清 PNG 均可导入，最高 2048 像素 / 8 MB，损坏文件不覆盖原头像。无缓存或网络不可用时从本地游戏资源读取默认皮肤头部，不再显示带肩膀的小人占位图。此入口设置的是启动器头像；不修改游戏内皮肤或服务器验证策略。

## 壁纸使用

壁纸缩略图的选中边框保留滚动视口的裁剪范围；较小窗口和多张壁纸时，卡片与底部导入区分开绘制，避免最后一行描边越过列表边界。透明列表使用完整重绘滚动，减少旧图像残留。

打开顶栏「壁纸」，点击「导入壁纸」选择一个或多个本地文件，也可以将文件拖进壁纸列表。主页移除重复的「更换背景」入口。工坊的「返回主页」固定为 102×38，不随标题行拉高。支持 JPG/JPEG、PNG、WebP 静态图片以及 GIF、MP4、WebM 动态文件。导入前检查格式、文件大小和实际解码结果；损坏或不支持的文件不会留下半成品。单文件上限 1 GB。

导入文件复制到启动器目录的 `wallpapers/`，缩略图和索引保存在同一目录，原始文件之后移动或删除不会影响已导入的壁纸。壁纸仅在本地处理，不上传到服务器。外观偏好单独存储在 `.minecraft/appearance.properties`，不改变登录凭据或游戏设置。

在右侧预览后，可调整填充 / 适应 / 拉伸、模糊度、遮罩强度、UI 透明度、循环播放、静音播放和「游戏启动后暂停」。这些改动点击「应用壁纸」后才保存并用于主页。浅深色切换即时保存。关闭后重新打开可恢复选择和偏好。

「UI 透明度」范围为 0%–100%，0% 保持原有面板背景，100% 让顶栏和底部启动栏背景完全透出同一张壁纸。文字、图标和操作按钮保持独立绘制，浅深色均生效。右侧缩略预览包含顶栏与启动栏，拖动可预览，点击「应用壁纸」保存至 `.minecraft/appearance.properties`。这里调整的是界面面板的背景混合，顶层窗口仍是不透明的正常 Windows 窗口，保留 QQ 智能框选兼容性。

动态壁纸默认静音并循环播放；可在预览区暂停。最小化窗口时停止解码，打开壁纸工坊时暂停主页的后台播放；切回主页后恢复。开启游戏暂停选项时，启动游戏的等待阶段和游戏进程存活期间均停止后台解码，游戏结束后恢复。恢复动态壁纸时从片头重新播放，当前不提供时间轴拖动。视频解码尺寸最高 1280×720；已经移除旧版固定 24 fps，保留每一帧的源时间戳并按实际时间呈现，支持原片的高帧率与 GIF / 可变帧率延迟。不会通过插帧把低帧率视频变成高帧率，显示器刷新率和机器性能仍会影响实际效果。

## 内存设置与鼠标焦点

全局设置支持自动配置和自定义，版本设置另提供跟随全局。自定义仅使用与单选项对齐的一条滑条调整，右侧实时显示 MB，范围保持 1024–32768 MB；删除旧版输入框及增减按钮。自动或跟随全局时，自定义滑条置灰，实际预算仍在下次启动前根据可用内存重新计算。

鼠标点击后不再残留按钮蓝色焦点框；键盘操作时仍显示焦点，保留 Tab 导航。

## 实现与构建

- `Appearance.java`：独立偏好设置和持久化。
- `WallpaperLibrary.java`：本地导入、文件校验、缩略图与索引。
- `WallpaperPlayer.java`：后台解码、受控帧发布、静音/音频、暂停、循环及关闭清理。
- `WallpaperView.java`：统一的壁纸缩放、遮罩和模糊。
- `UiTheme.java`：主题、线性图标、圆角控件、滑块与开关。
- `AppearancePanel.java`、`LauncherFrame.java`：H 壁纸工坊与 F 主页。
- `AccountStore.java`、`AccountPopup.java`、`SkinFetcher.java`：多账号、删除、皮肤大头与加密列表。
- `VersionInstaller.java`、`InstallVersionDialog.java`：兼容版本完整安装与取消、缓存、校验。
- `ModManager.java`、`VersionSettingsDialog.java`：当前版本的本地 Mod 管理与文件拖入。

运行根目录 `build-integration.ps1`，然后运行 `launcher-java/shell/build_shell.ps1 -DistDir <artifacts/client绝对路径> -OutDir <artifacts/single绝对路径>`。构建机需有 FFmpeg Windows x64 可执行文件；构建脚本将媒体组件、其原始许可证和构建 README 放入分发包的 `Media/`。FFmpeg 是独立子进程，不与授权 DLL 链接；分发时应保留第三方声明。具体构建的源码地址在打包的 `Media/FFmpeg-README.txt` 中。

分发包包含默认风景和播放组件，不包含个人壁纸、`appearance.properties`、账号缓存、测试文件或 `.minecraft` 游戏目录。再次构建不会删除已有单文件版目录中的游戏或用户文件。

## 测试记录（2026-10-19）

`WallpaperBehaviorTest` 使用实际媒体文件验证六种格式导入、中文及空格文件名、真实像素变化、暂停 / 恢复、关闭后进程释放、关闭循环在文件末尾停止、带静音音轨的视频、已暂停时的正确海报帧、配置恢复，以及损坏文件回滚。测试目录独立于生产账号和游戏。

`WallpaperTimingTest` 将实际解码帧绘制到 1140×720 的生产壁纸视图，测得 30 fps 素材约 30.01 fps、60 fps 素材约 59.98 fps。测试覆盖当前机器的软件绘制，不能代表所有显示器或硬件。

`AccountBehaviorTest`、`AccountUiBehaviorTest`、`SkinHeadTest` 覆盖真实 DPAPI 读写、去重、中文名恢复、损坏库不覆盖、头像点击打开列表、切换、取消删除、删除当前 / 最后账号、重启不重现、按账号裁脸与透明头饰、错误皮肤回滚。截图中的正版账号为无访问权限的演示会话，头像示例来自本机游戏资源，不冒充真实微软登录。

`LauncherDataMigrationTest` 覆盖五个状态文件的原始字节迁移、真实 DPAPI 解密、迁移后的账号/会话/内存/外观读取与保存、并发调用、重复调用、冲突备份、新目录首次写入和迁移失败保留旧文件。验证不改动游戏存档、皮肤或壁纸，读写不再在根目录生成旧状态文件。

`SkinLookupLiveTest` 通过公开档案验证了离线英文名与正版 UUID 两种真实皮肤查询、裁头和缓存路径，无需登录账号。


`LauncherDesignShot` 使用真实界面绘制浅色 / 深色主页、浅色 / 深色工坊，以及最小窗口尺寸下的布局。它不初始化授权 DLL，不能启动游戏，也不绕过任何服务器检查。

`MemoryAndClientsTest` 验证自动内存预算、版本跟随/独立配置、账号迁移、空账号提示区、真实子进程结束且无关子进程保留、连续启动后按钮可用、合成验证码复制及紧凑返回按钮。`VisualPolishTest` 验证普通不透明窗口与 DWM 圆角状态或旧系统的形状回退；`WindowChromeTest` 覆盖拖动、缩放、最大化/还原。为兼容 QQ 智能框选，窗口改为原生 DWM 圆角并保留正常边框元数据和系统识别名称，移除原有逐像素透明遮罩。`WindowCaptureTest` 验证 GDI 和 PrintWindow 实际抓图、截图未排除、无分层/工具窗口样式、完整客户区和系统名称。视频解码以 packed RGB 帧交给 Swing，减少缩放时的像素转换。此前 `RoundedWallpaperTimingTest` 使用不透明目标验证完整主界面在 100%/125% 缩放下的 30/60 fps，实测 30.00/29.98 和 59.95/59.95 fps（当前机器）。用户已确认 QQ 可以获取窗口。

原生组件实测同时启动两个 MxAddon 2.1.4 客户端，直接检查进程参数确认分别为 -Xmx2048m 与 -Xmx3072m，两者保持运行后由进程管理器全部结束。本次未再次进入世界；此前进入单人世界的证据保留在 artifacts/ui-final-evidence/previous-cloud-world.log。新增证据在 artifacts/ui-final-evidence/native-multi。

真实 Windows 窗口点测覆盖了系统文件选择入口、MP4 壁纸选择、实时模糊、应用保存和返回主页。六种格式的实际导入与解码由媒体测试覆盖；系统文件选择器内的文件提交未通过桌面自动化工具完整执行。

实际生产入口 `Main` 使用测试账号运行，在新版主页点击「启动游戏」后成功启动 Fabric 0.19.3 / Minecraft 1.21.1 / MxAddon 2.1.4，游戏日志记录三层验证通过。游戏启动前存在一个 FFmpeg 壁纸子进程，游戏运行时为零，退出游戏后恢复一个。测试账号之后停用并撤销测试激活，不占用设备绑定。

实机证据与构建校验存放在被 Git 忽略的 `artifacts/ui-pretty-evidence/`。

## 默认壁纸素材

资源：`launcher-java/src/main/resources/wallpapers/default-landscape.png`。使用内置 image_gen 生成，作为当前项目随包素材，原始图片保留在 Codex generated_images 目录。提示词：

> Use case: illustration-story. Asset type: default wallpaper background for a premium Minecraft desktop launcher. Generate a beautiful cinematic Minecraft voxel landscape, wide 16:9 composition, 2560 by 1440 feel. Peaceful alpine lake reflecting distant jagged snowy peaks, pine trees on distant shores, grassy flowering block meadow in foreground, golden dawn sunlight from upper left, cool blue water and softly peach clouds, elegant restrained atmosphere and convincing voxel geometry. Composition keeps central sky and lake calm for desktop controls, horizon above center, important mountains in middle third, darker grass at very bottom for overlay dock readability. High quality physically lit game artwork, detailed but calm, not photorealistic real mountains. No buildings, no characters, no UI, no frame, no logos, no text, no watermark.

本轮 `AppearanceControlsTest` 验证自定义内存只有滑条、MB 数值同步、自动模式禁用滑条、全局保存和重新打开恢复；验证鼠标/键盘焦点、浅深色 0%/50%/100% 背景混合、连续壁纸、最小窗口布局、预览不提前保存和应用持久化。100% UI 透明度下仍检查原生窗口样式，并通过 GDI 与 PrintWindow 获取内容。`WallpaperBehaviorTest`、`MemoryAndClientsTest`、`WindowChromeTest` 和 `VisualPolishTest` 回归通过。

当前机器的完整窗口视频复测（UI 透明度 60%，3 秒预热、5 秒采样）中，30 fps 素材约 30.00/29.79 fps；60 fps 素材约 50.17/49.38 fps（100%/125%）。同环境下提交前版本约 50.95/38.58 fps。60 fps 的源帧率 ±5% 目标本轮未通过，不能把此前满帧结果当作本轮结果。通过缓存遮罩、先合成不透明 RGB 场景并按设备像素绘制，减少 125% 缩放的透明像素变换；没有更改源时间戳播放或跳帧规则。

## 最大化稳定性修复

用户在旧包 79eae74083cd 点击最大化后出现整机无响应，并异常重启；系统记录 Kernel-Power 41，没有可用的 Java 崩溃栈，不能据此断定具体驱动或原生调用的根因。旧实现为无系统标题栏窗口补充原生边框元数据后，仍调用 `Frame.MAXIMIZED_BOTH` 进入原生最大化。

新版启动 EXE 在创建 JVM 时指定 `sun.java2d.d3d=false` 和 `sun.java2d.opengl=false`，Main 也在初始化 AWT 前配置相同选项。仅改变启动器的 Java2D 绘制，不向游戏 JVM 传递这些选项。相关问题的官方排查说明：https://docs.oracle.com/en/java/javase/22/troubleshoot/java-2d-pipeline-rendering-and-properties.html 。

最大化改为保存正常位置/大小并调整为当前显示器工作区；还原时恢复原矩形，不使用原生 MAXIMIZED_BOTH 切换。窗口取消原生最大化框和厚边框元数据，保留 QQ 识别所需的系统名称、普通不透明窗口及标题元数据；内置按钮、双击顶栏、最小化和自绘边缘缩放保留。最大化时关闭边缘缩放，圆角属性只在状态变化时更新，壁纸缓存不参与硬件加速。

新增 `.minecraft/logs/launcher.log`，记录启动器 Java/渲染配置、最大化/还原尺寸及未处理异常；超过 1 MiB 的旧日志在下次启动时轮换为 launcher.previous.log。日志仅保存在本地。

`MaximizeStabilityTest` 在隔离账号目录中通过 100%/125% 缩放下共 96 次切换，覆盖浅深色、静态和视频壁纸、事件线程心跳、原尺寸还原、最小化/恢复和 GDI/PrintWindow 抓图。另用用户当前壁纸导入临时目录，重复 48 次切换。测试断言实际 GraphicsConfiguration 不使用 D3D、未设置原生最大化状态且窗口精确等于工作区。故障旧包不再用于复现测试；隔离结果不等于已经确认驱动层根因。

修复包 dcbb40bfe8e7 已覆盖 mxtest 的 EXE，并使用用户现有账号缓存和壁纸通过电脑操作实际完成最大化、观察绘制及还原。进程持续响应，窗口精确为 1920×1080 工作区、还原为原 1140×810 位置；日志确认 d3d/opengl=false。真实窗口保持运行，未启动游戏或修改设备绑定。
