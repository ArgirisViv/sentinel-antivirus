#include "sentinel/behavior.hpp"

namespace sentinel {

bool FileChangeBurstDetector::observe(
    const std::filesystem::path& path,
    Clock::time_point now) {
    const std::string key = path.lexically_normal().u8string();
    last_change_[key] = now;

    for (auto iterator = last_change_.begin(); iterator != last_change_.end();) {
        if (now - iterator->second > observation_window) {
            iterator = last_change_.erase(iterator);
        } else {
            ++iterator;
        }
    }

    if (last_change_.size() < minimum_distinct_files) {
        alert_active_ = false;
        return false;
    }
    if (alert_active_) {
        return false;
    }
    alert_active_ = true;
    return true;
}

}
