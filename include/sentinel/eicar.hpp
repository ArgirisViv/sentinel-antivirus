#pragma once

#include <cstddef>
#include <string_view>

namespace sentinel {

inline constexpr std::size_t eicar_maximum_prefix_size = 128;

bool is_eicar_test_file(std::string_view prefix);

}
