cmake_minimum_required(VERSION 3.22.1)
project(spidey_needle LANGUAGES CXX)

# libneedle.a and needle.h are fetched, not committed: run tools/fetch_needle_engine.py.
# The Gradle build only includes this project when engine/libneedle.a exists.
add_library(needle STATIC IMPORTED)
set_target_properties(needle PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/engine/libneedle.a)

add_library(spidey_needle SHARED needle_jni.cpp)
target_include_directories(spidey_needle PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/engine)
target_link_libraries(spidey_needle needle log android)
