# Pianova — native audio library (Phase 0 smoke test).
#
# Oboe is consumed through AGP's Prefab integration: app/build.gradle.kts sets
# `buildFeatures.prefab = true` and adds `com.google.oboe:oboe`, which publishes
# a prefab package named "oboe". AGP hands the prefab include/library paths to
# CMake, so `find_package(oboe REQUIRED CONFIG)` resolves here.

cmake_minimum_required(VERSION 3.22.1)
project(pianova_audio LANGUAGES CXX)

find_package(oboe REQUIRED CONFIG)

add_library(pianova_audio SHARED
        native-lib.cpp)

target_compile_features(pianova_audio PRIVATE cxx_std_17)

target_compile_options(pianova_audio PRIVATE
        -Wall
        -Wextra
        -fvisibility=hidden)

target_include_directories(pianova_audio PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR})

target_link_libraries(pianova_audio PRIVATE
        oboe::oboe
        log)
