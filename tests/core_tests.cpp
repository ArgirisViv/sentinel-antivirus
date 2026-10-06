#include "sentinel/analysis.hpp"
#include "sentinel/behavior.hpp"
#include "sentinel/config.hpp"
#include "sentinel/hash.hpp"
#include "sentinel/logging.hpp"
#include "sentinel/quick_scan.hpp"
#include "sentinel/scanner.hpp"
#include "sentinel/signatures.hpp"

#include <algorithm>
#include <chrono>
#include <cstdint>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <vector>
#include <random>
#include <stdexcept>
#include <string>

namespace {

class TemporaryDirectory {
public:
    TemporaryDirectory() {
        const auto nonce = std::chrono::steady_clock::now()
                               .time_since_epoch()
                               .count();
        path_ = std::filesystem::temp_directory_path() /
                ("sentinel-core-tests-" + std::to_string(nonce));
        std::filesystem::create_directories(path_);
    }

    ~TemporaryDirectory() {
        std::error_code error;
        std::filesystem::remove_all(path_, error);
    }

    const std::filesystem::path& path() const {
        return path_;
    }

private:
    std::filesystem::path path_;
};

void require(bool condition, const std::string& message) {
    if (!condition) {
        throw std::runtime_error(message);
    }
}

void write_text(const std::filesystem::path& path, const std::string& text) {
    std::ofstream output(path, std::ios::binary);
    if (!output) {
        throw std::runtime_error("Cannot create test file: " + path.string());
    }
    output << text;
    if (!output) {
        throw std::runtime_error("Cannot write test file: " + path.string());
    }
}

void write_binary(
    const std::filesystem::path& path,
    const std::vector<unsigned char>& bytes) {
    std::ofstream output(path, std::ios::binary);
    if (!output) {
        throw std::runtime_error("Cannot create binary test file: " + path.string());
    }
    output.write(
        reinterpret_cast<const char*>(bytes.data()),
        static_cast<std::streamsize>(bytes.size()));
    if (!output) {
        throw std::runtime_error("Cannot write binary test file: " + path.string());
    }
}

void put_u16(
    std::vector<unsigned char>& bytes,
    std::size_t offset,
    std::uint16_t value) {
    bytes.at(offset) = static_cast<unsigned char>(value & 0xff);
    bytes.at(offset + 1) = static_cast<unsigned char>((value >> 8) & 0xff);
}

void put_u32(
    std::vector<unsigned char>& bytes,
    std::size_t offset,
    std::uint32_t value) {
    bytes.at(offset) = static_cast<unsigned char>(value & 0xff);
    bytes.at(offset + 1) = static_cast<unsigned char>((value >> 8) & 0xff);
    bytes.at(offset + 2) = static_cast<unsigned char>((value >> 16) & 0xff);
    bytes.at(offset + 3) = static_cast<unsigned char>((value >> 24) & 0xff);
}

template <typename Operation>
void require_throws(Operation operation, const std::string& message) {
    try {
        operation();
    } catch (const std::exception&) {
        return;
    }
    throw std::runtime_error(message);
}

void test_sha256_known_vector(const std::filesystem::path& root) {
    const auto file = root / "known-vector.txt";
    write_text(file, "abc");
    require(
        sentinel::sha256_file(file) ==
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        "SHA-256 did not match the known abc vector.");
}

void test_file_change_burst_detection(const std::filesystem::path& root) {
    sentinel::FileChangeBurstDetector detector;
    const auto start = sentinel::FileChangeBurstDetector::Clock::time_point{};
    require(
        !detector.observe(root / "one.txt", start),
        "Ransomware-like activity triggered below the threshold.");
    require(
        !detector.observe(root / "one.txt", start + std::chrono::seconds(1)),
        "A repeated change was incorrectly counted as a distinct file.");

    bool alerted = false;
    for (std::size_t index = 1;
         index < sentinel::FileChangeBurstDetector::minimum_distinct_files;
         ++index) {
        alerted = detector.observe(
            root / ("file-" + std::to_string(index) + ".txt"),
            start + std::chrono::seconds(2));
    }
    require(alerted, "High-volume file-change burst was not detected.");
    require(
        !detector.observe(root / "additional.txt", start + std::chrono::seconds(3)),
        "The same continuous burst emitted duplicate alerts.");

    require(
        !detector.observe(
            root / "after-window.txt",
            start + sentinel::FileChangeBurstDetector::observation_window +
                std::chrono::seconds(3)),
        "Expired changes were retained in the burst window.");
}

void test_signature_database(const std::filesystem::path& root) {
    const auto valid = root / "valid-signatures.txt";
    write_text(
        valid,
        "# safe sample\n"
        "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD\tfixture\n");
    const auto signatures = sentinel::load_signatures(valid);
    require(signatures.size() == 1, "Valid signature database has wrong size.");
    require(
        signatures.begin()->first ==
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        "Signature hashes should be normalized to lowercase.");

    const auto malformed = root / "malformed-signatures.txt";
    write_text(malformed, "not a signature\n");
    require_throws(
        [&] { sentinel::load_signatures(malformed); },
        "Malformed signature database was accepted.");

    const auto duplicate = root / "duplicate-signatures.txt";
    write_text(
        duplicate,
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad\tone\n"
        "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD\ttwo\n");
    require_throws(
        [&] { sentinel::load_signatures(duplicate); },
        "Duplicate signature was accepted.");
}

sentinel::SignatureDatabase test_signatures() {
    return {{
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        "safe-test-vector"}};
}

void test_report_only_scan(const std::filesystem::path& root) {
    const auto file = root / "report-only.txt";
    write_text(file, "abc");

    sentinel::ScanOptions options;
    options.target = file;
    const auto summary = sentinel::scan_path(options, test_signatures());
    require(summary.files_scanned == 1, "Report-only scan did not scan one file.");
    require(summary.threats_detected == 1, "Report-only scan missed its signature.");
    require(
        summary.highest_risk_score == 100 &&
            summary.highest_risk_severity == "SIGNATURE_MATCH",
        "Exact local signature match was not reflected in scan risk summary.");
    require(summary.files_quarantined == 0, "Report-only scan quarantined a file.");
    require(std::filesystem::exists(file), "Report-only scan moved its target.");
}

void test_quick_scan_aggregates_and_deduplicates_locations(
    const std::filesystem::path& root) {
    const auto downloads = root / "quick-downloads";
    const auto temporary = root / "quick-temp";
    std::filesystem::create_directories(downloads);
    std::filesystem::create_directories(temporary);
    write_text(downloads / "match.txt", "abc");
    write_text(temporary / "ordinary.txt", "ordinary content");

    sentinel::ScanOptions options;
    const auto summary = sentinel::scan_quick_locations(
        {
            {"Downloads", downloads},
            {"Temp", temporary},
            {"Duplicate Downloads", downloads / "."},
            {"Missing Startup", root / "missing-startup"} },
        options,
        test_signatures());
    require(summary.locations_scanned == 2,
            "Quick scan did not scan each existing unique location.");
    require(summary.locations_skipped == 2,
            "Quick scan did not report missing and duplicate locations as skipped.");
    require(summary.scan.files_scanned == 2,
            "Quick scan did not aggregate file totals across locations.");
    require(summary.scan.threats_detected == 1,
            "Quick scan did not aggregate signature matches.");
    require(summary.scan.errors == 0,
            "Quick scan reported an error for an absent optional location.");
    require(
        summary.scan.highest_risk_score == 100 &&
            summary.scan.highest_risk_severity == "SIGNATURE_MATCH",
        "Quick scan did not aggregate the highest risk assessment.");
}

void test_quarantine_scan(const std::filesystem::path& root) {
    const auto target = root / "quarantine-target";
    const auto quarantine = root / "quarantine";
    std::filesystem::create_directories(target);
    const auto file = target / "sample.txt";
    write_text(file, "abc");

    sentinel::ScanOptions options;
    options.target = target;
    options.quarantine_directory = quarantine;
    options.quarantine_matches = true;
    const auto summary = sentinel::scan_path(options, test_signatures());
    require(summary.files_scanned == 1, "Quarantine scan did not scan one file.");
    require(summary.threats_detected == 1, "Quarantine scan missed its signature.");
    require(summary.files_quarantined == 1, "Matching file was not quarantined.");
    require(!std::filesystem::exists(file), "Quarantined source file still exists.");
    const auto entries = sentinel::list_quarantine(quarantine);
    require(entries.size() == 1, "Quarantine history did not list the moved file.");
    require(
        entries.front().original_path == std::filesystem::weakly_canonical(file),
        "Quarantine history did not retain the original path.");
    require(
        entries.front().sha256 ==
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        "Quarantine history did not retain the SHA-256.");
    require(
        entries.front().threat == "safe-test-vector",
        "Quarantine history did not retain the threat label.");
    require(
        std::filesystem::exists(entries.front().quarantined_path),
        "Quarantined file was not written to the expected destination.");
    require(
        sentinel::format_quarantine_entry(entries.front()).rfind(
            "QUARANTINE_ENTRY\t", 0) == 0,
        "Quarantine CLI record did not have its documented prefix.");

    const auto restored = sentinel::restore_quarantined_file(
        quarantine, entries.front().id);
    require(restored.original_path == entries.front().original_path,
            "Restore returned the wrong original path.");
    require(std::filesystem::exists(file), "Quarantined file was not restored.");
    require(sentinel::sha256_file(file) ==
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            "Restored file contents changed.");
    require(
        sentinel::list_quarantine(quarantine).empty(),
        "Restored item remained in quarantine history.");
    require(
        sentinel::list_quarantine(root / "not-created").empty(),
        "An uncreated quarantine location should have empty history.");
}

void test_quarantine_restore_never_overwrites(const std::filesystem::path& root) {
    const auto target = root / "restore-overwrite-target";
    const auto quarantine = root / "restore-overwrite-quarantine";
    std::filesystem::create_directories(target);
    const auto file = target / "sample.txt";
    write_text(file, "abc");

    sentinel::ScanOptions options;
    options.target = file;
    options.quarantine_directory = quarantine;
    options.quarantine_matches = true;
    const auto summary = sentinel::scan_path(options, test_signatures());
    require(summary.files_quarantined == 1,
            "Test fixture was not moved into quarantine.");
    const auto entries = sentinel::list_quarantine(quarantine);
    write_text(file, "user data");
    require_throws(
        [&] { sentinel::restore_quarantined_file(quarantine, entries.front().id); },
        "Restore overwrote a file created at the original path.");
    require(
        std::filesystem::exists(entries.front().quarantined_path),
        "Failed restore removed the quarantined original.");
    std::ifstream original(file, std::ios::binary);
    std::string content;
    std::getline(original, content);
    require(content == "user data", "Failed restore modified the existing file.");
}

void test_quarantine_restore_checks_integrity(const std::filesystem::path& root) {
    const auto target = root / "restore-integrity-target";
    const auto quarantine = root / "restore-integrity-quarantine";
    std::filesystem::create_directories(target);
    const auto file = target / "sample.txt";
    write_text(file, "abc");

    sentinel::ScanOptions options;
    options.target = file;
    options.quarantine_directory = quarantine;
    options.quarantine_matches = true;
    require(
        sentinel::scan_path(options, test_signatures()).files_quarantined == 1,
        "Integrity fixture was not moved into quarantine.");
    const auto entries = sentinel::list_quarantine(quarantine);
    write_text(entries.front().quarantined_path, "modified after quarantine");
    require_throws(
        [&] {
            sentinel::restore_quarantined_file(quarantine, entries.front().id);
        },
        "Restore accepted a quarantined file whose contents changed.");
    require(
        !std::filesystem::exists(file) &&
            std::filesystem::exists(entries.front().quarantined_path),
        "Integrity failure did not leave the file isolated in quarantine.");
}

void test_quarantine_within_scan_target(const std::filesystem::path& root) {
    const auto target = root / "target-with-quarantine";
    const auto quarantine = target / "quarantine";
    std::filesystem::create_directories(quarantine);
    write_text(quarantine / "must-not-be-scanned.txt", "abc");
    write_text(target / "clean.txt", "ordinary text");

    sentinel::ScanOptions options;
    options.target = target;
    options.quarantine_directory = quarantine;
    options.quarantine_matches = true;
    const auto summary = sentinel::scan_path(options, {});
    require(summary.files_scanned == 1, "Scanner traversed its own quarantine folder.");
    require(summary.threats_detected == 0, "Empty database unexpectedly detected a file.");
}

void test_quarantine_parent_is_rejected(const std::filesystem::path& root) {
    const auto target = root / "nested" / "scan";
    std::filesystem::create_directories(target);
    sentinel::ScanOptions options;
    options.target = target;
    options.quarantine_directory = root;
    options.quarantine_matches = true;
    require_throws(
        [&] { sentinel::scan_path(options, {}); },
        "Quarantine parent of target was accepted.");
}

void test_entropy_and_suspicious_extensions(const std::filesystem::path& root) {
    const auto executable = root / "invoice.pdf.exe";
    write_text(executable, "harmless extension test");
    const auto extension_analysis = sentinel::analyze_file(executable);
    require(
        std::any_of(
            extension_analysis.findings.begin(),
            extension_analysis.findings.end(),
            [](const std::string& finding) {
                return finding.find("double extension") != std::string::npos;
            }),
        "Double-extension heuristic did not report its test fixture.");
    const auto masquerading_risk =
        sentinel::assess_file_risk(extension_analysis, false);
    require(
        masquerading_risk.score == 60 &&
            masquerading_risk.severity == "HIGH" &&
            masquerading_risk.category == "multiple_static_indicator_categories" &&
            masquerading_risk.confidence == "heuristic_only",
        "Masquerading plus non-PE executable extension was misclassified.");

    const auto high_entropy = root / "high-entropy.bin";
    std::vector<unsigned char> bytes(64 * 1024);
    std::mt19937 generator(0x51A7U);
    for (auto& byte : bytes) {
        byte = static_cast<unsigned char>(generator() & 0xff);
    }
    write_binary(high_entropy, bytes);
    const auto entropy_analysis = sentinel::analyze_file(high_entropy);
    require(entropy_analysis.entropy_available, "Entropy was not calculated.");
    require(
        entropy_analysis.entropy_bits_per_byte >= 7.2,
        "Deterministic high-entropy fixture was below the configured threshold.");
    require(
        std::any_of(
            entropy_analysis.findings.begin(),
            entropy_analysis.findings.end(),
            [](const std::string& finding) {
                return finding.find("high byte entropy") != std::string::npos;
            }),
        "High-entropy heuristic did not emit an indicator.");
    const auto entropy_risk = sentinel::assess_file_risk(entropy_analysis, false);
    require(
        entropy_risk.score == 15 &&
            entropy_risk.severity == "LOW" &&
            entropy_risk.category == "high_entropy",
        "High entropy alone should be a low-severity heuristic, not a detection.");
}

void test_pe_metadata_and_writable_executable_section(
    const std::filesystem::path& root) {
    constexpr std::size_t pe_offset = 0x80;
    constexpr std::size_t optional_header_size = 0xf0;
    constexpr std::size_t section_offset = pe_offset + 24 + optional_header_size;
    std::vector<unsigned char> bytes(section_offset + 40, 0);
    bytes[0] = 'M';
    bytes[1] = 'Z';
    put_u32(bytes, 0x3c, static_cast<std::uint32_t>(pe_offset));
    bytes[pe_offset] = 'P';
    bytes[pe_offset + 1] = 'E';
    put_u16(bytes, pe_offset + 4, 0x8664);
    put_u16(bytes, pe_offset + 6, 1);
    put_u16(
        bytes,
        pe_offset + 20,
        static_cast<std::uint16_t>(optional_header_size));
    put_u16(bytes, pe_offset + 22, 0x0022);
    put_u16(bytes, pe_offset + 24, 0x020b);
    bytes[section_offset] = '.';
    bytes[section_offset + 1] = 't';
    bytes[section_offset + 2] = 'e';
    bytes[section_offset + 3] = 'x';
    bytes[section_offset + 4] = 't';
    put_u32(bytes, section_offset + 36, 0xa0000000);

    const auto file = root / "metadata-fixture.bin";
    write_binary(file, bytes);
    const auto analysis = sentinel::analyze_file(file);
    require(analysis.is_pe, "MZ/PE fixture was not recognized as a PE image.");
    require(analysis.valid_pe, "Well-formed minimal PE fixture was rejected.");
    require(analysis.pe_architecture == "x64", "PE architecture was parsed incorrectly.");
    require(analysis.pe_section_count == 1, "PE section count was parsed incorrectly.");
    require(
        std::any_of(
            analysis.findings.begin(),
            analysis.findings.end(),
            [](const std::string& finding) {
                return finding.find("writable and executable") != std::string::npos;
            }),
        "Writable/executable PE section heuristic did not emit an indicator.");
}

void test_malformed_pe_is_reported(const std::filesystem::path& root) {
    std::vector<unsigned char> bytes(64, 0);
    bytes[0] = 'M';
    bytes[1] = 'Z';
    const auto file = root / "malformed.exe";
    write_binary(file, bytes);
    const auto analysis = sentinel::analyze_file(file);
    require(analysis.is_pe, "MZ prefix was not recognized.");
    require(!analysis.valid_pe, "Truncated PE fixture was accepted as valid.");
    require(
        std::any_of(
            analysis.findings.begin(),
            analysis.findings.end(),
            [](const std::string& finding) {
                return finding.find("malformed") != std::string::npos;
            }),
        "Malformed PE header did not emit a static-analysis finding.");
    const auto risk = sentinel::assess_file_risk(analysis, false);
    require(
        risk.score == 45 && risk.severity == "HIGH" &&
            risk.category == "malformed_executable",
        "Malformed PE risk was misclassified or its correlated indicators double-counted.");
}

void test_risk_assessment_is_not_a_safety_verdict() {
    const sentinel::FileAnalysis clean;
    const auto no_indicators = sentinel::assess_file_risk(clean, false);
    require(
        no_indicators.score == 0 &&
            no_indicators.severity == "NONE" &&
            no_indicators.category == "no_configured_indicator" &&
            no_indicators.confidence == "no_configured_indicator",
        "Clean assessment should report no indicators, not safe.");
    require(
        !no_indicators.reasons.empty() &&
            no_indicators.reasons.front().find("not a safety verdict") !=
                std::string::npos,
        "Clean assessment did not explicitly avoid a safety claim.");

    sentinel::FileAnalysis combined;
    combined.indicators = {
        {sentinel::IndicatorCategory::malformed_executable, 45, "malformed PE"},
        {sentinel::IndicatorCategory::malformed_executable, 35, "MZ/PE mismatch"},
        {sentinel::IndicatorCategory::high_entropy, 15, "high entropy"}};
    const auto independent = sentinel::assess_file_risk(combined, false);
    require(
        independent.score == 60 && independent.severity == "HIGH" &&
            independent.category == "multiple_static_indicator_categories",
        "Risk scoring did not combine independent categories or avoid double-counting correlated findings.");

    const auto signature =
        sentinel::assess_file_risk(clean, true);
    require(
        signature.score == 100 &&
            signature.severity == "SIGNATURE_MATCH" &&
            signature.category == "local_signature_match" &&
            signature.confidence ==
                "exact_local_hash_match_database_authenticity_unverified",
        "Exact database match did not remain distinct from heuristic severity.");
}

void test_process_location_heuristic() {
    const auto image = std::filesystem::temp_directory_path() / "sentinel-process-test.exe";
    const auto findings = sentinel::analyze_process_image_path(image);
    require(
        std::any_of(
            findings.begin(), findings.end(), [](const std::string& finding) {
                return finding.find("temporary directory") != std::string::npos;
            }),
        "Temporary-directory process heuristic did not identify the temp path.");
}

void test_configuration_file(const std::filesystem::path& root) {
    const auto config_directory = root / "config";
    std::filesystem::create_directories(config_directory);
    const auto config_file = config_directory / "sentinel.ini";
    write_text(
        config_file,
        "# relative paths are based at the config file\n"
        "signatures = data/signatures.txt\n"
        "log_file = logs/events.jsonl\n"
        "quarantine_directory = quarantine\n");
    const auto config = sentinel::load_engine_config(config_file);
    require(
        config.signatures_path == config_directory / "data" / "signatures.txt",
        "Relative signature path was not resolved against the config file.");
    require(
        config.log_path == config_directory / "logs" / "events.jsonl",
        "Relative log path was not resolved against the config file.");
    require(
        config.quarantine_directory == config_directory / "quarantine",
        "Relative quarantine path was not resolved against the config file.");

    const auto duplicate = config_directory / "duplicate.ini";
    write_text(duplicate, "log_file = first.jsonl\nlog_file = second.jsonl\n");
    require_throws(
        [&] { sentinel::load_engine_config(duplicate); },
        "Duplicate configuration key was accepted.");

    const auto unknown = config_directory / "unknown.ini";
    write_text(unknown, "unsupported_option = value\n");
    require_throws(
        [&] { sentinel::load_engine_config(unknown); },
        "Unknown configuration key was accepted.");
}

void test_structured_event_log(const std::filesystem::path& root) {
    const auto log_file = root / "logs" / "events.jsonl";
    {
        sentinel::Logger logger(log_file);
        logger.write(
            "warning",
            "test_event",
            "quoted \"value\"\nnext line",
            root / "sample.txt");
    }

    std::ifstream input(log_file, std::ios::binary);
    std::string line;
    require(static_cast<bool>(std::getline(input, line)), "Event log is empty.");
    require(line.find("\"severity\":\"warning\"") != std::string::npos,
            "Structured event severity was not serialized.");
    require(line.find("\"event\":\"test_event\"") != std::string::npos,
            "Structured event name was not serialized.");
    require(
        line.find("\"message\":\"quoted \\\"value\\\"\\nnext line\"") !=
            std::string::npos,
        "Structured event string escaping is invalid.");
    std::string second_line;
    require(!std::getline(input, second_line), "A single event wrote multiple JSON lines.");
}

}

int main() {
    try {
        TemporaryDirectory temporary;
        test_file_change_burst_detection(temporary.path());
        test_sha256_known_vector(temporary.path());
        test_signature_database(temporary.path());
        test_report_only_scan(temporary.path());
        test_quick_scan_aggregates_and_deduplicates_locations(temporary.path());
        test_quarantine_scan(temporary.path());
        test_quarantine_restore_never_overwrites(temporary.path());
        test_quarantine_restore_checks_integrity(temporary.path());
        test_quarantine_within_scan_target(temporary.path());
        test_quarantine_parent_is_rejected(temporary.path());
        test_entropy_and_suspicious_extensions(temporary.path());
        test_pe_metadata_and_writable_executable_section(temporary.path());
        test_malformed_pe_is_reported(temporary.path());
        test_risk_assessment_is_not_a_safety_verdict();
        test_process_location_heuristic();
        test_configuration_file(temporary.path());
        test_structured_event_log(temporary.path());
        std::cout << "All Sentinel AV core tests passed.\n";
        return 0;
    } catch (const std::exception& error) {
        std::cerr << "Test failure: " << error.what() << '\n';
        return 1;
    }
}
