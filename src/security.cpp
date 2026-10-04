#include "sentinel/security.hpp"

#include "sentinel/logging.hpp"

#include <stdexcept>
#include <string>
#include <iostream>
#include <vector>

#ifdef _WIN32
#include <windows.h>
#include <aclapi.h>
#include <sddl.h>
#else
#error "Quarantine ACL hardening currently uses Windows security APIs."
#endif

namespace sentinel {
namespace {

class Handle {
public:
    explicit Handle(HANDLE value) : value_(value) {}
    ~Handle() {
        if (value_ != nullptr && value_ != INVALID_HANDLE_VALUE) {
            CloseHandle(value_);
        }
    }

    Handle(const Handle&) = delete;
    Handle& operator=(const Handle&) = delete;

    HANDLE get() const noexcept {
        return value_;
    }

private:
    HANDLE value_;
};

[[noreturn]] void throw_windows_error(
    const std::string& operation,
    DWORD error) {
    throw std::runtime_error(
        operation + " failed (Windows error " + std::to_string(error) + ").");
}

void apply_private_acl(
    const std::filesystem::path& path,
    Logger* logger,
    const std::string& event,
    const std::string& description) {
    HANDLE raw_token = nullptr;
    if (!OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &raw_token)) {
        throw_windows_error("OpenProcessToken", GetLastError());
    }
    Handle token(raw_token);

    DWORD token_size = 0;
    SetLastError(ERROR_SUCCESS);
    const BOOL size_query =
        GetTokenInformation(token.get(), TokenUser, nullptr, 0, &token_size);
    const DWORD size_query_error = GetLastError();
    if (size_query || size_query_error != ERROR_INSUFFICIENT_BUFFER ||
        token_size == 0) {
        throw_windows_error(
            "GetTokenInformation", size_query ? ERROR_INVALID_DATA : size_query_error);
    }
    std::vector<unsigned char> token_buffer(token_size);
    if (!GetTokenInformation(
            token.get(),
            TokenUser,
            token_buffer.data(),
            token_size,
            &token_size)) {
        throw_windows_error("GetTokenInformation", GetLastError());
    }
    const auto* token_user =
        reinterpret_cast<const TOKEN_USER*>(token_buffer.data());

    std::vector<unsigned char> system_sid(SECURITY_MAX_SID_SIZE);
    DWORD system_sid_size = static_cast<DWORD>(system_sid.size());
    if (!CreateWellKnownSid(
            WinLocalSystemSid, nullptr,
            system_sid.data(), &system_sid_size)) {
        throw_windows_error("CreateWellKnownSid(LocalSystem)", GetLastError());
    }
    std::vector<unsigned char> administrators_sid(SECURITY_MAX_SID_SIZE);
    DWORD administrators_sid_size =
        static_cast<DWORD>(administrators_sid.size());
    if (!CreateWellKnownSid(
            WinBuiltinAdministratorsSid, nullptr,
            administrators_sid.data(), &administrators_sid_size)) {
        throw_windows_error(
            "CreateWellKnownSid(Administrators)", GetLastError());
    }

    EXPLICIT_ACCESSW access[3]{};
    PSID trustees[3] = {
        token_user->User.Sid,
        system_sid.data(),
        administrators_sid.data()};
    for (std::size_t index = 0; index < 3; ++index) {
        access[index].grfAccessPermissions = FILE_ALL_ACCESS;
        access[index].grfAccessMode = SET_ACCESS;
        access[index].grfInheritance = NO_INHERITANCE;
        BuildTrusteeWithSidW(&access[index].Trustee, trustees[index]);
        access[index].Trustee.TrusteeType =
            index == 0 ? TRUSTEE_IS_USER : TRUSTEE_IS_WELL_KNOWN_GROUP;
    }

    PACL dacl = nullptr;
    const DWORD acl_error = SetEntriesInAclW(3, access, nullptr, &dacl);
    if (acl_error != ERROR_SUCCESS) {
        throw_windows_error("SetEntriesInAclW", acl_error);
    }

    std::wstring native_path = path.native();
    const DWORD security_error = SetNamedSecurityInfoW(
        native_path.data(),
        SE_FILE_OBJECT,
        DACL_SECURITY_INFORMATION |
            PROTECTED_DACL_SECURITY_INFORMATION |
            OWNER_SECURITY_INFORMATION,
        token_user->User.Sid,
        nullptr,
        dacl,
        nullptr);
    LocalFree(dacl);
    if (security_error != ERROR_SUCCESS) {
        throw_windows_error("SetNamedSecurityInfoW", security_error);
    }

    if (logger != nullptr) {
        logger->write(
            "info", event, description, path);
    }
}

}

void secure_quarantine_directory(
    const std::filesystem::path& directory,
    bool created,
    Logger* logger) {
    if (!created) {
        const std::string message =
            "Existing quarantine directory ACL was not changed; verify its permissions.";
        std::cerr << "[WARN] " << message << '\n';
        if (logger != nullptr) {
            logger->write("warning", "quarantine_acl_unverified", message, directory);
        }
        return;
    }
    apply_private_acl(
        directory,
        logger,
        "quarantine_directory_acl_hardened",
        "Restricted new quarantine directory access to the current user, "
        "LocalSystem, and local Administrators.");
}

void secure_quarantined_file(
    const std::filesystem::path& file,
    Logger* logger) {
    apply_private_acl(
        file,
        logger,
        "quarantined_file_acl_hardened",
        "Restricted quarantined file access to the current user, LocalSystem, "
        "and local Administrators.");
}

void secure_event_log_file(const std::filesystem::path& file) {
    apply_private_acl(
        file,
        nullptr,
        "event_log_acl_hardened",
        "Restricted event log access to the current user, LocalSystem, "
        "and local Administrators.");
}

}
