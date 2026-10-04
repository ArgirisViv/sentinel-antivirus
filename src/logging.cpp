#include "sentinel/logging.hpp"

#include "sentinel/security.hpp"

#include <chrono>
#include <ctime>
#include <iomanip>
#include <sstream>
#include <stdexcept>
#include <system_error>

#ifdef _WIN32
#include <windows.h>
#endif

namespace sentinel {
namespace {

constexpr std::uintmax_t maximum_log_size = 10 * 1024 * 1024;

std::string escape_json(const std::string& value) {
    std::ostringstream escaped;
    for (const unsigned char character : value) {
        switch (character) {
        case '"':
            escaped << "\\\"";
            break;
        case '\\':
            escaped << "\\\\";
            break;
        case '\b':
            escaped << "\\b";
            break;
        case '\f':
            escaped << "\\f";
            break;
        case '\n':
            escaped << "\\n";
            break;
        case '\r':
            escaped << "\\r";
            break;
        case '\t':
            escaped << "\\t";
            break;
        default:
            if (character < 0x20) {
                escaped << "\\u"
                        << std::hex << std::setw(4) << std::setfill('0')
                        << static_cast<unsigned int>(character)
                        << std::dec << std::setfill(' ');
            } else {
                escaped << static_cast<char>(character);
            }
            break;
        }
    }
    return escaped.str();
}

std::string timestamp_utc() {
    const auto now = std::chrono::system_clock::now();
    const auto milliseconds =
        std::chrono::duration_cast<std::chrono::milliseconds>(
            now.time_since_epoch()) %
        1000;
    const std::time_t time = std::chrono::system_clock::to_time_t(now);
    std::tm utc{};
#ifdef _WIN32
    if (gmtime_s(&utc, &time) != 0) {
        throw std::runtime_error("Cannot format UTC event timestamp.");
    }
#else
    if (gmtime_r(&time, &utc) == nullptr) {
        throw std::runtime_error("Cannot format UTC event timestamp.");
    }
#endif
    std::ostringstream formatted;
    formatted << std::put_time(&utc, "%Y-%m-%dT%H:%M:%S")
              << '.' << std::setw(3) << std::setfill('0')
              << milliseconds.count() << 'Z';
    return formatted.str();
}

}

Logger::Logger(const std::filesystem::path& file) : file_(file) {
    if (file_.empty()) {
        throw std::invalid_argument("Log file path must not be empty.");
    }
    const auto parent = file_.parent_path();
    if (!parent.empty()) {
        std::error_code error;
        std::filesystem::create_directories(parent, error);
        if (error) {
            throw std::runtime_error(
                "Cannot create log directory " + parent.string() + ": " +
                error.message());
        }
    }
    output_.open(file_, std::ios::out | std::ios::app);
    if (!output_) {
        throw std::runtime_error("Cannot open event log: " + file_.string());
    }
    secure_event_log_file(file_);
}

void Logger::write(
    const std::string& severity,
    const std::string& event,
    const std::string& message,
    const std::filesystem::path& path) {
    const std::string serialized =
        "{\"timestamp\":\"" + escape_json(timestamp_utc()) +
        "\",\"severity\":\"" + escape_json(severity) +
        "\",\"event\":\"" + escape_json(event) +
        "\",\"message\":\"" + escape_json(message) +
        "\",\"path\":\"" + escape_json(path.string()) + "\"}\n";

    std::lock_guard<std::mutex> lock(mutex_);
    output_.flush();
    if (!output_) {
        throw std::runtime_error("Cannot flush event log: " + file_.string());
    }
    std::error_code size_error;
    const auto current_size = std::filesystem::file_size(file_, size_error);
    if (!size_error && current_size + serialized.size() > maximum_log_size) {
        output_.close();
        auto backup = file_;
        backup += ".1";
        std::error_code error;
        std::filesystem::remove(backup, error);
        if (error) {
            throw std::runtime_error(
                "Cannot remove rotated event log " + backup.string() + ": " +
                error.message());
        }
        std::filesystem::rename(file_, backup, error);
        if (error) {
            throw std::runtime_error(
                "Cannot rotate event log " + file_.string() + ": " +
                error.message());
        }
        output_.open(file_, std::ios::out | std::ios::app);
        if (!output_) {
            throw std::runtime_error(
                "Cannot reopen event log after rotation: " + file_.string());
        }
        secure_event_log_file(file_);
    } else if (size_error) {
        throw std::runtime_error(
            "Cannot inspect event log size " + file_.string() + ": " +
            size_error.message());
    }
    output_ << serialized;
    output_.flush();
    if (!output_) {
        throw std::runtime_error("Cannot write event log: " + file_.string());
    }
}

}
