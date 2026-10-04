#pragma once

#include <filesystem>
#include <fstream>
#include <mutex>
#include <string>

namespace sentinel {

class Logger {
public:
    explicit Logger(const std::filesystem::path& file);

    Logger(const Logger&) = delete;
    Logger& operator=(const Logger&) = delete;

    void write(
        const std::string& severity,
        const std::string& event,
        const std::string& message,
        const std::filesystem::path& path = {});

private:
    std::filesystem::path file_;
    std::ofstream output_;
    std::mutex mutex_;
};

}
