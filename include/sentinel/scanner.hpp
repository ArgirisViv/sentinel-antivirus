#pragma once

#include "sentinel/signatures.hpp"

#include <cstddef>
#include <filesystem>
#include <string>
#include <vector>

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
    unsigned int highest_risk_score = 0;
    std::string highest_risk_severity = "NONE";
};

struct QuarantineEntry {
    std::string id;
    std::string sha256;
    std::string threat;
    std::string timestamp;
    std::filesystem::path original_path;
    std::filesystem::path quarantined_path;
};

ScanSummary scan_path(
    const ScanOptions& options,
    const SignatureDatabase& signatures);

ScanSummary scan_file(
    const std::filesystem::path& file,
    const ScanOptions& options,
    const SignatureDatabase& signatures);

std::vector<QuarantineEntry> list_quarantine(
    const std::filesystem::path& directory);

QuarantineEntry restore_quarantined_file(
    const std::filesystem::path& directory,
    const std::string& id,
    Logger* logger = nullptr);

std::string format_quarantine_entry(const QuarantineEntry& entry);

}
