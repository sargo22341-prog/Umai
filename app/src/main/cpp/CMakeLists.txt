# Builds whisper.cpp for the CPU of the phone, and the JNI bridge the app calls
# it through (WhisperNative.kt). The sources are fetched by the Gradle task
# fetchWhisperSource, which runs before this configuration.
cmake_minimum_required(VERSION 3.22.1)
# Optimized in the debug build too: unoptimized, whisper.cpp takes minutes to
# hear 30 seconds, and the debug app could not be tried on a real video.
set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
project(umai_whisper C CXX)

if(NOT EXISTS "${WHISPER_SOURCE_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "whisper.cpp sources missing in '${WHISPER_SOURCE_DIR}': run the Gradle task fetchWhisperSource")
endif()

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(GGML_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
# ggml's own thread pool: OpenMP would add a runtime library to ship.
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
# Every core of a phone that runs Android 17 has these, down to the Cortex-A55 of a Tensor G1.
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)

add_subdirectory(${WHISPER_SOURCE_DIR} whisper EXCLUDE_FROM_ALL)

add_library(umai_whisper SHARED whisper_jni.cpp)
# Every warning on the bridge written here, the stricter ones of the NASA rules for C
# included; whisper.cpp keeps its own settings.
target_compile_options(umai_whisper PRIVATE
    -Wall -Wextra -Wpedantic -Werror
    -Wconversion -Wsign-conversion -Wshadow -Wold-style-cast -Wcast-qual
    -Wnull-dereference -Wformat=2 -Wundef -Wunused -Wimplicit-fallthrough)
target_link_libraries(umai_whisper PRIVATE whisper log)
target_link_options(umai_whisper PRIVATE -Wl,--exclude-libs,ALL -Wl,--gc-sections)
