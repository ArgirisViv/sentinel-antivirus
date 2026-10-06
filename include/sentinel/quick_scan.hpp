#pragma once

#include "sentinel/scanner.hpp"

#include <filesystem>
#include <string>
#include <vector>

namespace sentinel {

struct QuickScanLocation {
    std::string name;
    std::filesystem::path path;
};

struct QuickScanPlan {
    std::vector<QuickScanLocation> locations;
    std::vector<std::string> errors;
};

struct QuickScanSummary {
    ScanSummary scan;
    std::size_t locations_scanned = 0;
    std::size_t locations_skipped = 0;
};

QuickScanPlan discover_quick_scan_locations();

QuickScanSummary scan_quick_locations(
    const std::vector<QuickScanLocation>& locations,
    const ScanOptions& options,
    const SignatureDatabase& signatures);

}
