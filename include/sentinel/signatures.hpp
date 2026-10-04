#pragma once

#include <filesystem>
#include <string>
#include <unordered_map>

namespace sentinel {

using SignatureDatabase = std::unordered_map<std::string, std::string>;

SignatureDatabase load_signatures(const std::filesystem::path& path);

}
