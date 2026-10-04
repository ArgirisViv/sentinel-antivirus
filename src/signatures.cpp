#include "sentinel/signatures.hpp"

#include <algorithm>
#include <cctype>
#include <fstream>
#include <stdexcept>
#include <string>

namespace sentinel {
namespace {

bool is_hex_digit(char value) {
    return (value >= '0' && value <= '9') ||
           (value >= 'a' && value <= 'f') ||
           (value >= 'A' && value <= 'F');
}

std::string lowercase(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char ch) {
        return static_cast<char>(std::tolower(ch));
    });
    return value;
}

}

SignatureDatabase load_signatures(const std::filesystem::path& path) {
    std::ifstream input(path);
    if (!input) {
        throw std::runtime_error("Cannot open signature database: " + path.string());
    }

    SignatureDatabase signatures;
    std::string line;
    std::size_t line_number = 0;
    while (std::getline(input, line)) {
        ++line_number;
        if (!line.empty() && line.back() == '\r') {
            line.pop_back();
        }
        if (line.empty() || line.front() == '#') {
            continue;
        }

        const std::size_t separator = line.find('\t');
        if (separator != 64 || separator == std::string::npos ||
            separator + 1 == line.size()) {
            throw std::runtime_error(
                "Invalid signature at " + path.string() + ":" +
                std::to_string(line_number) +
                " (expected 64 hex characters, a tab, and a non-empty label).");
        }

        const std::string hash = line.substr(0, separator);
        if (!std::all_of(hash.begin(), hash.end(), is_hex_digit)) {
            throw std::runtime_error(
                "Invalid SHA-256 hex value at " + path.string() + ":" +
                std::to_string(line_number));
        }
        if (!signatures.emplace(lowercase(hash), line.substr(separator + 1)).second) {
            throw std::runtime_error(
                "Duplicate SHA-256 signature at " + path.string() + ":" +
                std::to_string(line_number));
        }
    }
    if (input.bad()) {
        throw std::runtime_error("Error reading signature database: " + path.string());
    }
    return signatures;
}

}
