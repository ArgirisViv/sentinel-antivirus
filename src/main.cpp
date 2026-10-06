#include "sentinel/scanner.hpp"
#include "sentinel/config.hpp"
#include "sentinel/logging.hpp"
#include "sentinel/signatures.hpp"
#include "sentinel/watcher.hpp"
#include "sentinel/process_monitor.hpp"
#include "sentinel/quick_scan.hpp"

#include <filesystem>
#include <iostream>
#include <memory>
#include <optional>
#include <stdexcept>
#include <string>

namespace {

void print_usage(const char* executable) {
    std::cout
        << "Sentinel AV - file scanner and monitors\n\n"
        << "Usage:\n  " << executable
        << " scan <file-or-directory> [--signatures <file>]"
           " [--quarantine <directory>]\n  " << executable
        << " quick-scan [--signatures <file>] [--config <file>]"
           " [--quarantine [directory]]\n  " << executable
        << " watch <directory> [--signatures <file>]"
           " [--quarantine [directory]] [--config <file>] [--log <file>]\n  "
        << executable
        << " processes [--signatures <file>] [--config <file>] [--log <file>]\n  "
        << executable
        << " process-tree [--config <file>] [--log <file>]\n  "
        << executable
        << " quarantine list [--quarantine-dir <directory>] [--config <file>]\n  "
        << executable
        << " quarantine restore <id> [--quarantine-dir <directory>] [--config <file>]\n\n"
        << "Without --quarantine, detected files are only reported, not moved.\n";
}

}

int main(int argc, char* argv[]) {
    // Flush every write so output is visible immediately when stdout is a pipe
    // (the Java dashboard, `docker logs`).
    std::cout << std::unitbuf;
    std::cerr << std::unitbuf;

    if (argc < 2 || std::string(argv[1]) == "--help" ||
        std::string(argv[1]) == "-h") {
        print_usage(argv[0]);
        return argc < 2 ? 2 : 0;
    }

    const std::string command = argv[1];
    const bool process_monitor = command == "processes";
    const bool process_tree = command == "process-tree";
    const bool quarantine_command = command == "quarantine";
    const bool quick_scan = command == "quick-scan";
    const bool path_command = command == "scan" || command == "watch";
    if ((!process_monitor && !process_tree && !quarantine_command &&
         !quick_scan && !path_command) ||
        (path_command && argc < 3)) {
        print_usage(argv[0]);
        return 2;
    }

    sentinel::ScanOptions options;
    int option_index = 2;
    std::string quarantine_action;
    std::string quarantine_id;
    std::optional<std::filesystem::path> quarantine_directory_override;
    if (path_command) {
        options.target = std::filesystem::path(argv[2]);
        option_index = 3;
    } else if (quarantine_command) {
        if (argc < 3) {
            print_usage(argv[0]);
            return 2;
        }
        quarantine_action = argv[2];
        if (quarantine_action == "list") {
            option_index = 3;
        } else if (quarantine_action == "restore" && argc >= 4) {
            quarantine_id = argv[3];
            option_index = 4;
        } else {
            print_usage(argv[0]);
            return 2;
        }
    }
    std::optional<std::filesystem::path> config_path;
    std::optional<std::filesystem::path> signatures_override;
    std::optional<std::filesystem::path> log_override;
    std::optional<std::filesystem::path> quarantine_override;
    bool quarantine_requested = false;

    for (int index = option_index; index < argc; ++index) {
        const std::string argument = argv[index];
        if ((argument == "--config" || argument == "--signatures" ||
             argument == "--log" || argument == "--quarantine-dir") &&
            index + 1 < argc) {
            const std::filesystem::path value = argv[++index];
            if (argument == "--config") {
                config_path = value;
            } else if (argument == "--signatures") {
                signatures_override = value;
            } else if (argument == "--quarantine-dir") {
                if (!quarantine_command) {
                    std::cerr << "--quarantine-dir is only valid for quarantine commands.\n";
                    return 2;
                }
                quarantine_directory_override = value;
            } else {
                log_override = value;
            }
        } else if (argument == "--quarantine") {
            if (!path_command && !quick_scan) {
                std::cerr << "--quarantine is only valid for scan, quick-scan, and watch.\n";
                return 2;
            }
            quarantine_requested = true;
            options.quarantine_matches = true;
            if (index + 1 < argc &&
                std::string(argv[index + 1]).rfind("--", 0) != 0) {
                quarantine_override = std::filesystem::path(argv[++index]);
            }
        } else {
            std::cerr << "Unknown or incomplete option: " << argument << '\n';
            print_usage(argv[0]);
            return 2;
        }
    }

    std::unique_ptr<sentinel::Logger> logger;
    try {
        sentinel::EngineConfig config;
        if (config_path) {
            config = sentinel::load_engine_config(*config_path);
        }
        if (signatures_override) {
            config.signatures_path = *signatures_override;
        }
        if (log_override) {
            config.log_path = *log_override;
        }
        if (quarantine_directory_override) {
            config.quarantine_directory = *quarantine_directory_override;
        }
        options.quarantine_directory = config.quarantine_directory;
        if (quarantine_override) {
            options.quarantine_directory = *quarantine_override;
        }
        if (quarantine_requested && options.quarantine_directory.empty()) {
            throw std::runtime_error(
                "--quarantine needs a directory or quarantine_directory in config.");
        }
        if (!config.log_path.empty()) {
            logger = std::make_unique<sentinel::Logger>(config.log_path);
        }
        options.logger = logger.get();

        if (logger != nullptr) {
            logger->write(
                "info", "engine_started",
                "Sentinel AV command started: " + command);
        }
        if (quarantine_command) {
            if (options.quarantine_directory.empty()) {
                throw std::runtime_error(
                    "Quarantine directory must be set in config or with --quarantine-dir.");
            }
            if (quarantine_action == "list") {
                const auto entries =
                    sentinel::list_quarantine(options.quarantine_directory);
                if (entries.empty()) {
                    std::cout << "QUARANTINE_EMPTY\n";
                } else {
                    for (const auto& entry : entries) {
                        std::cout << sentinel::format_quarantine_entry(entry) << '\n';
                    }
                }
                return 0;
            }
            const auto restored = sentinel::restore_quarantined_file(
                options.quarantine_directory, quarantine_id, logger.get());
            std::cout << "[RESTORED] " << restored.original_path.string() << '\n';
            return 0;
        }
        if (process_tree) {
            sentinel::print_process_tree(logger.get());
            return 0;
        }

        const auto signatures = sentinel::load_signatures(config.signatures_path);
        std::cout << "Loaded " << signatures.size() << " SHA-256 signature(s).\n";
        if (process_monitor) {
            sentinel::watch_processes(signatures, logger.get());
            return 0;
        }
        if (quick_scan) {
            const auto plan = sentinel::discover_quick_scan_locations();
            auto summary =
                sentinel::scan_quick_locations(plan.locations, options, signatures);
            summary.scan.errors += plan.errors.size();
            for (const auto& discovery_error : plan.errors) {
                std::cerr << "[QUICK SCAN ERROR] " << discovery_error << '\n';
                if (logger != nullptr) {
                    logger->write(
                        "error", "quick_scan_location_error", discovery_error);
                }
            }
            std::cout << "\nQuick scan complete: "
                      << summary.locations_scanned << " location(s) scanned, "
                      << summary.scan.files_scanned << " file(s) scanned, "
                      << summary.scan.threats_detected << " detection(s), "
                      << summary.scan.suspicious_files << " suspicious file(s), "
                      << summary.locations_skipped << " skipped, "
                      << summary.scan.errors << " error(s), highest file risk "
                      << summary.scan.highest_risk_score << "/100 ("
                      << summary.scan.highest_risk_severity << ").\n";
            if (logger != nullptr) {
                logger->write(
                    summary.scan.errors == 0 ? "info" : "error",
                    "quick_scan_completed",
                    std::to_string(summary.locations_scanned) +
                        " location(s); " +
                        std::to_string(summary.scan.files_scanned) +
                        " file(s); " +
                        std::to_string(summary.scan.threats_detected) +
                        " signature detection(s); " +
                        std::to_string(summary.scan.errors) + " error(s).");
            }
            return summary.scan.errors == 0 ? 0 : 1;
        }
        if (command == "watch") {
            sentinel::watch_directory(options, signatures);
            return 0;
        }
        const auto summary = sentinel::scan_path(options, signatures);
        std::cout << "\nScan complete: " << summary.files_scanned
                  << " file(s) scanned, " << summary.threats_detected
                  << " detection(s), " << summary.suspicious_files
                  << " suspicious file(s), " << summary.files_quarantined
                  << " quarantined, " << summary.errors << " error(s), highest "
                  << "file risk " << summary.highest_risk_score << "/100 ("
                  << summary.highest_risk_severity << ").\n";
        if (logger != nullptr) {
            logger->write(
                summary.errors == 0 ? "info" : "error",
                "scan_summary",
                std::to_string(summary.files_scanned) + " file(s); " +
                    std::to_string(summary.threats_detected) +
                    " signature detection(s); " +
                    std::to_string(summary.suspicious_files) +
                    " suspicious file(s); " +
                    std::to_string(summary.files_quarantined) +
                    " quarantined; " + std::to_string(summary.errors) +
                    " error(s); highest file risk " +
                    std::to_string(summary.highest_risk_score) + "/100 (" +
                    summary.highest_risk_severity + ").",
                options.target);
        }
        return summary.errors == 0 ? 0 : 1;
    } catch (const std::exception& error) {
        std::cerr << "[FATAL] " << error.what() << '\n';
        if (logger != nullptr) {
            try {
                logger->write("error", "fatal_error", error.what());
            } catch (const std::exception& log_error) {
                std::cerr << "[FATAL] Could not persist fatal event: "
                          << log_error.what() << '\n';
            }
        }
        return 1;
    }
}
