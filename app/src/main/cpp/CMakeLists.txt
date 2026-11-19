cmake_minimum_required(VERSION 3.18)
project(after_motion_native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# The native part is ALWAYS built as Release, also inside a Gradle "debug" build.
set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)

# After Motion engine (decoder pool etc.).
add_subdirectory(engine am_engine_build)
