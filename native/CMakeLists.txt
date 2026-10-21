cmake_minimum_required(VERSION 3.24)
project(music_ranker LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

find_package(JNI REQUIRED)

add_library(music_ranker SHARED
    src/feature_matrix.cpp
    src/ranker.cpp
    src/jni_bridge.cpp
)

target_include_directories(music_ranker PRIVATE
    include
    ${JNI_INCLUDE_DIRS}
)

target_compile_options(music_ranker PRIVATE
    $<$<CXX_COMPILER_ID:GNU,Clang>:-O3 -Wall -Wextra -Wpedantic>
)

