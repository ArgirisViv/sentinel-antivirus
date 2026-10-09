#pragma once

#include <cstdint>
#include <filesystem>
#include <string>
#include <vector>

namespace sentinel {

enum class IndicatorCategory {
    active_content,
    file_masquerading,
    malformed_executable,
    suspicious_pe_structure,
    high_entropy
};

struct StaticIndicator {
    IndicatorCategory category;
    unsigned int risk_points;
    std::string description;
};

struct FileAnalysis {
    std::uintmax_t sampled_bytes = 0;
    double entropy_bits_per_byte = 0.0;
    bool entropy_available = false;
    bool is_pe = false;
    bool valid_pe = false;
    std::string pe_architecture;
    std::uint16_t pe_section_count = 0;
    std::vector<std::string> findings;
    std::vector<StaticIndicator> indicators;
};

struct RiskAssessment {
    unsigned int score = 0;
    std::string severity;
    std::string category;
    std::string confidence;
    std::vector<std::string> reasons;
};

FileAnalysis analyze_file(const std::filesystem::path& file);

RiskAssessment assess_file_risk(
    const FileAnalysis& analysis,
    bool exact_signature_match,
    bool exact_eicar_test_pattern_match = false);

std::vector<std::string> analyze_process_image_path(
    const std::filesystem::path& image_path);

}
