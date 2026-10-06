#include "sentinel/process_monitor.hpp"

#include "sentinel/analysis.hpp"
#include "sentinel/logging.hpp"
#include "sentinel/scanner.hpp"

#include <atomic>
#include <algorithm>
#include <filesystem>
#include <functional>
#include <iostream>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#ifdef _WIN32
#include <windows.h>
#include <tlhelp32.h>
#else
#error "Process monitoring currently uses Windows APIs."
#endif

namespace sentinel {
namespace {

std::atomic<bool> stop_requested{false};

BOOL WINAPI handle_console_control(DWORD control) {
    if (control == CTRL_C_EVENT || control == CTRL_BREAK_EVENT ||
        control == CTRL_CLOSE_EVENT || control == CTRL_LOGOFF_EVENT ||
        control == CTRL_SHUTDOWN_EVENT) {
        stop_requested.store(true);
        return TRUE;
    }
    return FALSE;
}

class SnapshotHandle {
public:
    explicit SnapshotHandle(HANDLE handle) : handle_(handle) {}
    ~SnapshotHandle() {
        if (handle_ != INVALID_HANDLE_VALUE) {
            CloseHandle(handle_);
        }
    }

    SnapshotHandle(const SnapshotHandle&) = delete;
    SnapshotHandle& operator=(const SnapshotHandle&) = delete;

    HANDLE get() const noexcept {
        return handle_;
    }

private:
    HANDLE handle_;
};

class ProcessHandle {
public:
    explicit ProcessHandle(HANDLE handle) : handle_(handle) {}
    ~ProcessHandle() {
        if (handle_ != nullptr) {
            CloseHandle(handle_);
        }
    }

    ProcessHandle(const ProcessHandle&) = delete;
    ProcessHandle& operator=(const ProcessHandle&) = delete;

    HANDLE get() const noexcept {
        return handle_;
    }

private:
    HANDLE handle_;
};

std::string to_utf8(const std::wstring& value) {
    if (value.empty()) {
        return {};
    }
    const int required = WideCharToMultiByte(
        CP_UTF8,
        WC_ERR_INVALID_CHARS,
        value.data(),
        static_cast<int>(value.size()),
        nullptr,
        0,
        nullptr,
        nullptr);
    if (required == 0) {
        throw std::runtime_error(
            "Cannot convert process metadata to UTF-8 (Windows error " +
            std::to_string(GetLastError()) + ").");
    }

    std::string result(static_cast<std::size_t>(required), '\0');
    if (WideCharToMultiByte(
            CP_UTF8,
            WC_ERR_INVALID_CHARS,
            value.data(),
            static_cast<int>(value.size()),
            result.data(),
            required,
            nullptr,
            nullptr) == 0) {
        throw std::runtime_error(
            "Cannot convert process metadata to UTF-8 (Windows error " +
            std::to_string(GetLastError()) + ").");
    }
    return result;
}

std::vector<PROCESSENTRY32W> take_process_snapshot() {
    SnapshotHandle snapshot(CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0));
    if (snapshot.get() == INVALID_HANDLE_VALUE) {
        throw std::runtime_error(
            "Cannot enumerate processes (Windows error " +
            std::to_string(GetLastError()) + ").");
    }

    PROCESSENTRY32W entry{};
    entry.dwSize = sizeof(entry);
    if (!Process32FirstW(snapshot.get(), &entry)) {
        const DWORD error = GetLastError();
        if (error == ERROR_NO_MORE_FILES) {
            return {};
        }
        throw std::runtime_error(
            "Cannot read process snapshot (Windows error " +
            std::to_string(error) + ").");
    }

    std::vector<PROCESSENTRY32W> processes;
    do {
        processes.push_back(entry);
        entry.dwSize = sizeof(entry);
    } while (Process32NextW(snapshot.get(), &entry));

    const DWORD error = GetLastError();
    if (error != ERROR_NO_MORE_FILES) {
        throw std::runtime_error(
            "Process enumeration ended unexpectedly (Windows error " +
            std::to_string(error) + ").");
    }
    return processes;
}

std::wstring process_image_path(HANDLE process) {
    std::vector<wchar_t> buffer(32768);
    DWORD length = static_cast<DWORD>(buffer.size());
    if (!QueryFullProcessImageNameW(process, 0, buffer.data(), &length)) {
        throw std::runtime_error(
            "Cannot query process image path (Windows error " +
            std::to_string(GetLastError()) + ").");
    }
    return std::wstring(buffer.data(), length);
}

void report_new_process(
    const PROCESSENTRY32W& entry,
    const SignatureDatabase& signatures,
    Logger* logger) {
    std::cout << "[PROCESS START] PID " << entry.th32ProcessID
              << " | " << to_utf8(entry.szExeFile) << '\n';
    if (logger != nullptr) {
        logger->write(
            "info", "process_started",
            "New process observed: " + to_utf8(entry.szExeFile) +
                " (PID " + std::to_string(entry.th32ProcessID) + ")");
    }

    ProcessHandle process(OpenProcess(
        PROCESS_QUERY_LIMITED_INFORMATION,
        FALSE,
        entry.th32ProcessID));
    if (process.get() == nullptr) {
        std::cerr << "[WARN] Cannot inspect PID " << entry.th32ProcessID
                  << " (Windows error " << GetLastError()
                  << "); image signature scan skipped.\n";
        if (logger != nullptr) {
            logger->write(
                "warning", "process_image_unavailable",
                "Could not inspect process image; Windows denied or lost process "
                "access (PID " + std::to_string(entry.th32ProcessID) + ").");
        }
        return;
    }

    const std::wstring image_path = process_image_path(process.get());

    std::cout << "[PROCESS IMAGE] " << to_utf8(image_path) << '\n';
    const std::filesystem::path executable(image_path);
    for (const auto& finding : analyze_process_image_path(executable)) {
        std::cout << "[SUSPICIOUS PROCESS] PID " << entry.th32ProcessID
                  << " | " << finding << '\n';
        if (logger != nullptr) {
            logger->write(
                "warning", "suspicious_process_location", finding, executable);
        }
    }
    ScanOptions options;
    options.logger = logger;
    const auto summary = scan_file(executable, options, signatures);
    if (summary.errors != 0) {
        std::cerr << "[WARN] Image scan for PID " << entry.th32ProcessID
                  << " reported " << summary.errors << " error(s).\n";
    }
}

}

void watch_processes(
    const SignatureDatabase& signatures,
    Logger* logger) {
    stop_requested.store(false);
    if (!SetConsoleCtrlHandler(handle_console_control, TRUE)) {
        throw std::runtime_error(
            "Cannot register process-monitor shutdown handler (Windows error " +
            std::to_string(GetLastError()) + ").");
    }

    std::unordered_set<DWORD> known_processes;
    bool baseline_loaded = false;
    std::cout << "Monitoring process starts. Press Ctrl+C to stop.\n";
    if (logger != nullptr) {
        logger->write(
            "info", "process_monitor_started",
            "Process-start monitoring started.");
    }
    try {
        while (!stop_requested.load()) {
            const auto processes = take_process_snapshot();
            std::unordered_set<DWORD> current_processes;
            current_processes.reserve(processes.size());

            for (const auto& process : processes) {
                current_processes.insert(process.th32ProcessID);
                if (!baseline_loaded ||
                    known_processes.find(process.th32ProcessID) != known_processes.end()) {
                    continue;
                }

                try {
                    report_new_process(process, signatures, logger);
                } catch (const std::exception& error) {
                    std::cerr << "[ERROR] Could not inspect new process PID "
                              << process.th32ProcessID << ": " << error.what() << '\n';
                    if (logger != nullptr) {
                        logger->write(
                            "error", "process_inspection_error", error.what());
                    }
                }
            }

            known_processes.swap(current_processes);
            baseline_loaded = true;
            for (unsigned int elapsed = 0; elapsed < 1000 && !stop_requested.load();
                 elapsed += 50) {
                Sleep(50);
            }
        }
    } catch (...) {
        SetConsoleCtrlHandler(handle_console_control, FALSE);
        throw;
    }

    SetConsoleCtrlHandler(handle_console_control, FALSE);
    std::cout << "Process monitoring stopped.\n";
    if (logger != nullptr) {
        logger->write(
            "info", "process_monitor_stopped",
            "Process-start monitoring stopped.");
    }
}

void print_process_tree(Logger* logger) {
    const auto processes = take_process_snapshot();
    std::unordered_map<DWORD, PROCESSENTRY32W> by_id;
    std::unordered_map<DWORD, std::vector<DWORD>> children;
    std::vector<DWORD> roots;
    by_id.reserve(processes.size());
    for (const auto& process : processes) {
        by_id.emplace(process.th32ProcessID, process);
    }

    for (const auto& process : processes) {
        const DWORD id = process.th32ProcessID;
        const DWORD parent = process.th32ParentProcessID;
        if (parent == id || by_id.find(parent) == by_id.end()) {
            roots.push_back(id);
        } else {
            children[parent].push_back(id);
        }
    }
    const auto by_pid = [](DWORD left, DWORD right) { return left < right; };
    std::sort(roots.begin(), roots.end(), by_pid);
    for (auto& [parent, child_ids] : children) {
        (void)parent;
        std::sort(child_ids.begin(), child_ids.end(), by_pid);
    }

    std::unordered_set<DWORD> visited;
    const auto emit = [&](DWORD root, const std::string& root_prefix) {
        std::function<void(DWORD, std::size_t)> visit =
            [&](DWORD id, std::size_t depth) {
                if (!visited.insert(id).second) {
                    return;
                }
                const auto& process = by_id.at(id);
                const std::string line =
                    root_prefix + std::string(depth * 2, ' ') +
                    (depth == 0 ? "" : "|- ") +
                    to_utf8(process.szExeFile) + " [PID " +
                    std::to_string(id) + ", PPID " +
                    std::to_string(process.th32ParentProcessID) + "]";
                std::cout << "[PROCESS TREE] " << line << '\n';
                if (logger != nullptr) {
                    logger->write("info", "process_tree_entry", line);
                }
                const auto descendants = children.find(id);
                if (descendants != children.end()) {
                    for (const DWORD child : descendants->second) {
                        visit(child, depth + 1);
                    }
                }
            };
        visit(root, 0);
    };

    std::cout << "Point-in-time process tree (metadata only; no enforcement).\n";
    for (const DWORD root : roots) {
        emit(root, {});
    }
    for (const auto& process : processes) {
        if (visited.find(process.th32ProcessID) == visited.end()) {
            emit(process.th32ProcessID, "[orphan/cycle] ");
        }
    }
    if (logger != nullptr) {
        logger->write(
            "info", "process_tree_completed",
            "Captured " + std::to_string(processes.size()) +
                " processes in a point-in-time process tree.");
    }
}

}
