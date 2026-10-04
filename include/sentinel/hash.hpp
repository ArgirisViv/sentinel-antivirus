#pragma once

#include <filesystem>
#include <string>

namespace sentinel {

std::string sha256_file(const std::filesystem::path& path);

}
