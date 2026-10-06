cmake_minimum_required(VERSION 3.16)
project(SentinelAV VERSION 0.1.0 LANGUAGES CXX)

find_package(Threads REQUIRED)
include(CTest)

if(NOT WIN32)
    message(FATAL_ERROR "Sentinel AV Phase 1 currently supports Windows only.")
endif()

add_executable(sentinel-av
    src/main.cpp
    src/config.cpp
    src/behavior.cpp
    src/quick_scan.cpp
    src/hash.cpp
    src/logging.cpp
    src/signatures.cpp
    src/analysis.cpp
    src/security.cpp
    src/scanner.cpp
    src/watcher.cpp
    src/process_monitor.cpp
)

target_include_directories(sentinel-av PRIVATE include)
target_compile_features(sentinel-av PRIVATE cxx_std_17)
target_link_libraries(sentinel-av PRIVATE bcrypt advapi32 shell32 ole32 Threads::Threads)

if(MSVC)
    target_compile_options(sentinel-av PRIVATE /W4 /permissive-)
else()
    target_compile_options(sentinel-av PRIVATE -Wall -Wextra -Wpedantic)
endif()

if(BUILD_TESTING)
    add_executable(sentinel-core-tests
        tests/core_tests.cpp
        src/behavior.cpp
        src/quick_scan.cpp
        src/config.cpp
        src/hash.cpp
        src/logging.cpp
        src/signatures.cpp
        src/analysis.cpp
        src/security.cpp
        src/scanner.cpp
    )
    target_include_directories(sentinel-core-tests PRIVATE include)
    target_compile_features(sentinel-core-tests PRIVATE cxx_std_17)
    target_link_libraries(sentinel-core-tests PRIVATE bcrypt advapi32 shell32 ole32 Threads::Threads)

    if(MSVC)
        target_compile_options(sentinel-core-tests PRIVATE /W4 /permissive-)
    else()
        target_compile_options(sentinel-core-tests PRIVATE -Wall -Wextra -Wpedantic)
    endif()

    add_test(NAME sentinel-core-tests COMMAND sentinel-core-tests)
endif()
