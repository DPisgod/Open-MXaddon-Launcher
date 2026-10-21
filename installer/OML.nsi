Unicode true
ManifestDPIAware true
RequestExecutionLevel user
SetCompressor /SOLID lzma
SetCompressorDictSize 32

!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "FileFunc.nsh"
!include "nsDialogs.nsh"
!include "x64.nsh"
!include "WinVer.nsh"

!ifndef PAYLOAD_DIR
  !error "Build with installer/build-installer.ps1 (PAYLOAD_DIR is required)."
!endif
!ifndef OUTPUT_FILE
  !error "OUTPUT_FILE is required."
!endif
!ifndef APP_VERSION
  !define APP_VERSION "1.0.0"
!endif

!define PRODUCT_KEY "Software\OML\Launcher"
!define UNINSTALL_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\OMLLauncher"
!define LEGACY_PRODUCT_KEY "Software\MXCloud\Launcher"
!define LEGACY_UNINSTALL_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\MXCloudLauncher"
!define APP_EXE "OML启动器.exe"
!define APP_UNINSTALLER "卸载 OML.exe"
!define MUI_ICON "assets\OML.ico"
!define MUI_UNICON "assets\OML.ico"
!define MUI_WELCOMEPAGE_TITLE "$InstallTitle"
!define MUI_WELCOMEPAGE_TEXT "$InstallMessage"
!define MUI_DIRECTORYPAGE_TEXT_TOP "选择 OML 的安装位置。游戏版本、账号和配置会保存在此目录中。"
!define MUI_FINISHPAGE_TITLE "OML 已安装"
!define MUI_FINISHPAGE_TEXT "安装已完成。你可以从桌面或开始菜单启动 OML。"
!define MUI_FINISHPAGE_RUN
!define MUI_FINISHPAGE_RUN_FUNCTION LaunchOML
!define MUI_FINISHPAGE_RUN_TEXT "启动 OML"
!define MUI_FINISHPAGE_RUN_NOTCHECKED
!define MUI_UNCONFIRMPAGE_TEXT_TOP "将永久删除整个安装文件夹，包含游戏、存档、账号、配置、皮肤和壁纸，并清空当前用户的启动器运行时缓存。$\r$\n$\r$\n需要保留的文件请先备份。请先关闭启动器和游戏。"

Name "OML"
Caption "OML 安装向导"
OutFile "${OUTPUT_FILE}"
InstallDir "D:\OML"
InstallDirRegKey HKCU "${PRODUCT_KEY}" "InstallDir"
BrandingText "OML"
ShowInstDetails show
ShowUninstDetails show
VIProductVersion "${APP_VERSION}.0"
VIAddVersionKey /LANG=2052 "ProductName" "OML 安装程序"
VIAddVersionKey /LANG=2052 "FileDescription" "OML 安装向导"
VIAddVersionKey /LANG=2052 "FileVersion" "${APP_VERSION}"
VIAddVersionKey /LANG=2052 "ProductVersion" "${APP_VERSION}"
VIAddVersionKey /LANG=2052 "LegalCopyright" "OML"

Var DesktopChoice
Var DesktopCheckbox
Var OptionsDialog
Var CommandLine
Var CommandValue
Var CleanTarget
Var CleanSafe
Var RuntimeTarget
Var InstallTitle
Var InstallMessage
Var ScanState
Var ExistingVersion
Var CleanOriginal
Var LegacyInstallDir

!insertmacro MUI_PAGE_WELCOME
!define MUI_PAGE_CUSTOMFUNCTION_LEAVE DirectoryLeave
!insertmacro MUI_PAGE_DIRECTORY
Page custom OptionsCreate OptionsLeave
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_UNPAGE_FINISH
!insertmacro MUI_LANGUAGE "SimpChinese"

Function .onInit
  SetShellVarContext current
  SetRegView 64
  ReadRegStr $LegacyInstallDir HKCU "${LEGACY_PRODUCT_KEY}" "InstallDir"
  ReadRegStr $0 HKCU "${PRODUCT_KEY}" "InstallDir"
  ; NSIS has already applied /D= here; preserve an explicit installation target.
  ${If} $0 == ""
  ${AndIf} $INSTDIR == "D:\OML"
  ${AndIf} $LegacyInstallDir != ""
    StrCpy $INSTDIR $LegacyInstallDir
  ${EndIf}
  ${IfNot} ${RunningX64}
    MessageBox MB_ICONSTOP "此版本需要 64 位 Windows 10 或更高版本。" /SD IDOK
    SetErrorLevel 1
    Quit
  ${EndIf}
  ${IfNot} ${AtLeastWin10}
    MessageBox MB_ICONSTOP "此版本需要 Windows 10 或更高版本。" /SD IDOK
    SetErrorLevel 1
    Quit
  ${EndIf}
  StrCpy $DesktopChoice ${BST_CHECKED}
  ClearErrors
  ReadRegDWORD $CommandValue HKCU "${PRODUCT_KEY}" "DesktopShortcut"
  ${If} ${Errors}
    ClearErrors
    ReadRegDWORD $CommandValue HKCU "${LEGACY_PRODUCT_KEY}" "DesktopShortcut"
  ${EndIf}
  ${IfNot} ${Errors}
    StrCpy $DesktopChoice $CommandValue
  ${EndIf}
  ${GetParameters} $CommandLine
  ClearErrors
  ${GetOptions} $CommandLine "/DESKTOP=" $CommandValue
  ${IfNot} ${Errors}
    ${If} $CommandValue == "0"
      StrCpy $DesktopChoice ${BST_UNCHECKED}
    ${ElseIf} $CommandValue == "1"
      StrCpy $DesktopChoice ${BST_CHECKED}
    ${Else}
      MessageBox MB_ICONSTOP "DESKTOP 参数只能是 0 或 1。" /SD IDOK
      SetErrorLevel 1
      Quit
    ${EndIf}
  ${EndIf}
  InitPluginsDir
  File /oname=$PLUGINSDIR\mx-inspect.exe "${INSPECTOR_FILE}"
  Call DetectInstallation
FunctionEnd

Function DetectInstallation
  nsExec::ExecToStack '"$PLUGINSDIR\mx-inspect.exe" inspect "$INSTDIR" "${APP_VERSION}"'
  Pop $0
  Pop $ScanState
  ReadINIStr $ExistingVersion "$INSTDIR\.mx-install.ini" "Install" "Version"
  ReadRegStr $2 HKCU "${PRODUCT_KEY}" "InstallDir"
  ${If} $2 == $INSTDIR
    ReadRegStr $2 HKCU "${UNINSTALL_KEY}" "DisplayVersion"
    ${If} $2 != $ExistingVersion
    ${AndIf} $ScanState != "new"
      StrCpy $ScanState "repair"
    ${EndIf}
  ${EndIf}
  ${If} $0 != 0
    StrCpy $ScanState "repair"
  ${EndIf}
  ${If} $ScanState == "update"
    StrCpy $InstallTitle "更新 OML"
    StrCpy $InstallMessage "已检测到完整的 OML 安装。$\r$\n$\r$\n已安装版本：$ExistingVersion$\r$\n本次版本：${APP_VERSION}$\r$\n$\r$\n将更新程序，保留游戏、账号、存档和壁纸。"
  ${ElseIf} $ScanState == "same"
    StrCpy $InstallTitle "更新 / 重新安装 OML"
    StrCpy $InstallMessage "已检测到完整的 OML ${APP_VERSION}，版本一致。$\r$\n$\r$\n你可以重新安装当前版本；游戏、账号、存档和壁纸会保留。"
  ${ElseIf} $ScanState == "repair"
    StrCpy $InstallTitle "修复 OML"
    StrCpy $InstallMessage "检测到本地程序文件缺失、校验不一致或版本信息不匹配。$\r$\n$\r$\n将修复为 OML ${APP_VERSION}，保留游戏、账号、存档和壁纸。"
  ${Else}
    StrCpy $InstallTitle "欢迎安装 OML ${APP_VERSION}"
    StrCpy $InstallMessage "将 OML 启动器安装到你的电脑。$\r$\n$\r$\n你可以选择安装位置，以及是否创建桌面快捷方式。$\r$\n$\r$\n启动器自带运行环境，无需另外安装 Java。"
  ${EndIf}
FunctionEnd

Function DirectoryLeave
  Call DetectInstallation
FunctionEnd

Function OptionsCreate
  !insertmacro MUI_HEADER_TEXT "快捷方式" "选择安装完成后要创建的快捷方式。"
  nsDialogs::Create 1018
  Pop $OptionsDialog
  ${If} $OptionsDialog == error
    Abort
  ${EndIf}
  ${NSD_CreateLabel} 0 0 100% 32u "$InstallTitle · ${APP_VERSION}$\r$\n$INSTDIR"
  Pop $0
  ${NSD_CreateCheckbox} 0 42u 100% 14u "创建桌面快捷方式"
  Pop $DesktopCheckbox
  ${NSD_SetState} $DesktopCheckbox $DesktopChoice
  ${NSD_CreateLabel} 0 70u 100% 28u "你也可以随时从开始菜单打开启动器。"
  Pop $0
  nsDialogs::Show
FunctionEnd

Function OptionsLeave
  ${NSD_GetState} $DesktopCheckbox $DesktopChoice
FunctionEnd

Function LaunchOML
  SetOutPath "$INSTDIR"
  Exec '"$INSTDIR\${APP_EXE}"'
FunctionEnd

Section "OML" Main
  SetOutPath "$INSTDIR"
  SetOverwrite on
  ClearErrors
  File "${PAYLOAD_DIR}\${APP_EXE}"
  File "${PAYLOAD_DIR}\使用说明.txt"
  ${If} ${Errors}
    MessageBox MB_ICONSTOP "无法写入安装目录。请关闭启动器，检查目录权限后重新安装。" /SD IDOK
    SetErrorLevel 1
    Abort
  ${EndIf}
  ClearErrors
  WriteUninstaller "$INSTDIR\${APP_UNINSTALLER}"
  ${If} ${Errors}
    MessageBox MB_ICONSTOP "无法写入卸载程序。请关闭正在运行的卸载程序，检查目录权限后重新安装。" /SD IDOK
    SetErrorLevel 1
    Abort
  ${EndIf}
  ; Commit expected hashes only after all program files exist and the embedded
  ; launcher version matches this setup. Never hash personal game/account data.
  nsExec::ExecToStack '"$PLUGINSDIR\mx-inspect.exe" write "$INSTDIR" "${APP_VERSION}"'
  Pop $0
  Pop $1
  ${If} $0 != 0
    MessageBox MB_ICONSTOP "程序完整性或版本检查失败，安装尚未完成。请重新下载匹配版本的安装包。" /SD IDOK
    SetErrorLevel 1
    Abort
  ${EndIf}
  CreateDirectory "$SMPROGRAMS\OML"
  ; build_shell.ps1 embeds two identical Mx icon groups. Recreate the links
  ; and use ordinal 1 to avoid the old ordinal 0 desktop icon cache.
  Delete "$SMPROGRAMS\OML\OML.lnk"
  CreateShortCut "$SMPROGRAMS\OML\OML.lnk" "$INSTDIR\${APP_EXE}" "" "$INSTDIR\${APP_EXE}" 1
  CreateShortCut "$SMPROGRAMS\OML\卸载 OML.lnk" "$INSTDIR\${APP_UNINSTALLER}" "" "$INSTDIR\${APP_UNINSTALLER}" 0
  ${If} $DesktopChoice == ${BST_CHECKED}
    Delete "$DESKTOP\OML.lnk"
    CreateShortCut "$DESKTOP\OML.lnk" "$INSTDIR\${APP_EXE}" "" "$INSTDIR\${APP_EXE}" 1
  ${EndIf}
  WriteRegStr HKCU "${PRODUCT_KEY}" "InstallDir" "$INSTDIR"
  WriteRegStr HKCU "${PRODUCT_KEY}" "LastInstallMode" "$ScanState"
  WriteRegDWORD HKCU "${PRODUCT_KEY}" "DesktopShortcut" $DesktopChoice
  WriteRegStr HKCU "${UNINSTALL_KEY}" "DisplayName" "OML"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "DisplayVersion" "${APP_VERSION}"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "Publisher" "OML"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "${UNINSTALL_KEY}" "DisplayIcon" '$\"$INSTDIR\${APP_EXE}$\",1'
  WriteRegStr HKCU "${UNINSTALL_KEY}" "UninstallString" '$\"$INSTDIR\${APP_UNINSTALLER}$\"'
  WriteRegStr HKCU "${UNINSTALL_KEY}" "QuietUninstallString" '$\"$INSTDIR\${APP_UNINSTALLER}$\" /S'
  WriteRegDWORD HKCU "${UNINSTALL_KEY}" "NoModify" 1
  WriteRegDWORD HKCU "${UNINSTALL_KEY}" "NoRepair" 1
  SectionGetSize ${Main} $0
  WriteRegDWORD HKCU "${UNINSTALL_KEY}" "EstimatedSize" $0
  ; Remove only the exact legacy program assets, after shortcuts use embedded icons.
  Delete "$INSTDIR\MXCloud启动器.exe"
  Delete "$INSTDIR\卸载 MXCloud.exe"
  Delete "$INSTDIR\MXCloud.ico"
  Delete "$INSTDIR\MXCloud-Mx.ico"
  Delete "$INSTDIR\MXCloud-Mx-4e31b56b8701.ico"
  ${If} $LegacyInstallDir == $INSTDIR
    Delete "$DESKTOP\MXCloud.lnk"
    Delete "$SMPROGRAMS\MXCloud\MXCloud.lnk"
    Delete "$SMPROGRAMS\MXCloud\卸载 MXCloud.lnk"
    RMDir "$SMPROGRAMS\MXCloud"
    DeleteRegKey HKCU "${LEGACY_PRODUCT_KEY}"
    DeleteRegKey HKCU "${LEGACY_UNINSTALL_KEY}"
  ${EndIf}
  Delete "$INSTDIR\OML.ico"
  Delete "$INSTDIR\OML-Mx.ico"
  Delete "$INSTDIR\OML-Mx-4e31b56b8701.ico"
SectionEnd

Function un.onInit
  SetShellVarContext current
  SetRegView 64
FunctionEnd

; Recursive deletion is permitted only for an exact registered installation and
; two fixed application cache folders. Normalize and preflight every target;
; reject volume/profile/system roots and any reparse point before deleting.
Function un.CheckProtected
  Exch $0
  Push $1
  Push $2
  GetFullPathName $0 "$0"
  StrLen $1 $CleanTarget
  StrCpy $2 $0 $1
  ${If} $2 == $CleanTarget
    StrCpy $2 $0 1 $1
    ${If} $2 == ""
    ${OrIf} $2 == "\"
      StrCpy $CleanSafe 0
    ${EndIf}
  ${EndIf}
  Pop $2
  Pop $1
  Pop $0
FunctionEnd

Function un.CheckTree
  Exch $0
  Push $1
  Push $2
  Push $3
  System::Call 'kernel32::GetFileAttributesW(w r0) i .r3'
  ${If} $3 != -1
    IntOp $3 $3 & 0x400
    ${If} $3 != 0
      StrCpy $CleanSafe 0
    ${Else}
      FindFirst $1 $2 "$0\*"
      ${DoWhile} $2 != ""
        ${If} $2 != "."
        ${AndIf} $2 != ".."
          Push "$0\$2"
          Call un.CheckTree
        ${EndIf}
        ${If} $CleanSafe == 0
          ${ExitDo}
        ${EndIf}
        FindNext $1 $2
      ${Loop}
      FindClose $1
    ${EndIf}
  ${EndIf}
  Pop $3
  Pop $2
  Pop $1
  Pop $0
FunctionEnd

Function un.ValidateRoot
  StrCpy $CleanSafe 1
  StrCpy $CleanOriginal $CleanTarget
  ; GetFullPathNameW resolves absent cache folders too; the NSIS instruction
  ; can return an empty string when an intermediate component does not exist.
  System::Call 'kernel32::GetFullPathNameW(w "$CleanTarget", i ${NSIS_MAX_STRLEN}, w .r0, p 0) i .r1'
  StrCpy $CleanTarget $0
  ${If} $1 == 0
  ${OrIf} $1 >= ${NSIS_MAX_STRLEN}
    StrCpy $CleanSafe 0
    Return
  ${EndIf}
  StrCpy $0 $CleanTarget 2 1
  ${If} $0 != ":\"
    StrCpy $CleanSafe 0
  ${EndIf}
  ${GetRoot} "$CleanTarget" $0
  ${If} $CleanTarget == $0
  ${OrIf} $CleanTarget == "$0\"
    StrCpy $CleanSafe 0
  ${EndIf}
  !macro ProtectCleanupRoot Path
    Push "${Path}"
    Call un.CheckProtected
  !macroend
  !insertmacro ProtectCleanupRoot "$WINDIR"
  !insertmacro ProtectCleanupRoot "$SYSDIR"
  !insertmacro ProtectCleanupRoot "$PROFILE"
  !insertmacro ProtectCleanupRoot "$DESKTOP"
  !insertmacro ProtectCleanupRoot "$DOCUMENTS"
  !insertmacro ProtectCleanupRoot "$PROFILE\Downloads"
  !insertmacro ProtectCleanupRoot "$APPDATA"
  !insertmacro ProtectCleanupRoot "$LOCALAPPDATA"
  !insertmacro ProtectCleanupRoot "$TEMP"
  !insertmacro ProtectCleanupRoot "$PROGRAMFILES"
  !insertmacro ProtectCleanupRoot "$PROGRAMFILES64"
  !insertmacro ProtectCleanupRoot "$COMMONFILES"
  !insertmacro ProtectCleanupRoot "$COMMONFILES64"
  !insertmacro ProtectCleanupRoot "$SMPROGRAMS"
  ; A directory inside Windows is never a launcher-owned installation.
  StrLen $0 $WINDIR
  StrCpy $1 $CleanTarget $0
  StrCpy $2 $CleanTarget 1 $0
  ${If} $1 == $WINDIR
  ${AndIf} $2 == "\"
    StrCpy $CleanSafe 0
  ${EndIf}
  ; Catch junctions in ancestors as well as links anywhere inside the tree.
  StrCpy $0 $CleanTarget
  ${Do}
    System::Call 'kernel32::GetFileAttributesW(w r0) i .r1'
    ${If} $1 != -1
      IntOp $1 $1 & 0x400
      ${If} $1 != 0
        StrCpy $CleanSafe 0
      ${EndIf}
    ${EndIf}
    ${GetParent} "$0" $1
    ${If} $1 == $0
    ${OrIf} $1 == ""
      ${ExitDo}
    ${EndIf}
    StrCpy $0 $1
  ${Loop}
  ${If} $CleanSafe == 1
    Push "$CleanTarget"
    Call un.CheckTree
  ${EndIf}
FunctionEnd

Section "Uninstall"
  ReadRegStr $0 HKCU "${PRODUCT_KEY}" "InstallDir"
  GetFullPathName $0 "$0"
  GetFullPathName $INSTDIR "$INSTDIR"
  ${If} $0 != $INSTDIR
    MessageBox MB_ICONSTOP "安装位置与注册记录不一致，已停止卸载，未删除文件。请从安装目录运行卸载程序。" /SD IDOK
    SetErrorLevel 1
    Abort
  ${EndIf}
  StrCpy $RuntimeTarget "$TEMP\OML"
  StrCpy $CleanTarget $INSTDIR
  Call un.ValidateRoot
  ${If} $CleanSafe != 1
    Goto unsafe_cleanup
  ${EndIf}
  StrCpy $CleanTarget $RuntimeTarget
  Call un.ValidateRoot
  ${If} $CleanSafe != 1
    Goto unsafe_cleanup
  ${EndIf}
  SetOutPath "$TEMP"
  ClearErrors
  RMDir /r "$RuntimeTarget"
  RMDir /r "$INSTDIR"
  ; The copied uninstaller runs from NSIS's own temporary directory; it can
  ; remove the original executable and its parent without reboot scheduling.
  IfFileExists "$INSTDIR" incomplete_cleanup
  IfFileExists "$RuntimeTarget" incomplete_cleanup
  Delete "$DESKTOP\OML.lnk"
  Delete "$SMPROGRAMS\OML\OML.lnk"
  Delete "$SMPROGRAMS\OML\卸载 OML.lnk"
  RMDir "$SMPROGRAMS\OML"
  DeleteRegKey HKCU "${UNINSTALL_KEY}"
  DeleteRegKey HKCU "${PRODUCT_KEY}"
  Goto cleanup_finished
unsafe_cleanup:
  WriteRegStr HKCU "${PRODUCT_KEY}" "LastUninstallError" "Unsafe path or directory link: $CleanTarget (input $CleanOriginal)"
  MessageBox MB_ICONSTOP "检测到不安全的删除路径或目录链接，已停止卸载，未删除文件。请移除目录链接后再卸载。" /SD IDOK
  SetErrorLevel 1
  Abort
incomplete_cleanup:
  WriteRegStr HKCU "${PRODUCT_KEY}" "LastUninstallError" "Files in use or access denied during complete cleanup"
  MessageBox MB_ICONSTOP "部分文件正在使用或没有删除权限。请关闭启动器和游戏后重试卸载；清理尚未完成。" /SD IDOK
  SetErrorLevel 1
  Abort
cleanup_finished:
SectionEnd
