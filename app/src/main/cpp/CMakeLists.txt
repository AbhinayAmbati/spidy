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

# --- Hardening ---------------------------------------------------------------------------
# Only the JNI entry points (marked JNIEXPORT in spidey_jni.cpp) are exported; every other
# symbol is hidden, which also shrinks the dynamic symbol table.
set_target_properties(spidey_engine PROPERTIES
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON)

target_compile_options(spidey_engine PRIVATE
    -fstack-protector-strong
    -fvisibility=hidden
    -ffunction-sections
    -fdata-sections
    -Wall
    -Wformat
    -Wformat-security
    # Fortified libc calls (memcpy, snprintf, ...) abort on a detected overflow. They need an
    # optimised build, so debug (-O0) is left alone to avoid the compiler warning.
    $<$<NOT:$<CONFIG:Debug>>:-U_FORTIFY_SOURCE>
    $<$<NOT:$<CONFIG:Debug>>:-D_FORTIFY_SOURCE=2>)

target_link_options(spidey_engine PRIVATE
    # 16 KB page-size compatibility (Android 15+ devices with 16 KB pages): ELF segments must
    # be aligned to 16 KB. Verified on the built APK by tools/check_native_alignment.py.
    -Wl,-z,max-page-size=16384
    # Read-only relocations, resolve at load time, non-executable stack.
    -Wl,-z,relro
    -Wl,-z,now
    -Wl,-z,noexecstack
    # Drop unused sections and hide the symbols of the static vendor engine from the .so API.
    -Wl,--gc-sections
    -Wl,--exclude-libs,ALL)
