#include "sentinel/analysis.hpp"

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdlib>
#include <fstream>
#include <iomanip>
#include <limits>
#include <iterator>
#include <sstream>
#include <stdexcept>
#include <string>
#include <utility>

#ifdef _WIN32
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
#endif

namespace sentinel {
namespace {

constexpr std::uintmax_t entropy_sample_limit = 1024 * 1024;
constexpr std::uintmax_t minimum_entropy_sample = 4096;
constexpr double high_entropy_threshold = 7.2;
constexpr std::uint16_t maximum_normal_pe_sections = 96;

bool has_finding(const FileAnalysis& analysis, const std::string& finding) {
    return std::find(
               analysis.findings.begin(), analysis.findings.end(), finding) !=
           analysis.findings.end();
}

void add_finding(
    FileAnalysis& analysis,
    IndicatorCategory category,
    unsigned int risk_points,
    std::string finding) {
    if (!has_finding(analysis, finding)) {
        analysis.findings.push_back(finding);
        analysis.indicators.push_back(
            StaticIndicator{category, risk_points, std::move(finding)});
    }
}

std::string category_name(IndicatorCategory category) {
    switch (category) {
    case IndicatorCategory::active_content:
        return "active_content";
    case IndicatorCategory::file_masquerading:
        return "file_masquerading";
    case IndicatorCategory::malformed_executable:
        return "malformed_executable";
    case IndicatorCategory::suspicious_pe_structure:
        return "suspicious_pe_structure";
    case IndicatorCategory::high_entropy:
        return "high_entropy";
    }
    return "unknown";
}

std::string severity_for_score(unsigned int score) {
    if (score == 0) {
        return "NONE";
    }
    if (score < 20) {
        return "LOW";
    }
    if (score < 40) {
        return "MEDIUM";
    }
    if (score < 70) {
        return "HIGH";
    }
    return "CRITICAL";
}

std::string lowercase_ascii(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char ch) {
        if (ch >= 'A' && ch <= 'Z') {
            return static_cast<char>(ch - 'A' + 'a');
        }
        return static_cast<char>(ch);
    });
    return value;
}

template <std::size_t Size>
bool contains_extension(
    const std::array<const char*, Size>& extensions,
    const std::string& extension) {
    return std::any_of(
        extensions.begin(), extensions.end(), [&extension](const char* candidate) {
            return extension == candidate;
        });
}

void inspect_filename(const std::filesystem::path& file, FileAnalysis& analysis) {
    static constexpr std::array<const char*, 18> active_extensions = {
        ".bat", ".cmd", ".com", ".cpl", ".hta", ".js", ".jse", ".lnk",
        ".msi", ".msp", ".pif", ".ps1", ".psm1", ".scr", ".vbe", ".vbs",
        ".wsf", ".wsh"};
    static constexpr std::array<const char*, 13> lure_extensions = {
        ".doc", ".docm", ".docx", ".gif", ".jpeg", ".jpg", ".pdf", ".png",
        ".ppt", ".pptm", ".pptx", ".txt", ".xls"};

    const auto extension = lowercase_ascii(file.extension().string());
    if (contains_extension(active_extensions, extension)) {
        add_finding(
            analysis,
            IndicatorCategory::active_content,
            5,
            "active or executable file extension " + extension);
    }

    if (extension == ".exe" || extension == ".scr" || extension == ".com") {
        const auto previous_extension =
            lowercase_ascii(std::filesystem::path(file.stem()).extension().string());
        if (contains_extension(lure_extensions, previous_extension)) {
            add_finding(
                analysis,
                IndicatorCategory::file_masquerading,
                25,
                "double extension may disguise an executable as " + previous_extension);
        }
    }
}

double calculate_entropy(
    const std::array<std::uint64_t, 256>& counts,
    std::uintmax_t sample_size) {
    double entropy = 0.0;
    for (const auto count : counts) {
        if (count == 0) {
            continue;
        }
        const double probability =
            static_cast<double>(count) / static_cast<double>(sample_size);
        entropy -= probability * std::log2(probability);
    }
    return entropy;
}

bool read_at(
    std::ifstream& input,
    std::uintmax_t file_size,
    std::uintmax_t offset,
    char* output,
    std::size_t size) {
    if (offset > file_size || size > file_size - offset ||
        offset > static_cast<std::uintmax_t>(
                     std::numeric_limits<std::streamoff>::max())) {
        return false;
    }
    input.clear();
    input.seekg(static_cast<std::streamoff>(offset), std::ios::beg);
    if (!input) {
        return false;
    }
    input.read(output, static_cast<std::streamsize>(size));
    return input.gcount() == static_cast<std::streamsize>(size);
}

bool read_u16(
    std::ifstream& input,
    std::uintmax_t file_size,
    std::uintmax_t offset,
    std::uint16_t& value) {
    std::array<unsigned char, 2> bytes{};
    if (!read_at(
            input, file_size, offset,
            reinterpret_cast<char*>(bytes.data()), bytes.size())) {
        return false;
    }
    value = static_cast<std::uint16_t>(bytes[0]) |
            static_cast<std::uint16_t>(bytes[1] << 8);
    return true;
}

bool read_u32(
    std::ifstream& input,
    std::uintmax_t file_size,
    std::uintmax_t offset,
    std::uint32_t& value) {
    std::array<unsigned char, 4> bytes{};
    if (!read_at(
            input, file_size, offset,
            reinterpret_cast<char*>(bytes.data()), bytes.size())) {
        return false;
    }
    value = static_cast<std::uint32_t>(bytes[0]) |
            (static_cast<std::uint32_t>(bytes[1]) << 8) |
            (static_cast<std::uint32_t>(bytes[2]) << 16) |
            (static_cast<std::uint32_t>(bytes[3]) << 24);
    return true;
}

void inspect_pe(
    std::ifstream& input,
    std::uintmax_t file_size,
    FileAnalysis& analysis) {
    std::array<char, 2> dos_signature{};
    if (!read_at(input, file_size, 0, dos_signature.data(), dos_signature.size()) ||
        dos_signature[0] != 'M' || dos_signature[1] != 'Z') {
        return;
    }
    analysis.is_pe = true;

    auto malformed = [&analysis] {
        add_finding(
            analysis,
            IndicatorCategory::malformed_executable,
            45,
            "malformed or truncated PE image headers");
    };

    std::uint32_t pe_offset = 0;
    if (!read_u32(input, file_size, 0x3c, pe_offset)) {
        malformed();
        return;
    }
    std::array<char, 4> signature{};
    if (!read_at(input, file_size, pe_offset, signature.data(), signature.size()) ||
        signature != std::array<char, 4>{'P', 'E', '\0', '\0'}) {
        malformed();
        return;
    }

    std::uint16_t machine = 0;
    std::uint16_t section_count = 0;
    std::uint16_t optional_header_size = 0;
    std::uint16_t characteristics = 0;
    if (!read_u16(input, file_size, pe_offset + 4, machine) ||
        !read_u16(input, file_size, pe_offset + 6, section_count) ||
        !read_u16(input, file_size, pe_offset + 20, optional_header_size) ||
        !read_u16(input, file_size, pe_offset + 22, characteristics)) {
        malformed();
        return;
    }

    switch (machine) {
    case 0x014c:
        analysis.pe_architecture = "x86";
        break;
    case 0x8664:
        analysis.pe_architecture = "x64";
        break;
    case 0xaa64:
        analysis.pe_architecture = "ARM64";
        break;
    default:
        analysis.pe_architecture = "unknown";
        add_finding(
            analysis,
            IndicatorCategory::suspicious_pe_structure,
            10,
            "PE image uses an unrecognized architecture");
        break;
    }

    analysis.pe_section_count = section_count;
    if (section_count == 0 || section_count > maximum_normal_pe_sections) {
        add_finding(
            analysis,
            IndicatorCategory::suspicious_pe_structure,
            10,
            "PE image has an unusual section count");
    }
    if ((characteristics & 0x0002) == 0) {
        add_finding(
            analysis,
            IndicatorCategory::suspicious_pe_structure,
            10,
            "PE file header is not marked as an executable image");
    }

    const std::uintmax_t optional_offset =
        static_cast<std::uintmax_t>(pe_offset) + 24;
    std::uint16_t optional_magic = 0;
    if (optional_header_size < 70 ||
        !read_u16(input, file_size, optional_offset, optional_magic) ||
        (optional_magic != 0x010b && optional_magic != 0x020b)) {
        malformed();
        return;
    }

    const std::uintmax_t section_table_offset =
        optional_offset + optional_header_size;
    const std::uintmax_t section_table_size =
        static_cast<std::uintmax_t>(section_count) * 40;
    if (section_table_offset > file_size ||
        section_table_size > file_size - section_table_offset) {
        malformed();
        return;
    }

    for (std::uint16_t index = 0; index < section_count; ++index) {
        std::uint32_t section_flags = 0;
        const std::uintmax_t flags_offset =
            section_table_offset + static_cast<std::uintmax_t>(index) * 40 + 36;
        if (!read_u32(input, file_size, flags_offset, section_flags)) {
            malformed();
            return;
        }
        constexpr std::uint32_t section_execute = 0x20000000;
        constexpr std::uint32_t section_write = 0x80000000;
        if ((section_flags & section_execute) != 0 &&
            (section_flags & section_write) != 0) {
            add_finding(
                analysis,
                IndicatorCategory::suspicious_pe_structure,
                30,
                "PE section is both writable and executable");
        }
    }

    analysis.valid_pe = true;
}

std::filesystem::path normalized(const std::filesystem::path& path) {
    std::error_code error;
    const auto result = std::filesystem::weakly_canonical(path, error);
    if (error) {
        return path.lexically_normal();
    }
    return result;
}

bool is_same_or_child(
    const std::filesystem::path& candidate,
    const std::filesystem::path& root) {
    auto candidate_part = candidate.begin();
    auto root_part = root.begin();
    for (; root_part != root.end(); ++root_part, ++candidate_part) {
        if (candidate_part == candidate.end()) {
            return false;
        }
#ifdef _WIN32
        if (_wcsicmp(candidate_part->c_str(), root_part->c_str()) != 0) {
#else
        if (*candidate_part != *root_part) {
#endif
            return false;
        }
    }
    return true;
}

void add_process_location_finding(
    std::vector<std::string>& findings,
    const std::filesystem::path& image,
    const std::filesystem::path& root,
    const char* label) {
    if (!root.empty() && is_same_or_child(normalized(image), normalized(root))) {
        findings.emplace_back(
            std::string("process image is located under ") + label);
    }
}

}

FileAnalysis analyze_file(const std::filesystem::path& file) {
    std::ifstream input(file, std::ios::binary);
    if (!input) {
        throw std::runtime_error("Cannot open file for static analysis: " + file.string());
    }

    std::error_code error;
    const std::uintmax_t file_size = std::filesystem::file_size(file, error);
    if (error) {
        throw std::runtime_error(
            "Cannot determine file size for analysis: " + error.message());
    }

    FileAnalysis analysis;
    std::array<std::uint64_t, 256> byte_counts{};
    std::array<char, 64 * 1024> buffer{};
    const std::uintmax_t sample_limit =
        std::min(file_size, entropy_sample_limit);
    while (analysis.sampled_bytes < sample_limit) {
        const auto remaining = sample_limit - analysis.sampled_bytes;
        const auto requested = static_cast<std::streamsize>(
            std::min<std::uintmax_t>(remaining, buffer.size()));
        input.read(buffer.data(), requested);
        const auto bytes_read = input.gcount();
        if (bytes_read <= 0) {
            throw std::runtime_error(
                "File changed or could not be read during static analysis: " +
                file.string());
        }
        for (std::streamsize index = 0; index < bytes_read; ++index) {
            ++byte_counts[static_cast<unsigned char>(buffer[static_cast<std::size_t>(index)])];
        }
        analysis.sampled_bytes += static_cast<std::uintmax_t>(bytes_read);
    }

    if (analysis.sampled_bytes >= minimum_entropy_sample) {
        analysis.entropy_available = true;
        analysis.entropy_bits_per_byte =
            calculate_entropy(byte_counts, analysis.sampled_bytes);
        if (analysis.entropy_bits_per_byte >= high_entropy_threshold) {
            std::ostringstream message;
            message << "high byte entropy ("
                    << std::fixed << std::setprecision(2)
                    << analysis.entropy_bits_per_byte
                    << " bits/byte in the first "
                    << analysis.sampled_bytes << " bytes)";
            add_finding(
                analysis,
                IndicatorCategory::high_entropy,
                15,
                message.str());
        }
    }

    inspect_filename(file, analysis);
    inspect_pe(input, file_size, analysis);
    if (analysis.is_pe && !analysis.valid_pe) {
        add_finding(
            analysis,
            IndicatorCategory::malformed_executable,
            45,
            "file begins with MZ but has no valid PE headers");
    }
    if (!analysis.is_pe) {
        const auto extension = lowercase_ascii(file.extension().string());
        if (extension == ".exe" || extension == ".dll" ||
            extension == ".sys" || extension == ".scr") {
            add_finding(
                analysis,
                IndicatorCategory::malformed_executable,
                35,
                "PE-like file extension does not match a PE image");
        }
    }
    return analysis;
}

RiskAssessment assess_file_risk(
    const FileAnalysis& analysis,
    bool exact_signature_match) {
    RiskAssessment assessment;
    if (exact_signature_match) {
        assessment.score = 100;
        assessment.severity = "SIGNATURE_MATCH";
        assessment.category = "local_signature_match";
        assessment.confidence = "exact_local_hash_match_database_authenticity_unverified";
        assessment.reasons.emplace_back(
            "SHA-256 exactly matches an entry in the configured local database; "
            "the database entry is not independently authenticated.");
        return assessment;
    }

    std::vector<std::pair<IndicatorCategory, unsigned int>> category_scores;
    std::vector<IndicatorCategory> categories;
    for (const auto& indicator : analysis.indicators) {
        const auto category = std::find(
            categories.begin(), categories.end(), indicator.category);
        if (category == categories.end()) {
            categories.push_back(indicator.category);
            category_scores.emplace_back(indicator.category, indicator.risk_points);
        } else {
            const auto index = static_cast<std::size_t>(
                std::distance(categories.begin(), category));
            category_scores[index].second =
                std::max(category_scores[index].second, indicator.risk_points);
        }
        assessment.reasons.push_back(indicator.description);
    }
    unsigned int score = 0;
    for (const auto& category_score : category_scores) {
        score = std::min(99U, score + category_score.second);
    }
    assessment.score = score;
    assessment.severity = severity_for_score(score);
    assessment.confidence =
        analysis.indicators.empty() ? "no_configured_indicator" : "heuristic_only";

    if (categories.empty()) {
        assessment.category = "no_configured_indicator";
        assessment.reasons.emplace_back(
            "No configured static indicator fired; this is not a safety verdict.");
    } else if (categories.size() > 1) {
        assessment.category = "multiple_static_indicator_categories";
    } else {
        assessment.category = category_name(categories.front());
    }
    return assessment;
}

std::vector<std::string> analyze_process_image_path(
    const std::filesystem::path& image_path) {
    std::vector<std::string> findings;
#ifdef _WIN32
    std::array<wchar_t, 32768> temp_buffer{};
    const DWORD temp_length = GetTempPathW(
        static_cast<DWORD>(temp_buffer.size()), temp_buffer.data());
    if (temp_length > 0 && temp_length < temp_buffer.size()) {
        add_process_location_finding(
            findings,
            image_path,
            std::filesystem::path(
                std::wstring(temp_buffer.data(), temp_length)),
            "the Windows temporary directory");
    }

    const wchar_t* profile = _wgetenv(L"USERPROFILE");
    if (profile != nullptr) {
        const auto profile_path = std::filesystem::path(profile);
        add_process_location_finding(
            findings, image_path, profile_path / L"Downloads",
            "the current user's Downloads directory");
    }
    const wchar_t* local_app_data = _wgetenv(L"LOCALAPPDATA");
    if (local_app_data != nullptr) {
        add_process_location_finding(
            findings, image_path, std::filesystem::path(local_app_data),
            "the current user's local AppData directory");
    }
#else
    (void)image_path;
#endif
    return findings;
}

}
