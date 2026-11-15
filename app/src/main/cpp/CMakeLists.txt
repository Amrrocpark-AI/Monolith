cmake_minimum_required(VERSION 3.22.1)
project("monolith")

include(FetchContent)

# 1. تفعيل الرؤية الحاسوبية (libmtmd) والمسرّع العتادي (Vulkan)
set(LLAMA_BUILD_MTMD ON CACHE BOOL "" FORCE)
set(GGML_VULKAN ON CACHE BOOL "" FORCE)

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

add_library(monolith SHARED ai_chat.cpp vision_processor.cpp)

target_include_directories(monolith PRIVATE
    ${llama_SOURCE_DIR}
    ${llama_SOURCE_DIR}/include
    ${llama_SOURCE_DIR}/common
    ${llama_SOURCE_DIR}/tools/mtmd   # إضافة مسار libmtmd
)

# 2. ربط مكتبات الرؤية والذكاء الاصطناعي
target_link_libraries(monolith
    mtmd        # مكتبة الرؤية الحاسوبية
    llama       # المحرك النصي
    ggml        # العمليات الرياضية
    android
    log
)
