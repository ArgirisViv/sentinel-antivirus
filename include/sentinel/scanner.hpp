#pragma once

#include "sentinel/signatures.hpp"

#include <cstddef>
#include <filesystem>

namespace sentinel {

class Logger;

struct ScanOptions {
    std::filesystem::path target;
    std::filesystem::path quarantine_directory;
    Logger* logger = nullptr;
    bool quarantine_matches = false;
};

struct ScanSummary {
    std::size_t files_scanned = 0;
    std::size_t threats_detected = 0;
    std::size_t suspicious_files = 0;
    std::size_t files_quarantined = 0;
    std::size_t errors = 0;
};

ScanSummary scan_path(
    const ScanOptions& options,
    const SignatureDatabase& signatures);

ScanSummary scan_file(
    const std::filesystem::path& file,
    const ScanOptions& options,
    const SignatureDatabase& signatures);

}
