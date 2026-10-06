#include "sentinel/quick_scan.hpp"

#include "sentinel/logging.hpp"

#include <algorithm>
#include <iostream>
#include <memory>
#include <stdexcept>
#include <system_error>
#include <utility>

#ifdef _WIN32
#include <windows.h>
#include <shlobj.h>
#else
#error "Quick scan location discovery currently uses Windows known folders."
#endif

namespace sentinel {
namespace {

struct CoTaskMemDeleter {
    void operator()(wchar_t* value) const noexcept {
        CoTaskMemFree(value);
    }
};

void add_known_folder(
    QuickScanPlan& plan,
    const KNOWNFOLDERID& id,
    const char* name) {
    PWSTR raw_path = nullptr;
    const HRESULT result =
        SHGetKnownFolderPath(id, KF_FLAG_DEFAULT, nullptr, &raw_path);
    std::unique_ptr<wchar_t, CoTaskMemDeleter> path(raw_path);
    if (FAILED(result) || path == nullptr) {
        plan.errors.emplace_back(
            std::string("Cannot resolve current user's ") + name +
            " known folder (HRESULT " +
            std::to_string(static_cast<unsigned long>(result)) + ").");
        return;
    }
    plan.locations.push_back(
        QuickScanLocation{name, std::filesystem::path(path.get())});
}

bool same_component(
    const std::filesystem::path& left,
    const std::filesystem::path& right) {
#ifdef _WIN32
    return _wcsicmp(left.c_str(), right.c_str()) == 0;
#else
    return left == right;
#endif
}

bool is_same_or_child(
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

void add_summary(ScanSummary& destination, const ScanSummary& source) {
    destination.files_scanned += source.files_scanned;
    destination.threats_detected += source.threats_detected;
    destination.suspicious_files += source.suspicious_files;
    destination.files_quarantined += source.files_quarantined;
    destination.errors += source.errors;
    if (source.highest_risk_score > destination.highest_risk_score) {
        destination.highest_risk_score = source.highest_risk_score;
        destination.highest_risk_severity = source.highest_risk_severity;
    }
}

}

QuickScanPlan discover_quick_scan_locations() {
    QuickScanPlan plan;
    add_known_folder(plan, FOLDERID_Downloads, "Downloads");
    std::error_code error;
    const auto temporary = std::filesystem::temp_directory_path(error);
    if (error) {
        plan.errors.emplace_back(
            "Cannot resolve the current user's temporary directory: " +
            error.message());
    } else {
        plan.locations.push_back(QuickScanLocation{"Temp", temporary});
    }
    add_known_folder(plan, FOLDERID_Startup, "Startup");
    return plan;
}

QuickScanSummary scan_quick_locations(
    const std::vector<QuickScanLocation>& locations,
    const ScanOptions& options,
    const SignatureDatabase& signatures) {
    QuickScanSummary summary;
    std::vector<std::filesystem::path> scanned_roots;

    for (const auto& location : locations) {
        std::error_code error;
        const auto status = std::filesystem::symlink_status(location.path, error);
        if (error == std::errc::no_such_file_or_directory ||
            (!error && !std::filesystem::exists(status))) {
            ++summary.locations_skipped;
            std::cout << "[QUICK SCAN SKIPPED] " << location.name
                      << " | location does not exist: " << location.path.string()
                      << '\n';
            continue;
        }
        if (error) {
            ++summary.scan.errors;
            const std::string message =
                "Cannot inspect " + location.name + " quick-scan location: " +
                error.message();
            std::cerr << "[QUICK SCAN ERROR] " << message << '\n';
            if (options.logger != nullptr) {
                options.logger->write(
                    "error", "quick_scan_location_error", message, location.path);
            }
            continue;
        }
        if (std::filesystem::is_symlink(status) ||
            !std::filesystem::is_directory(status)) {
            ++summary.scan.errors;
            const std::string message =
                "Quick-scan location is not a real directory: " +
                location.path.string();
            std::cerr << "[QUICK SCAN ERROR] " << message << '\n';
            if (options.logger != nullptr) {
                options.logger->write(
                    "error", "quick_scan_location_error", message, location.path);
            }
            continue;
        }

        const auto root = std::filesystem::weakly_canonical(location.path, error);
        if (error) {
            ++summary.scan.errors;
            const std::string message =
                "Cannot resolve " + location.name + " quick-scan location: " +
                error.message();
            std::cerr << "[QUICK SCAN ERROR] " << message << '\n';
            if (options.logger != nullptr) {
                options.logger->write(
                    "error", "quick_scan_location_error", message, location.path);
            }
            continue;
        }
        const auto duplicate = std::find_if(
            scanned_roots.begin(), scanned_roots.end(),
            [&root](const std::filesystem::path& scanned) {
                return is_same_or_child(root, scanned) &&
                       is_same_or_child(scanned, root);
            });
        if (duplicate != scanned_roots.end()) {
            ++summary.locations_skipped;
            std::cout << "[QUICK SCAN SKIPPED] " << location.name
                      << " | duplicates an already scanned location: "
                      << location.path.string() << '\n';
            continue;
        }

        std::cout << "[QUICK SCAN LOCATION] " << location.name
                  << " | " << root.string() << '\n';
        if (options.logger != nullptr) {
            options.logger->write(
                "info", "quick_scan_location_started",
                "Scanning selected quick-scan location: " + location.name, root);
        }
        try {
            const auto location_summary =
                scan_path(ScanOptions{
                             root,
                             options.quarantine_directory,
                             options.logger,
                             options.quarantine_matches},
                         signatures);
            add_summary(summary.scan, location_summary);
            scanned_roots.push_back(root);
            ++summary.locations_scanned;
        } catch (const std::exception& scan_error) {
            ++summary.scan.errors;
            const std::string message =
                "Could not scan " + location.name + ": " + scan_error.what();
            std::cerr << "[QUICK SCAN ERROR] " << message << '\n';
            if (options.logger != nullptr) {
                options.logger->write(
                    "error", "quick_scan_location_error", message, root);
            }
        }
    }

    return summary;
}

}
