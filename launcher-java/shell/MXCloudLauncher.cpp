// MXCloudLauncher.cpp —— 自解压启动器壳子
//
// 作用：把内嵌的启动器运行时（jar / JRE）解压到 %TEMP%，
// 然后启动图形界面。用户看到的目录里只有：
//
//     MXCloud启动器.exe
//     .min\              （游戏目录，用户自备）
//
// 设计要点：
//   * 内嵌内容只有启动器本身：不含游戏、不含任何 mod、不连接授权服务。
//   * 解压到 %TEMP%\MXCloud\<内容版本>\，用内容哈希做缓存：
//     版本一致则跳过解压，直接复用，加快二次启动。
//   * 通过 -Dmxcloud.home=<exe目录> 告诉 Java 侧：.min / 设置 / 皮肤缓存
//     都在 exe 旁边，而不是临时目录。
//   * 启动后本进程立即退出（不常驻）。

#define WIN32_LEAN_AND_MEAN
#define _WIN32_WINNT 0x0601
#include <windows.h>
#include <shlwapi.h>
#include <cstdio>
#include <cstdarg>
#include <string>
#include <vector>

#pragma comment(lib, "shlwapi.lib")

// ============================================================
//  内嵌资源
//  由 pack_shell.ps1 在编译前生成 resources.rc / payload 文件。
//  这里只声明外部符号，实际数据在 payload.rc 里。
// ============================================================

// 资源 ID 由构建脚本生成的 payload_ids.h 提供
#include "payload_ids.h"

#ifndef MXCLOUD_BUILD_ID
#define MXCLOUD_BUILD_ID "dev"
#endif

// ============================================================
//  工具
// ============================================================

// 前置声明（log_open 需要 file_exists）
static bool file_exists(const std::wstring &p);

// 调试日志：仅当 exe 同级存在 debug.txt 时才写入。
// （/SUBSYSTEM:WINDOWS 下没有 stdout，排错靠这个文件。）
static FILE *g_log = nullptr;

static void log_open(const std::wstring &home) {
    std::wstring p = home + L"\\debug.txt";
    if (!file_exists(p)) return;
    _wfopen_s(&g_log, p.c_str(), L"w, ccs=UTF-8");
}

static void logf(const wchar_t *fmt, ...) {
    if (!g_log) return;
    va_list ap;
    va_start(ap, fmt);
    vfwprintf(g_log, fmt, ap);
    va_end(ap);
    fputwc(L'\n', g_log);
    fflush(g_log);
}

static std::wstring exe_path() {
    // 用堆分配：路径可能超过 MAX_PATH
    std::vector<wchar_t> buf(32768, L'\0');
    DWORD n = GetModuleFileNameW(nullptr, buf.data(), (DWORD)buf.size());
    return std::wstring(buf.data(), n);
}

static std::wstring exe_dir() {
    std::wstring p = exe_path();
    size_t pos = p.find_last_of(L"\\/");
    return (pos == std::wstring::npos) ? L"." : p.substr(0, pos);
}

static bool file_exists(const std::wstring &p) {
    return GetFileAttributesW(p.c_str()) != INVALID_FILE_ATTRIBUTES;
}

static bool dir_exists(const std::wstring &p) {
    DWORD a = GetFileAttributesW(p.c_str());
    return a != INVALID_FILE_ATTRIBUTES && (a & FILE_ATTRIBUTE_DIRECTORY);
}

static bool ensure_dir(const std::wstring &p) {
    if (dir_exists(p)) return true;
    // 递归创建
    size_t pos = p.find_last_of(L"\\/");
    if (pos != std::wstring::npos && pos > 2) {
        ensure_dir(p.substr(0, pos));
    }
    return CreateDirectoryW(p.c_str(), nullptr) != 0 || dir_exists(p);
}

static std::wstring temp_root() {
    wchar_t buf[MAX_PATH];
    DWORD n = GetTempPathW(MAX_PATH, buf);
    std::wstring s(buf, n);
    while (!s.empty() && (s.back() == L'\\' || s.back() == L'/')) s.pop_back();
    return s;
}

// ============================================================
//  资源读取
// ============================================================

struct Res {
    const void *data;
    DWORD size;
};

static bool load_res(int id, Res &out) {
    HRSRC h = FindResourceW(nullptr, MAKEINTRESOURCEW(id), RT_RCDATA);
    if (!h) {
        logf(L"[FAIL] FindResource id=%d err=%lu", id, GetLastError());
        return false;
    }
    DWORD size = SizeofResource(nullptr, h);
    HGLOBAL g = LoadResource(nullptr, h);
    if (!g || size == 0) {
        logf(L"[FAIL] LoadResource id=%d err=%lu size=%lu", id, GetLastError(), size);
        return false;
    }
    const void *p = LockResource(g);
    if (!p) {
        logf(L"[FAIL] LockResource id=%d err=%lu", id, GetLastError());
        return false;
    }
    out.data = p;
    out.size = size;
    return true;
}

// 把资源原样写入目标文件（分块写，避免一次性大块调用）
static bool write_res_to_file(int id, const std::wstring &dest) {
    Res r{};
    if (!load_res(id, r)) return false;
    logf(L"资源大小 = %lu 字节", r.size);

    HANDLE f = CreateFileW(dest.c_str(), GENERIC_WRITE, 0, nullptr,
                           CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (f == INVALID_HANDLE_VALUE) {
        logf(L"[FAIL] CreateFile %ls err=%lu", dest.c_str(), GetLastError());
        return false;
    }

    const BYTE *p = static_cast<const BYTE *>(r.data);
    DWORD remain = r.size;
    while (remain > 0) {
        DWORD chunk = remain > (1u << 20) ? (1u << 20) : remain;   // 每次最多 1MB
        DWORD written = 0;
        if (!WriteFile(f, p, chunk, &written, nullptr) || written != chunk) {
            logf(L"[FAIL] WriteFile err=%lu written=%lu chunk=%lu",
                 GetLastError(), written, chunk);
            CloseHandle(f);
            return false;
        }
        p += chunk;
        remain -= chunk;
    }
    CloseHandle(f);
    logf(L"已写出 %ls", dest.c_str());
    return true;
}

// ============================================================
//  内嵌 ZIP 解压（调用系统内置 tar/Expand-Archive 不可靠，
//  这里用最简实现：payload 存为「未压缩 zip」，用 Shell 解压）
//
//  简化方案：构建时把运行时目录打成 .zip 作为资源；
//  运行时落盘到 %TEMP% 后调用 PowerShell Expand-Archive 解压。
//  首次启动略慢，之后命中缓存直接跳过。
// ============================================================

static bool run_hidden(const std::wstring &cmdline, DWORD timeoutMs = 180000) {
    std::vector<wchar_t> buf(cmdline.begin(), cmdline.end());
    buf.push_back(L'\0');

    STARTUPINFOW si{};
    si.cb = sizeof(si);
    PROCESS_INFORMATION pi{};
    si.dwFlags = STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;

    if (!CreateProcessW(nullptr, buf.data(), nullptr, nullptr, FALSE,
                        CREATE_NO_WINDOW, nullptr, nullptr, &si, &pi)) {
        logf(L"[FAIL] CreateProcess(解压) err=%lu", GetLastError());
        return false;
    }
    DWORD w = WaitForSingleObject(pi.hProcess, timeoutMs);
    DWORD code = (DWORD)-1;
    GetExitCodeProcess(pi.hProcess, &code);
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);

    if (w == WAIT_TIMEOUT) {
        logf(L"[FAIL] 解压超时");
        return false;
    }
    return code == 0;
}

// ============================================================
//  主流程
//
//  用 wWinMain（/SUBSYSTEM:WINDOWS）—— 双击时不弹控制台窗口。
//  出错信息通过 MessageBox 展示（因为看不到 stdout）。
// ============================================================

/** 弹窗报错（Windows 子系统下没有控制台，必须用对话框）。 */
static void fail_box(const std::wstring &msg) {
    MessageBoxW(nullptr, msg.c_str(), L"MXCloud 启动器", MB_ICONERROR | MB_OK);
}

struct ExtractionGuard {
    HANDLE handle = nullptr;
    bool owned = false;
    ~ExtractionGuard() { if (owned) ReleaseMutex(handle); if (handle) CloseHandle(handle); }
};

int APIENTRY wWinMain(HINSTANCE, HINSTANCE, LPWSTR, int) {
    // Serialize first-run extraction. Java owns the long-lived, per-user UI lock.
    ExtractionGuard extraction;
    extraction.handle = CreateMutexW(nullptr, FALSE, L"Local\\MXCloud.Extract." MXCLOUD_BUILD_ID);
    DWORD wait = extraction.handle ? WaitForSingleObject(extraction.handle, 180000) : WAIT_FAILED;
    extraction.owned = wait == WAIT_OBJECT_0 || wait == WAIT_ABANDONED;
    if (!extraction.owned) { fail_box(L"启动器正在准备运行环境，请稍后重试。"); return 1; }
    const std::wstring home = exe_dir();                 // exe 目录（.min 所在）
    log_open(home);
    logf(L"home     = %ls", home.c_str());

    const std::wstring tmpRoot = temp_root();
    const std::wstring cacheDir =
        tmpRoot + L"\\MXCloud\\" + std::wstring(L"" MXCLOUD_BUILD_ID);
    logf(L"cacheDir = %ls", cacheDir.c_str());

    const std::wstring jarPath = cacheDir + L"\\mxaddon-launcher.jar";
    const std::wstring jreDir  = cacheDir + L"\\runtime";

    // 1) 检查缓存：jar / jre 齐全则跳过解压
    const std::wstring ready = cacheDir + L"\\runtime.ready";
    bool cached = file_exists(ready) && file_exists(jarPath)
               && file_exists(jreDir + L"\\bin\\javaw.exe");
    logf(L"cached   = %d", cached ? 1 : 0);

    if (!cached) {
        if (!ensure_dir(cacheDir)) {
            logf(L"[FAIL] ensure_dir");
            fail_box(L"无法创建运行目录：\n" + cacheDir);
            return 1;
        }
        logf(L"cacheDir 已创建");

        // 解压 payload.zip 到 cacheDir
        const std::wstring zipPath = cacheDir + L"\\payload.zip";
        logf(L"写出 payload.zip ...");
        if (!write_res_to_file(IDR_PAYLOAD_ZIP, zipPath)) {
            logf(L"[FAIL] write_res_to_file");
            fail_box(L"释放运行环境失败（无法写出临时文件）。\n\n"
                     L"请检查杀毒软件是否拦截，或 %TEMP% 是否可写。");
            return 1;
        }
        logf(L"payload.zip 已写出");

        // 用 PowerShell 解压（系统自带，无需额外组件）
        std::wstring cmd = L"powershell -NoProfile -ExecutionPolicy Bypass -Command "
                           L"\"Expand-Archive -LiteralPath '" + zipPath +
                           L"' -DestinationPath '" + cacheDir + L"' -Force\"";
        logf(L"执行解压: %ls", cmd.c_str());
        if (!run_hidden(cmd)) {
            logf(L"[FAIL] run_hidden(Expand-Archive)");
            fail_box(L"解压运行环境失败。\n\n请检查杀毒软件、%TEMP% 权限，或手动重试。");
            return 1;
        }
        logf(L"解压完成");
        DeleteFileW(zipPath.c_str());

        if (!file_exists(jarPath) || !file_exists(jreDir + L"\\bin\\javaw.exe")) {
            logf(L"[FAIL] 解压结果不完整: jar=%d jre=%d",
                 file_exists(jarPath) ? 1 : 0,
                 file_exists(jreDir + L"\\bin\\javaw.exe") ? 1 : 0);
            fail_box(L"解压结果不完整。\n\n请删除 %TEMP%\\MXCloud 后重试。");
            return 1;
        }
        logf(L"解压结果校验通过");
        HANDLE marker = CreateFileW(ready.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
        if (marker != INVALID_HANDLE_VALUE) CloseHandle(marker);
    }

    // 2) 找 javaw（优先自带 JRE）
    std::wstring javaw = jreDir + L"\\bin\\javaw.exe";
    if (!file_exists(javaw)) {
        javaw = L"javaw";  // 回退系统
    }

    // 3) 组装命令行
    //    -Dmxcloud.home 指向 exe 目录；游戏和设置位于 .min，皮肤缓存仍在 exe 旁。
    std::wstring cp = jarPath + L";" + cacheDir + L"\\jna-5.14.0.jar;"
                    + cacheDir + L"\\jna-platform-5.14.0.jar";

    std::wstring cmdline = L"\"" + javaw + L"\"";
    cmdline += L" -Dfile.encoding=UTF-8";
    // Configure before AWT loads: avoid the Direct3D maximize/surface recreation path.
    // These options belong only to the launcher JVM, never to the game process.
    cmdline += L" -Dsun.java2d.d3d=false -Dsun.java2d.opengl=false";
    cmdline += L" -Dmxcloud.home=\"" + home + L"\"";
    cmdline += L" -cp \"" + cp + L"\"";
    cmdline += L" dev.mx.launcher.Main";

    // 4) 启动（不等待，壳子立即退出）
    logf(L"启动命令: %ls", cmdline.c_str());

    std::vector<wchar_t> buf(cmdline.begin(), cmdline.end());
    buf.push_back(L'\0');

    STARTUPINFOW si{};
    si.cb = sizeof(si);
    PROCESS_INFORMATION pi{};
    if (!CreateProcessW(nullptr, buf.data(), nullptr, nullptr, FALSE,
                        CREATE_NO_WINDOW, nullptr, cacheDir.c_str(), &si, &pi)) {
        logf(L"[FAIL] CreateProcess err=%lu", GetLastError());
        fail_box(L"启动失败（错误码 " + std::to_wstring(GetLastError()) + L"）。");
        return 1;
    }
    logf(L"[OK] 已启动 PID=%lu", pi.dwProcessId);
    CloseHandle(pi.hThread);
    CloseHandle(pi.hProcess);
    return 0;
}
