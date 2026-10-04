#pragma once

#include <filesystem>

namespace sentinel {

class Logger;

void secure_quarantine_directory(
    const std::filesystem::path& directory,
    bool created,
    Logger* logger);

void secure_quarantined_file(
    const std::filesystem::path& file,
    Logger* logger);

void secure_event_log_file(const std::filesystem::path& file);

}
