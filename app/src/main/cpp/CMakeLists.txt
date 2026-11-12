cmake_minimum_required(VERSION 3.22.1)
project("monolith")

include(FetchContent)

FetchContent_Declare(
    llama
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG        v0.9.0
)
FetchContent_MakeAvailable(llama)

FetchContent_GetProperties(llama)
if(NOT llama_POPULATED)
    FetchContent_Populate(llama)
endif()

add_library(monolith SHARED ai_chat.cpp)

target_include_directories(monolith PRIVATE
    ${llama_SOURCE_DIR}
    ${llama_SOURCE_DIR}/include
    ${llama_SOURCE_DIR}/common
)

target_link_libraries(monolith
    llama
    android
    log
)
