#include "sentinel/scanner.hpp"
#include "sentinel/config.hpp"
#include "sentinel/logging.hpp"
#include "sentinel/signatures.hpp"
#include "sentinel/watcher.hpp"
#include "sentinel/process_monitor.hpp"

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
        << " watch <directory> [--signatures <file>]"
           " [--quarantine [directory]] [--config <file>] [--log <file>]\n  "
        << executable
        << " processes [--signatures <file>] [--config <file>] [--log <file>]\n\n"
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
    const bool path_command = command == "scan" || command == "watch";
    if ((!process_monitor && !path_command) || (path_command && argc < 3)) {
        print_usage(argv[0]);
        return 2;
    }

    sentinel::ScanOptions options;
    int option_index = 2;
    if (path_command) {
        options.target = std::filesystem::path(argv[2]);
        option_index = 3;
    }
    std::optional<std::filesystem::path> config_path;
    std::optional<std::filesystem::path> signatures_override;
    std::optional<std::filesystem::path> log_override;
    std::optional<std::filesystem::path> quarantine_override;
    bool quarantine_requested = false;

    for (int index = option_index; index < argc; ++index) {
        const std::string argument = argv[index];
        if ((argument == "--config" || argument == "--signatures" ||
             argument == "--log") &&
            index + 1 < argc) {
            const std::filesystem::path value = argv[++index];
            if (argument == "--config") {
                config_path = value;
            } else if (argument == "--signatures") {
                signatures_override = value;
            } else {
                log_override = value;
            }
        } else if (argument == "--quarantine") {
            if (process_monitor) {
                std::cerr << "--quarantine is not supported by process monitoring.\n";
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

        const auto signatures = sentinel::load_signatures(config.signatures_path);
        std::cout << "Loaded " << signatures.size() << " SHA-256 signature(s).\n";
        if (logger != nullptr) {
            logger->write(
                "info", "engine_started",
                "Sentinel AV command started: " + command);
        }
        if (process_monitor) {
            sentinel::watch_processes(signatures, logger.get());
            return 0;
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
                  << " quarantined, " << summary.errors << " error(s).\n";
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
                    " error(s).",
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
