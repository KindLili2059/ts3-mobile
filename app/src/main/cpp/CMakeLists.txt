cmake_minimum_required(VERSION 3.22.1)
project(ts3opus_jni LANGUAGES C CXX)

include(opus-source.cmake)

add_library(ts3opus_jni SHARED opus_jni.cpp)

target_compile_features(ts3opus_jni PRIVATE cxx_std_17)
target_link_libraries(ts3opus_jni PRIVATE Opus::opus)
