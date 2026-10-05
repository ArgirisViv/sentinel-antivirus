#include "sentinel/watcher.hpp"

#include "sentinel/logging.hpp"
#include "sentinel/security.hpp"

#include <array>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstddef>
#include <cstring>
#include <filesystem>
#include <iostream>
#include <mutex>
#include <stdexcept>
#include <string>
#include <system_error>
#include <unordered_map>
#include <vector>

#ifdef _WIN32
#include <windows.h>
#else
#error "Real-time directory monitoring currently uses the Windows API."
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

bool same_component(
    const std::filesystem::path& left,
    const std::filesystem::path& right) {
    return _wcsicmp(left.c_str(), right.c_str()) == 0;
}

bool is_within(
    const std::filesystem::path& candidate,
    const std::filesystem::path& parent) {
    auto candidate_part = candidate.begin();
    auto parent_part = parent.begin();
    for (; parent_part != parent.end(); ++parent_part, ++candidate_part) {
        if (candidate_part == candidate.end() ||
            !same_component(*candidate_part, *parent_part)) {
            return false;
        }
    }
    return true;
}

struct WatchQueue {
    std::mutex mutex;
    std::condition_variable changed;
    std::vector<std::filesystem::path> paths;
    std::string error;
    bool finished = false;
};

struct WatchWorkerContext {
    HANDLE directory;
    const std::filesystem::path* root;
    WatchQueue* queue;
};

DWORD WINAPI watch_worker(void* context_pointer) {
    auto& context = *static_cast<WatchWorkerContext*>(context_pointer);
    const auto& root = *context.root;
    auto& queue = *context.queue;
    alignas(DWORD) std::array<std::byte, 64 * 1024> buffer{};
    std::string failure;

    while (!stop_requested.load()) {
        DWORD bytes_returned = 0;
        const BOOL succeeded = ReadDirectoryChangesW(
            context.directory,
            buffer.data(),
            static_cast<DWORD>(buffer.size()),
            TRUE,
            FILE_NOTIFY_CHANGE_FILE_NAME |
                FILE_NOTIFY_CHANGE_LAST_WRITE |
                FILE_NOTIFY_CHANGE_SIZE |
                FILE_NOTIFY_CHANGE_CREATION,
            &bytes_returned,
            nullptr,
            nullptr);
        if (!succeeded) {
            const DWORD error = GetLastError();
            if (stop_requested.load() && error == ERROR_OPERATION_ABORTED) {
                break;
            }
            failure = "ReadDirectoryChangesW failed (Windows error " +
                      std::to_string(error) + ").";
            break;
        }
        if (bytes_returned == 0) {
            failure =
                "Filesystem notification buffer overflowed; run a full scan to "
                "cover changes that may have been missed.";
            break;
        }
        if (bytes_returned < offsetof(FILE_NOTIFY_INFORMATION, FileName)) {
            failure = "Filesystem notification record was truncated.";
            break;
        }

        std::size_t offset = 0;
        while (offset + offsetof(FILE_NOTIFY_INFORMATION, FileName) <= bytes_returned) {
            const std::size_t header_size =
                offsetof(FILE_NOTIFY_INFORMATION, FileName);
            // Copy the fixed header instead of casting: some implementations
            // (for example Wine) do not pad records to DWORD alignment.
            FILE_NOTIFY_INFORMATION record{};
            std::memcpy(&record, buffer.data() + offset, header_size);
            const FILE_NOTIFY_INFORMATION* notification = &record;
            if (notification->FileNameLength % sizeof(wchar_t) != 0 ||
                notification->FileNameLength > bytes_returned - offset - header_size) {
                failure = "Filesystem notification record was malformed.";
                break;
            }

            if (notification->Action == FILE_ACTION_ADDED ||
                notification->Action == FILE_ACTION_MODIFIED ||
                notification->Action == FILE_ACTION_RENAMED_NEW_NAME) {
                std::wstring relative(
                    notification->FileNameLength / sizeof(wchar_t), L'\0');
                std::memcpy(
                    relative.data(), buffer.data() + offset + header_size,
                    notification->FileNameLength);
                const auto path = (root / std::filesystem::path(relative)).lexically_normal();
                if (!is_within(path, root)) {
                    failure = "Filesystem notification escaped the watched directory.";
                    break;
                }
                {
                    std::lock_guard<std::mutex> lock(queue.mutex);
                    queue.paths.push_back(path);
                }
                queue.changed.notify_one();
            }

            if (notification->NextEntryOffset == 0) {
                break;
            }
            if (notification->NextEntryOffset < header_size +
                    notification->FileNameLength ||
                notification->NextEntryOffset > bytes_returned - offset) {
                failure = "Filesystem notification record offset was malformed.";
                break;
            }
            offset += notification->NextEntryOffset;
        }
        if (!failure.empty()) {
            break;
        }
    }

    {
        std::lock_guard<std::mutex> lock(queue.mutex);
        queue.error = failure;
        queue.finished = true;
    }
    queue.changed.notify_one();
    return 0;
}

bool wait_for_stable_file(const std::filesystem::path& file) {
    constexpr auto poll_interval = std::chrono::milliseconds(200);
    constexpr auto timeout = std::chrono::seconds(30);
    constexpr unsigned int required_stable_checks = 3;
    const auto deadline = std::chrono::steady_clock::now() + timeout;

    std::uintmax_t previous_size = 0;
    std::filesystem::file_time_type previous_write_time{};
    unsigned int stable_checks = 0;
    bool have_previous = false;

    while (std::chrono::steady_clock::now() < deadline) {
        std::error_code error;
        const auto status = std::filesystem::symlink_status(file, error);
        if (error == std::errc::no_such_file_or_directory ||
            (!error && !std::filesystem::exists(status))) {
            return false;
        }
        if (error) {
            throw std::runtime_error(
                "Cannot inspect changed file " + file.string() + ": " + error.message());
        }
        if (std::filesystem::is_symlink(status) ||
            !std::filesystem::is_regular_file(status)) {
            return false;
        }

        const auto size = std::filesystem::file_size(file, error);
        if (error) {
            stable_checks = 0;
            have_previous = false;
        } else {
            const auto write_time = std::filesystem::last_write_time(file, error);
            if (error) {
                stable_checks = 0;
                have_previous = false;
            } else if (have_previous &&
                       size == previous_size &&
                       write_time == previous_write_time) {
                ++stable_checks;
                if (stable_checks >= required_stable_checks) {
                    return true;
                }
            } else {
                previous_size = size;
                previous_write_time = write_time;
                stable_checks = 0;
                have_previous = true;
            }
        }
        Sleep(static_cast<DWORD>(poll_interval.count()));
    }
    throw std::runtime_error(
        "File did not become stable within 30 seconds; it was not scanned: " +
        file.string());
}

}

void watch_directory(
    const ScanOptions& options,
    const SignatureDatabase& signatures) {
    std::error_code error;
    const auto status = std::filesystem::symlink_status(options.target, error);
    if (error || !std::filesystem::is_directory(status) ||
        std::filesystem::is_symlink(status)) {
        throw std::runtime_error(
            "Watch target must be an accessible directory, not a symbolic link: " +
            options.target.string());
    }

    const auto root = std::filesystem::weakly_canonical(options.target, error);
    if (error) {
        throw std::runtime_error(
            "Cannot resolve watch directory: " + error.message());
    }

    ScanOptions effective_options = options;
    std::filesystem::path quarantine;
    if (options.quarantine_matches) {
        const bool quarantine_created =
            std::filesystem::create_directories(
                options.quarantine_directory, error);
        if (error) {
            throw std::runtime_error(
                "Cannot create quarantine directory: " + error.message());
        }
        quarantine = std::filesystem::weakly_canonical(
            options.quarantine_directory, error);
        if (error) {
            throw std::runtime_error(
                "Cannot resolve quarantine directory: " + error.message());
        }
        if (is_within(root, quarantine)) {
            throw std::runtime_error(
                "Quarantine directory cannot be the watch target or one of its parents.");
        }
        secure_quarantine_directory(
            quarantine, quarantine_created, options.logger);
        effective_options.quarantine_directory = quarantine;
    }

    HANDLE directory = CreateFileW(
        root.c_str(),
        FILE_LIST_DIRECTORY,
        FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
        nullptr,
        OPEN_EXISTING,
        FILE_FLAG_BACKUP_SEMANTICS,
        nullptr);
    if (directory == INVALID_HANDLE_VALUE) {
        throw std::runtime_error(
            "Cannot open directory for monitoring (Windows error " +
            std::to_string(GetLastError()) + ").");
    }

    stop_requested.store(false);
    if (!SetConsoleCtrlHandler(handle_console_control, TRUE)) {
        CloseHandle(directory);
        throw std::runtime_error(
            "Cannot register console shutdown handler (Windows error " +
            std::to_string(GetLastError()) + ").");
    }

    WatchQueue queue;
    WatchWorkerContext context{directory, &root, &queue};
    HANDLE worker = CreateThread(
        nullptr, 0, watch_worker, &context, 0, nullptr);
    if (worker == nullptr) {
        CloseHandle(directory);
        SetConsoleCtrlHandler(handle_console_control, FALSE);
        throw std::runtime_error(
            "Cannot start filesystem-monitor thread (Windows error " +
            std::to_string(GetLastError()) + ").");
    }
    std::unordered_map<std::filesystem::path, std::chrono::steady_clock::time_point>
        pending;
    std::cout << "Watching recursively: " << root.string()
              << "\nPress Ctrl+C to stop.\n";
    if (options.logger != nullptr) {
        options.logger->write(
            "info", "filesystem_monitor_started",
            "Recursive filesystem monitoring started.", root);
    }

    while (true) {
        std::vector<std::filesystem::path> incoming;
        bool finished = false;
        {
            std::unique_lock<std::mutex> lock(queue.mutex);
            queue.changed.wait_for(lock, std::chrono::milliseconds(200), [&queue] {
                return !queue.paths.empty() || queue.finished ||
                       stop_requested.load();
            });
            incoming.swap(queue.paths);
            finished = queue.finished;
        }

        const auto now = std::chrono::steady_clock::now();
        for (const auto& path : incoming) {
            pending[path] = now;
        }

        for (auto iterator = pending.begin(); iterator != pending.end();) {
            const bool quiet =
                now - iterator->second >= std::chrono::milliseconds(750);
            if (!quiet && !finished && !stop_requested.load()) {
                ++iterator;
                continue;
            }

            const auto file = iterator->first;
            iterator = pending.erase(iterator);
            if (options.quarantine_matches && is_within(file, quarantine)) {
                continue;
            }

            try {
                if (!wait_for_stable_file(file)) {
                    continue;
                }
                const auto summary = sentinel::scan_file(
                    file, effective_options, signatures);
                if (summary.errors != 0) {
                    std::cerr << "[ERROR] Real-time scan reported "
                              << summary.errors << " error(s).\n";
                }
            } catch (const std::exception& scan_error) {
                std::cerr << "[ERROR] " << scan_error.what() << '\n';
            }
        }

        if (finished || stop_requested.load()) {
            break;
        }
    }

    DWORD cancellation_error = ERROR_SUCCESS;
    if (stop_requested.load() && !CancelSynchronousIo(worker)) {
        cancellation_error = GetLastError();
    }
    WaitForSingleObject(worker, INFINITE);
    CloseHandle(worker);
    CloseHandle(directory);
    SetConsoleCtrlHandler(handle_console_control, FALSE);

    std::string worker_error;
    {
        std::lock_guard<std::mutex> lock(queue.mutex);
        worker_error = queue.error;
    }
    if (!worker_error.empty()) {
        if (options.logger != nullptr) {
            options.logger->write(
                "error", "filesystem_monitor_error", worker_error, root);
        }
        throw std::runtime_error(worker_error);
    }
    if (cancellation_error != ERROR_SUCCESS &&
        cancellation_error != ERROR_NOT_FOUND) {
        throw std::runtime_error(
            "Cannot stop filesystem monitoring (Windows error " +
            std::to_string(cancellation_error) + ").");
    }
    if (!stop_requested.load()) {
        throw std::runtime_error("Filesystem monitoring stopped unexpectedly.");
    }
    std::cout << "Filesystem monitoring stopped.\n";
    if (options.logger != nullptr) {
        options.logger->write(
            "info", "filesystem_monitor_stopped",
            "Recursive filesystem monitoring stopped.", root);
    }
}

}
