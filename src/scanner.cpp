#include "sentinel/scanner.hpp"

#include "sentinel/analysis.hpp"
#include "sentinel/hash.hpp"
#include "sentinel/logging.hpp"
#include "sentinel/security.hpp"

#include <filesystem>
#include <iostream>
#include <stdexcept>
#include <string>
#include <system_error>

namespace sentinel {
namespace {

std::filesystem::path normalized_path(const std::filesystem::path& path) {
    std::error_code error;
    const auto result = std::filesystem::weakly_canonical(path, error);
    if (error) {
        throw std::runtime_error(
            "Cannot resolve path " + path.string() + ": " + error.message());
    }
    return result;
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

bool is_within(
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

std::filesystem::path quarantine_destination(
    const std::filesystem::path& source,
    const std::filesystem::path& quarantine,
    const std::string& hash) {
    std::string filename = hash + "_" + source.filename().string();
    auto destination = quarantine / filename;
    for (std::size_t suffix = 1; std::filesystem::exists(destination); ++suffix) {
        destination = quarantine /
            (hash + "_" + std::to_string(suffix) + "_" +
             source.filename().string());
    }
    return destination;
}

void scan_file_impl(
    const std::filesystem::path& file,
    const ScanOptions& options,
    const SignatureDatabase& signatures,
    ScanSummary& summary) {
    try {
        const auto analysis = analyze_file(file);
        if (!analysis.findings.empty()) {
            ++summary.suspicious_files;
            for (const auto& finding : analysis.findings) {
                std::cout << "[SUSPICIOUS] " << file.string()
                          << " | " << finding << '\n';
                if (options.logger != nullptr) {
                    options.logger->write(
                        "warning", "static_indicator", finding, file);
                }
            }
        }
        if (analysis.valid_pe) {
            std::cout << "[PE] " << file.string()
                      << " | " << analysis.pe_architecture
                      << " | " << analysis.pe_section_count << " section(s)\n";
            if (options.logger != nullptr) {
                options.logger->write(
                    "info",
                    "pe_metadata",
                    analysis.pe_architecture + ", " +
                        std::to_string(analysis.pe_section_count) + " section(s)",
                    file);
            }
        }

        const std::string hash = sha256_file(file);
        ++summary.files_scanned;
        const auto match = signatures.find(hash);
        if (match == signatures.end()) {
            return;
        }

        ++summary.threats_detected;
        std::cout << "[DETECTED] " << file.string()
                  << " | " << match->second << " | SHA-256 " << hash << '\n';
        if (options.logger != nullptr) {
            options.logger->write(
                "alert", "signature_detection",
                match->second + " | SHA-256 " + hash, file);
        }

        if (options.quarantine_matches) {
            const auto destination = quarantine_destination(
                file, options.quarantine_directory, hash);
            std::error_code error;
            std::filesystem::rename(file, destination, error);
            if (error) {
                throw std::runtime_error(
                    "Cannot move detected file to quarantine (use a directory on "
                    "the same volume): " + error.message());
            }
            try {
                secure_quarantined_file(destination, options.logger);
            } catch (const std::exception& security_error) {
                std::error_code rollback_error;
                std::filesystem::rename(destination, file, rollback_error);
                if (rollback_error) {
                    throw std::runtime_error(
                        "Quarantine ACL hardening failed: " +
                        std::string(security_error.what()) +
                        "; the source file could not be restored and remains at " +
                        destination.string() + " (" + rollback_error.message() + ").");
                }
                throw std::runtime_error(
                    "Quarantine ACL hardening failed; source file was restored: " +
                    std::string(security_error.what()));
            }
            ++summary.files_quarantined;
            std::cout << "[QUARANTINED] " << destination.string() << '\n';
            if (options.logger != nullptr) {
                options.logger->write(
                    "warning", "file_quarantined",
                    "Moved matching file into quarantine.", destination);
            }
        }
    } catch (const std::exception& error) {
        ++summary.errors;
        std::cerr << "[ERROR] " << error.what() << '\n';
        if (options.logger != nullptr) {
            options.logger->write("error", "file_scan_error", error.what(), file);
        }
    }
}

}

ScanSummary scan_path(
    const ScanOptions& options,
    const SignatureDatabase& signatures) {
    std::error_code error;
    const auto target_status = std::filesystem::symlink_status(options.target, error);
    if (error || !std::filesystem::exists(target_status)) {
        throw std::runtime_error(
            "Scan target does not exist or cannot be accessed: " + options.target.string());
    }
    if (std::filesystem::is_symlink(target_status)) {
        throw std::runtime_error("Refusing to scan a symbolic link: " + options.target.string());
    }

    const auto target = normalized_path(options.target);
    std::filesystem::path quarantine;
    if (options.quarantine_matches) {
        const bool quarantine_created =
            std::filesystem::create_directories(
                options.quarantine_directory, error);
        if (error) {
            throw std::runtime_error(
                "Cannot create quarantine directory: " + error.message());
        }
        quarantine = normalized_path(options.quarantine_directory);
        if (is_within(target, quarantine)) {
            throw std::runtime_error(
                "Quarantine directory cannot be the scan target or one of its parents.");
        }
        secure_quarantine_directory(
            quarantine, quarantine_created, options.logger);
    }

    ScanOptions effective_options = options;
    if (options.quarantine_matches) {
        effective_options.quarantine_directory = quarantine;
    }

    ScanSummary summary;
    if (options.logger != nullptr) {
        options.logger->write(
            "info", "scan_started", "File scan started.", target);
    }
    if (std::filesystem::is_regular_file(target_status)) {
        scan_file_impl(target, effective_options, signatures, summary);
        if (options.logger != nullptr) {
            options.logger->write(
                "info", "scan_completed",
                "Scanned " + std::to_string(summary.files_scanned) +
                    " file(s); " + std::to_string(summary.threats_detected) +
                    " signature detection(s); " +
                    std::to_string(summary.suspicious_files) +
                    " suspicious file(s).",
                target);
        }
        return summary;
    }
    if (!std::filesystem::is_directory(target_status)) {
        throw std::runtime_error("Scan target is not a regular file or directory.");
    }

    std::filesystem::recursive_directory_iterator iterator(
        target,
        std::filesystem::directory_options::none,
        error);
    const std::filesystem::recursive_directory_iterator end;
    if (error) {
        throw std::runtime_error("Cannot enumerate scan target: " + error.message());
    }

    while (iterator != end) {
        const auto entry_path = iterator->path();
        const auto status = iterator->symlink_status(error);
        if (error) {
            ++summary.errors;
            std::cerr << "[ERROR] Cannot inspect " << entry_path.string()
                      << ": " << error.message() << '\n';
            error.clear();
            iterator.increment(error);
            continue;
        }

        if (options.quarantine_matches &&
            std::filesystem::is_directory(status) &&
            is_within(entry_path.lexically_normal(), quarantine)) {
            iterator.disable_recursion_pending();
        } else if (std::filesystem::is_symlink(status)) {
            iterator.disable_recursion_pending();
        } else if (std::filesystem::is_regular_file(status)) {
            scan_file_impl(entry_path, effective_options, signatures, summary);
        }

        iterator.increment(error);
        if (error) {
            ++summary.errors;
            std::cerr << "[ERROR] Directory traversal failed: "
                      << error.message() << '\n';
            error.clear();
        }
    }
    if (options.logger != nullptr) {
        options.logger->write(
            "info", "scan_completed",
            "Scanned " + std::to_string(summary.files_scanned) +
                " file(s); " + std::to_string(summary.threats_detected) +
                " signature detection(s); " +
                std::to_string(summary.suspicious_files) +
                " suspicious file(s).",
            target);
    }
    return summary;
}

ScanSummary scan_file(
    const std::filesystem::path& file,
    const ScanOptions& options,
    const SignatureDatabase& signatures) {
    std::error_code error;
    const auto status = std::filesystem::symlink_status(file, error);
    if (error) {
        throw std::runtime_error(
            "Cannot inspect file " + file.string() + ": " + error.message());
    }
    if (std::filesystem::is_symlink(status)) {
        throw std::runtime_error("Refusing to scan a symbolic link: " + file.string());
    }
    if (!std::filesystem::is_regular_file(status)) {
        throw std::runtime_error("Scan target is not a regular file: " + file.string());
    }

    ScanOptions effective_options = options;
    if (options.quarantine_matches) {
        effective_options.quarantine_directory = normalized_path(options.quarantine_directory);
    }
    ScanSummary summary;
    scan_file_impl(normalized_path(file), effective_options, signatures, summary);
    return summary;
}

}
