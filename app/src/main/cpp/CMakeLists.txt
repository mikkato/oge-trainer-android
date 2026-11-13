cmake_minimum_required(VERSION 3.22)
project(ogellm C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Собираем только движок: без тестов, примеров и сервера — APK не должен тащить лишнее.
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

add_subdirectory(llama.cpp EXCLUDE_FROM_ALL)

find_library(log-lib log)
add_library(ogellm SHARED llm_jni.cpp)

target_link_libraries(ogellm llama ${log-lib} android)

target_compile_options(ogellm PRIVATE -O3 -fvisibility=hidden)
