cmake_minimum_required(VERSION 3.22.1)
project("monolith")

include(FetchContent)
find_package(Threads REQUIRED)

# 1. تعطيل بناء أدوات سطر الأوامر
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

# 2. تفعيل بناء المكتبة المشتركة (common) التي نحتاجها
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)

# 3. جلب llama.cpp بإصدار ثابت (Commit Hash) لتجنب كسر التوافقيات
FetchContent_Declare(
    llama
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG        b54752df4 
)
FetchContent_MakeAvailable(llama)

# 4. إضافة مكتبة الرؤية (mtmd) يدوياً لأننا عطلنا بناء الأدوات
set(LLAMA_INSTALL_VERSION "0.0.0" CACHE STRING "" FORCE)
add_subdirectory(${llama_SOURCE_DIR}/tools/mtmd mtmd)

add_library(monolith SHARED ai_chat.cpp vision_processor.cpp)

target_include_directories(monolith PRIVATE
    ${llama_SOURCE_DIR}
    ${llama_SOURCE_DIR}/include
    ${llama_SOURCE_DIR}/common
    ${llama_SOURCE_DIR}/tools/mtmd
)

# 5. ربط المكتبات (تم تغيير common إلى llama-common)
target_link_libraries(monolith
    mtmd
    llama
    ggml
    llama-common
    android
    log
    Threads::Threads
)
