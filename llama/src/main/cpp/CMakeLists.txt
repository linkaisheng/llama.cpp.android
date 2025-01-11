# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("llama-android")

# Set path to llama.cpp root directory
set(LLAMA_SOURCE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party)

# Load local llama.cpp
add_subdirectory(${LLAMA_SOURCE_DIR} build-llama)

# Main Android library
add_library(${CMAKE_PROJECT_NAME} SHARED
    llama-android.cpp
)

# Include directories
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${LLAMA_SOURCE_DIR}
    ${LLAMA_SOURCE_DIR}/common
    ${LLAMA_SOURCE_DIR}/examples
    ${LLAMA_SOURCE_DIR}/include
)

# Link libraries
target_link_libraries(${CMAKE_PROJECT_NAME}
    llama
    common
    android
    log
)
