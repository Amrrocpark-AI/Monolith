
cmake_minimum_required(VERSION 3.22.1)

project(monolith LANGUAGES C CXX)

# ==========================================
# Global Build Configuration
# ==========================================

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(CMAKE_POSITION_INDEPENDENT_CODE ON)

include(FetchContent)

find_package(Threads REQUIRED)

# ==========================================
# Android / Native Build Options
# ==========================================

# Build dependencies statically and package them
# inside the monolith shared library.

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# ==========================================
# llama.cpp Configuration
# ==========================================

set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_UI OFF CACHE BOOL "" FORCE)

# Enable the official standalone MTMD library.

set(LLAMA_BUILD_MTMD ON CACHE BOOL "" FORCE)

# Disable subprocess-dependent features.

set(LLAMA_SUBPROCESS OFF CACHE BOOL "" FORCE)

# Avoid optional video / FFmpeg integration.

set(MTMD_VIDEO OFF CACHE BOOL "" FORCE)

# Disable unnecessary optional dependencies.

set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)

# ==========================================
# GGML Configuration
# ==========================================

set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

# ==========================================
# Fetch Pinned llama.cpp Version
# ==========================================

FetchContent_Declare(
    llama

    GIT_REPOSITORY
        https://github.com/ggml-org/llama.cpp.git

    GIT_TAG
        b54752df40e4d8e53d34ef5a598336b4e4871d39

    GIT_SHALLOW FALSE
)

FetchContent_MakeAvailable(llama)

# ==========================================
# Verify Required Targets
# ==========================================

if(NOT TARGET llama)
    message(FATAL_ERROR "llama target was not created.")
endif()

if(NOT TARGET ggml)
    message(FATAL_ERROR "ggml target was not created.")
endif()

if(NOT TARGET mtmd)
    message(FATAL_ERROR "mtmd target was not created.")
endif()

# ==========================================
# Monolith Native Library
# ==========================================

add_library(
    monolith
    SHARED

    ai_chat.cpp
    vision_processor.cpp
)

# ==========================================
# Include Directories
# ==========================================

target_include_directories(
    monolith

    PRIVATE

    ${llama_SOURCE_DIR}/include
    ${llama_SOURCE_DIR}/tools/mtmd
)

# ==========================================
# C++ Standard
# ==========================================

target_compile_features(
    monolith

    PRIVATE

    cxx_std_17
)

# ==========================================
# Link Native Dependencies
# ==========================================

target_link_libraries(
    monolith

    PRIVATE

    mtmd
    llama
    ggml

    Threads::Threads

    android
    log
)

# ==========================================
# Build Diagnostics
# ==========================================

message(STATUS "================================")
message(STATUS "Monolith Native Engine")
message(STATUS "llama.cpp: b54752df4")
message(STATUS "MTMD: ENABLED")
message(STATUS "Text Engine: ENABLED")
message(STATUS "Vision Engine: ENABLED")
message(STATUS "OpenMP: DISABLED")
message(STATUS "Video Support: DISABLED")
message(STATUS "================================")
