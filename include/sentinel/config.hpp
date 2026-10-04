#pragma once

#include <filesystem>

namespace sentinel {

struct EngineConfig {
    std::filesystem::path signatures_path = "signatures.txt";
    std::filesystem::path log_path;
    std::filesystem::path quarantine_directory = "quarantine";
};

EngineConfig load_engine_config(const std::filesystem::path& file);

}
