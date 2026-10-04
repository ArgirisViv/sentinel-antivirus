#include "sentinel/hash.hpp"

#include <array>
#include <fstream>
#include <iomanip>
#include <memory>
#include <sstream>
#include <stdexcept>
#include <vector>

#ifdef _WIN32
#include <windows.h>
#include <bcrypt.h>
#else
#error "SHA-256 currently uses the Windows CNG API."
#endif

namespace sentinel {
namespace {

struct AlgorithmCloser {
    void operator()(BCRYPT_ALG_HANDLE handle) const noexcept {
        if (handle != nullptr) {
            BCryptCloseAlgorithmProvider(handle, 0);
        }
    }
};

struct HashCloser {
    void operator()(BCRYPT_HASH_HANDLE handle) const noexcept {
        if (handle != nullptr) {
            BCryptDestroyHash(handle);
        }
    }
};

[[noreturn]] void throw_ntstatus(const char* operation, NTSTATUS status) {
    std::ostringstream message;
    message << operation << " failed (NTSTATUS 0x" << std::hex
            << static_cast<unsigned long>(status) << ')';
    throw std::runtime_error(message.str());
}

void check_ntstatus(const char* operation, NTSTATUS status) {
    if (!BCRYPT_SUCCESS(status)) {
        throw_ntstatus(operation, status);
    }
}

ULONG get_property(
    BCRYPT_HANDLE handle,
    LPCWSTR property,
    const char* operation) {
    ULONG value = 0;
    ULONG result_size = 0;
    const NTSTATUS status = BCryptGetProperty(
        handle,
        property,
        reinterpret_cast<PUCHAR>(&value),
        sizeof(value),
        &result_size,
        0);
    check_ntstatus(operation, status);
    return value;
}

}

std::string sha256_file(const std::filesystem::path& path) {
    std::ifstream input(path, std::ios::binary);
    if (!input) {
        throw std::runtime_error("Cannot open file for hashing: " + path.string());
    }

    BCRYPT_ALG_HANDLE raw_algorithm = nullptr;
    check_ntstatus(
        "BCryptOpenAlgorithmProvider",
        BCryptOpenAlgorithmProvider(
            &raw_algorithm,
            BCRYPT_SHA256_ALGORITHM,
            nullptr,
            0));
    const std::unique_ptr<void, AlgorithmCloser> algorithm(raw_algorithm);

    const ULONG object_size =
        get_property(algorithm.get(), BCRYPT_OBJECT_LENGTH, "BCryptGetProperty");
    const ULONG hash_size =
        get_property(algorithm.get(), BCRYPT_HASH_LENGTH, "BCryptGetProperty");
    if (hash_size != 32) {
        throw std::runtime_error("Windows CNG returned an unexpected SHA-256 size.");
    }

    std::vector<UCHAR> hash_object(object_size);
    BCRYPT_HASH_HANDLE raw_hash = nullptr;
    check_ntstatus(
        "BCryptCreateHash",
        BCryptCreateHash(
            algorithm.get(),
            &raw_hash,
            hash_object.data(),
            object_size,
            nullptr,
            0,
            0));
    const std::unique_ptr<void, HashCloser> hash(raw_hash);

    std::array<char, 64 * 1024> buffer{};
    while (input.read(buffer.data(), static_cast<std::streamsize>(buffer.size())) ||
           input.gcount() > 0) {
        const auto bytes_read = static_cast<ULONG>(input.gcount());
        check_ntstatus(
            "BCryptHashData",
            BCryptHashData(
                hash.get(),
                reinterpret_cast<PUCHAR>(buffer.data()),
                bytes_read,
                0));
    }
    if (input.bad()) {
        throw std::runtime_error("Error while reading file for hashing: " + path.string());
    }

    std::array<UCHAR, 32> digest{};
    check_ntstatus(
        "BCryptFinishHash",
        BCryptFinishHash(hash.get(), digest.data(), static_cast<ULONG>(digest.size()), 0));

    std::ostringstream result;
    result << std::hex << std::setfill('0');
    for (const UCHAR byte : digest) {
        result << std::setw(2) << static_cast<unsigned int>(byte);
    }
    return result.str();
}

}
