cmake_minimum_required(VERSION 3.22.1)
project(spidey_engine LANGUAGES CXX)

# libneedle.a and needle.h are the vendor engine (Cactus Compute) that runs spidey.cact. They are
# fetched, not committed: run tools/fetch_engine.py. The Gradle build only includes this project
# when engine/libneedle.a exists.
add_library(vendor_engine STATIC IMPORTED)
set_target_properties(vendor_engine PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/engine/libneedle.a)

add_library(spidey_engine SHARED spidey_jni.cpp)
target_include_directories(spidey_engine PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/engine)
target_link_libraries(spidey_engine vendor_engine log android)
