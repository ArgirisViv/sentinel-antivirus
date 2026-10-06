#pragma once

#include <chrono>
#include <cstddef>
#include <filesystem>
#include <string>
#include <unordered_map>

namespace sentinel {

class FileChangeBurstDetector {
public:
    using Clock = std::chrono::steady_clock;

    static constexpr std::size_t minimum_distinct_files = 32;
    static constexpr auto observation_window = std::chrono::seconds(10);

    bool observe(
        const std::filesystem::path& path,
        Clock::time_point now = Clock::now());

    std::size_t recent_distinct_files() const noexcept {
        return last_change_.size();
    }

private:
    std::unordered_map<std::string, Clock::time_point> last_change_;
    bool alert_active_ = false;
};

}
