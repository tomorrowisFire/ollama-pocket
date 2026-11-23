# Android NDK toolchain wrapper. SPIRV-HeadersConfig.cmake is installed in the
# Ubuntu host CMake prefix, not in the Android sysroot.
if(NOT DEFINED ENV{ANDROID_NDK_HOME})
  message(FATAL_ERROR "ANDROID_NDK_HOME must be defined")
endif()
include("$ENV{ANDROID_NDK_HOME}/build/cmake/android.toolchain.cmake")
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE BOTH)
