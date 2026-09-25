cmake_minimum_required(VERSION 3.22.1)

project(umai_llm C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama.cpp is fetched at a pinned release and checked against its hash, never
# vendored in the repository. Only the directories the library needs are
# extracted: the web UI under tools/ has paths too long for Windows.
set(LLAMA_TAG b11179)
set(LLAMA_SHA256 f0e5204dedac090fdf93bf46a4c2e76dcf9b721839f2e0d5c2bee4b4d52193d2)

if(NOT DEFINED UMAI_LLAMA_CACHE)
    set(UMAI_LLAMA_CACHE ${CMAKE_BINARY_DIR}/llama-cache)
endif()
set(LLAMA_ARCHIVE ${UMAI_LLAMA_CACHE}/llama.cpp-${LLAMA_TAG}.tar.gz)
set(LLAMA_SRC ${UMAI_LLAMA_CACHE}/llama.cpp-${LLAMA_TAG})

if(NOT EXISTS ${LLAMA_SRC}/CMakeLists.txt)
    file(DOWNLOAD
        https://github.com/ggml-org/llama.cpp/archive/refs/tags/${LLAMA_TAG}.tar.gz
        ${LLAMA_ARCHIVE}
        EXPECTED_HASH SHA256=${LLAMA_SHA256}
        SHOW_PROGRESS)
    file(ARCHIVE_EXTRACT
        INPUT ${LLAMA_ARCHIVE}
        DESTINATION ${UMAI_LLAMA_CACHE}
        PATTERNS
            llama.cpp-${LLAMA_TAG}/CMakeLists.txt
            llama.cpp-${LLAMA_TAG}/LICENSE
            llama.cpp-${LLAMA_TAG}/cmake
            llama.cpp-${LLAMA_TAG}/common
            llama.cpp-${LLAMA_TAG}/ggml
            llama.cpp-${LLAMA_TAG}/include
            llama.cpp-${LLAMA_TAG}/licenses
            llama.cpp-${LLAMA_TAG}/src
            llama.cpp-${LLAMA_TAG}/vendor)
    file(REMOVE ${LLAMA_ARCHIVE})
endif()

# The CPU backend is built once per instruction set, from plain ARMv8.0 to
# ARMv9.2, and the best one for the phone is loaded at run time: i8mm and SVE2
# on a recent Tensor, dot products only on an older one.
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP ON CACHE BOOL "" FORCE)
set(LLAMA_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_SUBPROCESS OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_SRC} llama-build)

add_library(umai_llm SHARED llm_bridge.cpp)

target_include_directories(umai_llm PRIVATE
    ${LLAMA_SRC}/common
    ${LLAMA_SRC}/include
    ${LLAMA_SRC}/ggml/include)

target_link_libraries(umai_llm llama llama-common android log)
