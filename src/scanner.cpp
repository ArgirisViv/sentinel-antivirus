#include "sentinel/scanner.hpp"

#include "sentinel/analysis.hpp"
#include "sentinel/hash.hpp"
#include "sentinel/logging.hpp"
#include "sentinel/security.hpp"

#include <algorithm>
#include <chrono>
#include <ctime>
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <sstream>
#include <stdexcept>
#include <string>
#include <system_error>
#include <vector>

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
    const std::filesystem::path& quarantine,
    const std::string& hash) {
    for (std::size_t suffix = 0;; ++suffix) {
        const auto filename = hash + "_" + std::to_string(suffix) + ".qtn";
        const auto destination = quarantine / filename;
        auto metadata = destination;
        metadata += ".sentinel-meta";
        if (!std::filesystem::exists(destination) &&
            !std::filesystem::exists(metadata)) {
            return destination;
        }
    }
}

std::string encode_field(const std::string& value) {
    std::ostringstream encoded;
    encoded << std::uppercase << std::hex;
    for (const unsigned char character : value) {
        const bool safe =
            (character >= 'a' && character <= 'z') ||
            (character >= 'A' && character <= 'Z') ||
            (character >= '0' && character <= '9') ||
            character == '-' || character == '_' || character == '.';
        if (safe) {
            encoded << static_cast<char>(character);
        } else {
            encoded << '%' << std::setw(2) << std::setfill('0')
                    << static_cast<unsigned int>(character);
        }
    }
    return encoded.str();
}

int hex_value(char character) {
    if (character >= '0' && character <= '9') {
        return character - '0';
    }
    if (character >= 'a' && character <= 'f') {
        return character - 'a' + 10;
    }
    if (character >= 'A' && character <= 'F') {
        return character - 'A' + 10;
    }
    return -1;
}

std::string decode_field(const std::string& value) {
    std::string decoded;
    decoded.reserve(value.size());
    for (std::size_t index = 0; index < value.size(); ++index) {
        if (value[index] != '%') {
            decoded.push_back(value[index]);
            continue;
        }
        if (index + 2 >= value.size()) {
            throw std::runtime_error("Quarantine metadata contains invalid escaping.");
        }
        const int high = hex_value(value[index + 1]);
        const int low = hex_value(value[index + 2]);
        if (high < 0 || low < 0) {
            throw std::runtime_error("Quarantine metadata contains invalid escaping.");
        }
        decoded.push_back(static_cast<char>((high << 4) | low));
        index += 2;
    }
    return decoded;
}

std::string utc_timestamp() {
    const auto now = std::chrono::system_clock::now();
    const std::time_t value = std::chrono::system_clock::to_time_t(now);
    std::tm utc{};
#ifdef _WIN32
    if (gmtime_s(&utc, &value) != 0) {
        throw std::runtime_error("Cannot format quarantine timestamp.");
    }
#else
    if (gmtime_r(&value, &utc) == nullptr) {
        throw std::runtime_error("Cannot format quarantine timestamp.");
    }
#endif
    std::ostringstream formatted;
    formatted << std::put_time(&utc, "%Y-%m-%dT%H:%M:%SZ");
    return formatted.str();
}

bool is_sha256(const std::string& value) {
    if (value.size() != 64) {
        return false;
    }
    return std::all_of(value.begin(), value.end(), [](unsigned char character) {
        return (character >= '0' && character <= '9') ||
               (character >= 'a' && character <= 'f') ||
               (character >= 'A' && character <= 'F');
    });
}

void write_quarantine_metadata(
    const std::filesystem::path& directory,
    const std::filesystem::path& destination,
    const std::filesystem::path& original,
    const std::string& hash,
    const std::string& threat,
    Logger* logger) {
    const std::string id = destination.filename().u8string();
    auto metadata = directory / (id + ".sentinel-meta");
    auto temporary = metadata;
    temporary += ".tmp";
    if (std::filesystem::exists(metadata) || std::filesystem::exists(temporary)) {
        throw std::runtime_error("Quarantine metadata destination already exists.");
    }

    {
        std::ofstream output(temporary, std::ios::binary | std::ios::out);
        if (!output) {
            throw std::runtime_error(
                "Cannot create quarantine metadata: " + temporary.string());
        }
        output << "1\t" << encode_field(id) << '\t' << hash << '\t'
               << encode_field(threat) << '\t'
               << encode_field(utc_timestamp()) << '\t'
               << encode_field(original.u8string()) << '\n';
        output.flush();
        if (!output) {
            throw std::runtime_error(
                "Cannot persist quarantine metadata: " + temporary.string());
        }
    }

    secure_quarantined_file(temporary, logger);
    std::error_code error;
    std::filesystem::rename(temporary, metadata, error);
    if (error) {
        std::filesystem::remove(temporary);
        throw std::runtime_error(
            "Cannot finalize quarantine metadata: " + error.message());
    }
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
        const auto risk = assess_file_risk(
            analysis, match != signatures.end());
        if (risk.score > summary.highest_risk_score) {
            summary.highest_risk_score = risk.score;
            summary.highest_risk_severity = risk.severity;
        }
        if (risk.score != 0) {
            std::ostringstream reasons;
            for (std::size_t index = 0; index < risk.reasons.size(); ++index) {
                if (index != 0) {
                    reasons << "; ";
                }
                reasons << risk.reasons[index];
            }
            std::cout << "[RISK] " << file.string()
                      << " | score=" << risk.score
                      << " | severity=" << risk.severity
                      << " | category=" << risk.category
                      << " | confidence=" << risk.confidence
                      << " | reasons=" << reasons.str() << '\n';
            if (options.logger != nullptr) {
                options.logger->write(
                    risk.severity == "SIGNATURE_MATCH" ? "alert" : "warning",
                    "file_risk_assessment",
                    "score=" + std::to_string(risk.score) +
                        "; severity=" + risk.severity +
                        "; category=" + risk.category +
                        "; confidence=" + risk.confidence +
                        "; reasons=" + reasons.str(),
                    file);
            }
        }
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
                options.quarantine_directory, hash);
            std::error_code error;
            std::filesystem::rename(file, destination, error);
            if (error) {
                throw std::runtime_error(
                    "Cannot move detected file to quarantine (use a directory on "
                    "the same volume): " + error.message());
            }
            try {
                secure_quarantined_file(destination, options.logger);
                write_quarantine_metadata(
                    options.quarantine_directory,
                    destination,
                    file,
                    hash,
                    match->second,
                    options.logger);
            } catch (const std::exception& security_error) {
                auto metadata = destination;
                metadata += ".sentinel-meta";
                auto temporary_metadata = metadata;
                temporary_metadata += ".tmp";
                std::error_code cleanup_error;
                std::filesystem::remove(metadata, cleanup_error);
                cleanup_error.clear();
                std::filesystem::remove(temporary_metadata, cleanup_error);
                std::error_code rollback_error;
                std::filesystem::rename(destination, file, rollback_error);
                if (rollback_error) {
                    throw std::runtime_error(
                        "Quarantine finalization failed: " +
                        std::string(security_error.what()) +
                        "; the source file could not be restored and remains at " +
                        destination.string() + " (" + rollback_error.message() + ").");
                }
                throw std::runtime_error(
                    "Quarantine finalization failed; source file was restored: " +
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

std::vector<QuarantineEntry> list_quarantine(
    const std::filesystem::path& directory) {
    std::error_code error;
    const auto initial_status = std::filesystem::symlink_status(directory, error);
    if (error == std::errc::no_such_file_or_directory ||
        (!error && !std::filesystem::exists(initial_status))) {
        return {};
    }
    if (error || std::filesystem::is_symlink(initial_status) ||
        !std::filesystem::is_directory(initial_status)) {
        throw std::runtime_error(
            "Quarantine location is not an accessible directory: " +
            directory.string());
    }
    const auto root = normalized_path(directory);
    if (!std::filesystem::is_directory(root, error) || error) {
        throw std::runtime_error(
            "Quarantine location is not an accessible directory: " +
            directory.string());
    }

    std::vector<QuarantineEntry> entries;
    for (std::filesystem::directory_iterator iterator(root, error), end;
         iterator != end;
         iterator.increment(error)) {
        if (error) {
            throw std::runtime_error(
                "Cannot enumerate quarantine directory: " + error.message());
        }
        const auto metadata_path = iterator->path();
        if (metadata_path.extension() != ".sentinel-meta") {
            continue;
        }
        const auto metadata_status = iterator->symlink_status(error);
        if (error || !std::filesystem::is_regular_file(metadata_status)) {
            throw std::runtime_error(
                "Quarantine metadata is not a regular file: " +
                metadata_path.string());
        }
        const auto metadata_size = std::filesystem::file_size(metadata_path, error);
        if (error || metadata_size > 256 * 1024) {
            throw std::runtime_error(
                "Quarantine metadata is unreadable or exceeds the size limit: " +
                metadata_path.string());
        }

        std::ifstream input(metadata_path, std::ios::binary);
        std::string line;
        std::string extra;
        if (!input || !std::getline(input, line) || std::getline(input, extra)) {
            throw std::runtime_error(
                "Cannot read quarantine metadata: " + metadata_path.string());
        }
        std::vector<std::string> fields;
        std::size_t start = 0;
        while (true) {
            const auto separator = line.find('\t', start);
            fields.push_back(line.substr(
                start,
                separator == std::string::npos ? separator : separator - start));
            if (separator == std::string::npos) {
                break;
            }
            start = separator + 1;
        }
        if (fields.size() != 6 || fields[0] != "1") {
            throw std::runtime_error(
                "Unsupported or malformed quarantine metadata: " +
                metadata_path.string());
        }

        QuarantineEntry entry;
        entry.id = decode_field(fields[1]);
        entry.sha256 = fields[2];
        entry.threat = decode_field(fields[3]);
        entry.timestamp = decode_field(fields[4]);
        entry.original_path = std::filesystem::u8path(decode_field(fields[5]));
        const std::filesystem::path id_path(entry.id);
        const std::string id_prefix = entry.sha256 + "_";
        const std::string id_suffix = ".qtn";
        const bool valid_id =
            entry.id.size() > id_prefix.size() + id_suffix.size() &&
            entry.id.compare(0, id_prefix.size(), id_prefix) == 0 &&
            entry.id.compare(
                entry.id.size() - id_suffix.size(),
                id_suffix.size(),
                id_suffix) == 0 &&
            std::all_of(
                entry.id.begin() + static_cast<std::ptrdiff_t>(id_prefix.size()),
                entry.id.end() - static_cast<std::ptrdiff_t>(id_suffix.size()),
                [](unsigned char character) {
                    return character >= '0' && character <= '9';
                });
        if (entry.id.empty() || id_path.has_parent_path() ||
            id_path.filename().u8string() != entry.id ||
            !is_sha256(entry.sha256) || !valid_id ||
            metadata_path.filename().u8string() !=
                entry.id + ".sentinel-meta") {
            throw std::runtime_error(
                "Quarantine metadata failed validation: " +
                metadata_path.string());
        }
        entry.quarantined_path = root / id_path;
        const auto quarantined_status =
            std::filesystem::symlink_status(entry.quarantined_path, error);
        if (error == std::errc::no_such_file_or_directory ||
            (!error && !std::filesystem::exists(quarantined_status))) {
            error.clear();
            continue;
        }
        if (error || std::filesystem::is_symlink(quarantined_status) ||
            !std::filesystem::is_regular_file(quarantined_status)) {
            throw std::runtime_error(
                "Quarantined item is missing or is not a regular file: " +
                entry.quarantined_path.string());
        }
        const auto resolved_item = normalized_path(entry.quarantined_path);
        if (!is_within(resolved_item, root)) {
            throw std::runtime_error("Quarantine entry escaped its storage directory.");
        }
        entries.push_back(std::move(entry));
    }
    if (error) {
        throw std::runtime_error(
            "Cannot enumerate quarantine directory: " + error.message());
    }
    std::sort(entries.begin(), entries.end(), [](const auto& left, const auto& right) {
        return left.timestamp > right.timestamp;
    });
    return entries;
}

QuarantineEntry restore_quarantined_file(
    const std::filesystem::path& directory,
    const std::string& id,
    Logger* logger) {
    const auto entries = list_quarantine(directory);
    const auto match = std::find_if(
        entries.begin(), entries.end(), [&id](const QuarantineEntry& entry) {
            return entry.id == id;
        });
    if (match == entries.end()) {
        throw std::runtime_error("Quarantine entry not found: " + id);
    }
    if (sha256_file(match->quarantined_path) != match->sha256) {
        throw std::runtime_error(
            "Quarantined file hash does not match its metadata; refusing to restore.");
    }
    if (!match->original_path.is_absolute()) {
        throw std::runtime_error(
            "Refusing to restore to a non-absolute original path.");
    }
    const auto parent = match->original_path.parent_path();
    std::error_code error;
    if (!std::filesystem::is_directory(parent, error) || error) {
        throw std::runtime_error(
            "Original parent directory is unavailable; restore manually: " +
            parent.string());
    }
    const auto original_status =
        std::filesystem::symlink_status(match->original_path, error);
    if (error != std::errc::no_such_file_or_directory &&
        (error || std::filesystem::exists(original_status))) {
        throw std::runtime_error(
            "Refusing to overwrite an existing restore destination: " +
            match->original_path.string());
    }
    error.clear();
    std::filesystem::rename(match->quarantined_path, match->original_path, error);
    if (error) {
        throw std::runtime_error(
            "Cannot restore quarantined file: " + error.message());
    }
    auto metadata = match->quarantined_path;
    metadata += ".sentinel-meta";
    std::filesystem::remove(metadata, error);
    if (error) {
        throw std::runtime_error(
            "File was restored, but quarantine metadata cleanup failed: " +
            error.message());
    }
    if (logger != nullptr) {
        logger->write(
            "info", "file_restored",
            "Restored quarantine entry " + match->id, match->original_path);
    }
    return *match;
}

std::string format_quarantine_entry(const QuarantineEntry& entry) {
    return "QUARANTINE_ENTRY\t" + encode_field(entry.id) + "\t" +
           encode_field(entry.sha256) + "\t" + encode_field(entry.threat) +
           "\t" + encode_field(entry.timestamp) + "\t" +
           encode_field(entry.original_path.u8string());
}

}
