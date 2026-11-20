cmake_minimum_required(VERSION 3.22.1)
project(pianova_audio LANGUAGES CXX)

find_package(oboe REQUIRED CONFIG)

add_library(pianova_audio SHARED
        native-lib.cpp
        AudioEngine.cpp)

target_compile_features(pianova_audio PRIVATE cxx_std_17)

target_compile_options(pianova_audio PRIVATE
        -Wall
        -Wextra
        -Werror
        -fvisibility=hidden)

target_include_directories(pianova_audio PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR})

target_link_libraries(pianova_audio PRIVATE
        android
        oboe::oboe
        log)
