#include "sentinel/config.hpp"

#include <algorithm>
#include <cctype>
#include <fstream>
#include <set>
#include <stdexcept>
#include <string>

namespace sentinel {
namespace {

std::string trim(std::string value) {
    const auto not_space = [](unsigned char character) {
        return !std::isspace(character);
    };
    const auto first = std::find_if(value.begin(), value.end(), not_space);
    const auto last = std::find_if(value.rbegin(), value.rend(), not_space).base();
    if (first >= last) {
        return {};
    }
    return std::string(first, last);
}

std::filesystem::path resolve_path(
    const std::filesystem::path& config_file,
    const std::string& value) {
    const std::filesystem::path configured(value);
    if (configured.is_absolute()) {
        return configured.lexically_normal();
    }
    return (config_file.parent_path() / configured).lexically_normal();
}

}

EngineConfig load_engine_config(const std::filesystem::path& file) {
    std::ifstream input(file);
    if (!input) {
        throw std::runtime_error("Cannot open configuration file: " + file.string());
    }

    EngineConfig config;
    std::set<std::string> seen;
    std::string line;
    std::size_t line_number = 0;
    while (std::getline(input, line)) {
        ++line_number;
        if (!line.empty() && line.back() == '\r') {
            line.pop_back();
        }
        const std::string content = trim(line);
        if (content.empty() || content.front() == '#' || content.front() == ';') {
            continue;
        }

        const auto separator = content.find('=');
        if (separator == std::string::npos) {
            throw std::runtime_error(
                "Invalid configuration at " + file.string() + ":" +
                std::to_string(line_number) + " (expected key=value).");
        }
        const auto key = trim(content.substr(0, separator));
        const auto value = trim(content.substr(separator + 1));
        if (key.empty() || value.empty()) {
            throw std::runtime_error(
                "Empty configuration key or value at " + file.string() + ":" +
                std::to_string(line_number));
        }
        if (!seen.insert(key).second) {
            throw std::runtime_error(
                "Duplicate configuration key '" + key + "' at " +
                file.string() + ":" + std::to_string(line_number));
        }

        if (key == "signatures") {
            config.signatures_path = resolve_path(file, value);
        } else if (key == "log_file") {
            config.log_path = resolve_path(file, value);
        } else if (key == "quarantine_directory") {
            config.quarantine_directory = resolve_path(file, value);
        } else {
            throw std::runtime_error(
                "Unknown configuration key '" + key + "' at " +
                file.string() + ":" + std::to_string(line_number));
        }
    }
    if (input.bad()) {
        throw std::runtime_error("Error reading configuration file: " + file.string());
    }
    return config;
}

}
