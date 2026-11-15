cmake_minimum_required(VERSION 3.22.1)
project("monolith")

include(FetchContent)
find_package(Threads REQUIRED)

# 1. جلب llama.cpp بأحدث إصدار لضمان وجود mtmd
FetchContent_Declare(
    llama
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG        master
)
FetchContent_MakeAvailable(llama)

add_library(monolith SHARED ai_chat.cpp vision_processor.cpp)

target_include_directories(monolith PRIVATE
    ${llama_SOURCE_DIR}
    ${llama_SOURCE_DIR}/include
    ${llama_SOURCE_DIR}/common
    ${llama_SOURCE_DIR}/tools/mtmd
)

# 2. ربط مكتبات الرؤية والذكاء الاصطناعي
target_link_libraries(monolith
    mtmd
    llama
    ggml
    common
    android
    log
    Threads::Threads
)
