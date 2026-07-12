cmake_minimum_required(VERSION 3.22.1)
project(soundtouch CXX)

set(ST_DIR ${CMAKE_CURRENT_SOURCE_DIR}/soundtouch)

add_library(soundtouch SHARED
        soundtouch-jni.cpp
        ${ST_DIR}/SoundTouch/AAFilter.cpp
        ${ST_DIR}/SoundTouch/FIFOSampleBuffer.cpp
        ${ST_DIR}/SoundTouch/FIRFilter.cpp
        ${ST_DIR}/SoundTouch/InterpolateCubic.cpp
        ${ST_DIR}/SoundTouch/InterpolateLinear.cpp
        ${ST_DIR}/SoundTouch/InterpolateShannon.cpp
        ${ST_DIR}/SoundTouch/RateTransposer.cpp
        ${ST_DIR}/SoundTouch/SoundTouch.cpp
        ${ST_DIR}/SoundTouch/TDStretch.cpp
        ${ST_DIR}/SoundTouch/cpu_detect_x86.cpp
        ${ST_DIR}/SoundTouch/mmx_optimized.cpp
        ${ST_DIR}/SoundTouch/sse_optimized.cpp)

target_include_directories(soundtouch PRIVATE ${ST_DIR}/include)

# 录音链路是 16-bit PCM，整型采样可让 JNI 直接透传 short[]，避免 float 往返转换
target_compile_definitions(soundtouch PRIVATE SOUNDTOUCH_INTEGER_SAMPLES=1)
target_compile_options(soundtouch PRIVATE -O3 -fexceptions -fvisibility=hidden)

target_link_libraries(soundtouch log)
