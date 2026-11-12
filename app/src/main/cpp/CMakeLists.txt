cmake_minimum_required(VERSION 3.22.1)
project("monolith")

include(FetchContent)

FetchContent_Declare(
    llama
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG        b4697
)
FetchContent_MakeAvailable(llama)

add_library(monolith SHARED ai_chat.cpp)

target_link_libraries(monolith
    llama
    android
    log
)
