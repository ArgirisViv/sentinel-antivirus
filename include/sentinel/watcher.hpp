#pragma once

#include "sentinel/scanner.hpp"

namespace sentinel {

void watch_directory(
    const ScanOptions& options,
    const SignatureDatabase& signatures);

}
