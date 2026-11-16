cmake_minimum_required(VERSION 3.22.1)
project("monolith")

include(FetchContent)
find_package(Threads REQUIRED)

# إجبار بناء أدوات llama.cpp والمكتبات المطلوبة
set(LLAMA_BUILD_TOOLS ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)

# 1. جلب llama.cpp بإصدار ثابت (Commit Hash) لتجنب كسر التوافقيات المستقبلية
FetchContent_Declare(
    llama
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG        b54752df4 
)
FetchContent_MakeAvailable(llama)

add_library(monolith SHARED ai_chat.cpp vision_processor.cpp)

target_include_directories(monolith PRIVATE
    ${llama_SOURCE_DIR}
    ${llama_SOURCE_DIR}/include
    ${llama_SOURCE_DIR}/common
    ${llama_SOURCE_DIR}/tools/mtmd
)

# 2. ربط المكتبات
target_link_libraries(monolith
    mtmd
    llama
    ggml
    common
    android
    log
    Threads::Threads
)
