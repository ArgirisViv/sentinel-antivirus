#include "sentinel/eicar.hpp"

#include <array>
#include <string>
#include <string_view>

namespace sentinel {

bool is_eicar_test_file(std::string_view prefix) {
    if (prefix.size() > eicar_maximum_prefix_size) {
        return false;
    }

    constexpr std::array<std::string_view, 4> parts = {
        "X5O!P%@AP[4\\P",
        "ZX54(P^)7CC)7}$EICAR-",
        "STANDARD-ANTIVIRUS-",
        "TEST-FILE!$H+H*"};
    std::string expected;
    for (const auto part : parts) {
        expected.append(part);
    }

    while (!prefix.empty()) {
        const char last = prefix.back();
        if (last != ' ' && last != '\r' && last != '\n' &&
            last != '\t' && last != '\x1a') {
            break;
        }
        prefix.remove_suffix(1);
    }

    return prefix == std::string_view(expected);
}

}
