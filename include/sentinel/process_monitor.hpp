#pragma once

#include "sentinel/signatures.hpp"

namespace sentinel {

class Logger;

void watch_processes(
    const SignatureDatabase& signatures,
    Logger* logger = nullptr);

void print_process_tree(Logger* logger = nullptr);

}
