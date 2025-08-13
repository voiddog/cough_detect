cmake_minimum_required(VERSION 3.22.1)

project("coughdetect")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find required packages
find_library(log-lib log)
find_library(android-lib android)

# TensorFlow Lite配置 - 使用find_library方式
set(TENSORFLOW_LITE_AVAILABLE ON)
add_definitions(-DTENSORFLOW_LITE_AVAILABLE)

# 查找TensorFlow Lite库
find_library(tensorflowlite-lib tensorflowlite)
if(tensorflowlite-lib)
    message(STATUS "Found TensorFlow Lite library: ${tensorflowlite-lib}")
else()
    message(WARNING "TensorFlow Lite library not found, using rule-based detection only")
    set(TENSORFLOW_LITE_AVAILABLE OFF)
    remove_definitions(-DTENSORFLOW_LITE_AVAILABLE)
endif()

# Find Oboe using prefab
find_package(oboe REQUIRED CONFIG)
message(STATUS "Found Oboe library via prefab")

# Include directories
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)

# Source files
set(SOURCES
    cough_detect_engine.cpp
    audio_recorder.cpp
    tensorflow_wrapper.cpp
    jni_interface.cpp
)

# Create shared library
add_library(coughdetect SHARED ${SOURCES})

# Link libraries
target_link_libraries(coughdetect
    ${log-lib}
    ${android-lib}
    oboe::oboe
)

# 条件链接TensorFlow Lite
if(tensorflowlite-lib)
    target_link_libraries(coughdetect ${tensorflowlite-lib})
endif()

# Compiler flags
target_compile_options(coughdetect PRIVATE
    -Wall
    -Wextra
    -O2
    -fPIC
)

# Include directories for target
target_include_directories(coughdetect PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
) 