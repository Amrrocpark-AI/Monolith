cmake_minimum_required(VERSION 3.22.1)
project("monolith")

# إضافة مجلد llama.cpp (الذي سنضيفه لاحقاً)
set(LLAMA_DIR ${CMAKE_SOURCE_DIR}/llama.cpp)

add_library(monolith SHARED
    ai_chat.cpp
)

target_include_directories(monolith PRIVATE
    ${LLAMA_DIR}
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}/common
)

find_library(log-lib log)

target_link_libraries(monolith
    ${log-lib}
    # سنربط مكتبة llama.cpp هنا لاحقاً
)
