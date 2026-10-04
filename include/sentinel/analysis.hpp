#pragma once

#include <cstdint>
#include <filesystem>
#include <string>
#include <vector>

namespace sentinel {

struct FileAnalysis {
    std::uintmax_t sampled_bytes = 0;
    double entropy_bits_per_byte = 0.0;
    bool entropy_available = false;
    bool is_pe = false;
    bool valid_pe = false;
    std::string pe_architecture;
    std::uint16_t pe_section_count = 0;
    std::vector<std::string> findings;
};

FileAnalysis analyze_file(const std::filesystem::path& file);

std::vector<std::string> analyze_process_image_path(
    const std::filesystem::path& image_path);

}
