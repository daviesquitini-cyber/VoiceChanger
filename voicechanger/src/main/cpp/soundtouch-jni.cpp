/*
 * SoundTouch JNI 绑定，对应 Kotlin 类 io.github.neboyang.voicechanger.SoundTouch。
 *
 * 以 SOUNDTOUCH_INTEGER_SAMPLES 编译，SAMPLETYPE == short，
 * Java 层的 short[] PCM 可直接透传，无需 float 转换。
 */
#include <jni.h>
#include <stdexcept>

#include "SoundTouch.h"

using soundtouch::SoundTouch;

namespace {

inline SoundTouch *st(jlong handle) {
    return reinterpret_cast<SoundTouch *>(handle);
}

void throwRuntime(JNIEnv *env, const char *msg) {
    jclass cls = env->FindClass("java/lang/RuntimeException");
    if (cls != nullptr) {
        env->ThrowNew(cls, msg);
    }
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_io_github_neboyang_voicechanger_SoundTouch_nativeNew(JNIEnv *, jclass) {
    return reinterpret_cast<jlong>(new SoundTouch());
}

JNIEXPORT void JNICALL
Java_io_github_neboyang_voicechanger_SoundTouch_nativeDelete(JNIEnv *, jclass, jlong handle) {
    delete st(handle);
}

JNIEXPORT void JNICALL
Java_io_github_neboyang_voicechanger_SoundTouch_nativeSetSampleRate(JNIEnv *, jclass, jlong handle, jint sampleRate) {
    st(handle)->setSampleRate(static_cast<uint>(sampleRate));
}

JNIEXPORT void JNICALL
Java_io_github_neboyang_voicechanger_SoundTouch_nativeSetChannels(JNIEnv *, jclass, jlong handle, jint channels) {
    st(handle)->setChannels(static_cast<uint>(channels));
}

JNIEXPORT void JNICALL
Java_io_github_neboyang_voicechanger_SoundTouch_nativeSetTempo(JNIEnv *, jclass, jlong handle, jfloat tempo) {
    st(handle)->setTempo(tempo);
}

JNIEXPORT void JNICALL
Java_io_github_neboyang_voicechanger_SoundTouch_nativeSetRate(JNIEnv *, jclass, jlong handle, jfloat rate) {
    st(handle)->setRate(rate);
}

JNIEXPORT void JNICALL
Java_io_github_neboyang_voicechanger_SoundTouch_nativeSetPitchSemiTones(JNIEnv *, jclass, jlong handle, jfloat semiTones) {
    st(handle)->setPitchSemiTones(semiTones);
}

JNIEXPORT void JNICALL
Java_io_github_neboyang_voicechanger_SoundTouch_nativePutSamples(
        JNIEnv *env, jclass, jlong handle, jshortArray samples, jint numFrames) {
    jshort *buf = env->GetShortArrayElements(samples, nullptr);
    if (buf == nullptr) return; // OutOfMemoryError 已抛出
    try {
        st(handle)->putSamples(reinterpret_cast<soundtouch::SAMPLETYPE *>(buf),
                               static_cast<uint>(numFrames));
    } catch (const std::exception &e) {
        env->ReleaseShortArrayElements(samples, buf, JNI_ABORT);
        throwRuntime(env, e.what());
        return;
    }
    env->ReleaseShortArrayElements(samples, buf, JNI_ABORT);
}

JNIEXPORT jint JNICALL
Java_io_github_neboyang_voicechanger_SoundTouch_nativeReceiveSamples(
        JNIEnv *env, jclass, jlong handle, jshortArray out, jint maxFrames) {
    jshort *buf = env->GetShortArrayElements(out, nullptr);
    if (buf == nullptr) return 0;
    uint received = 0;
    try {
        received = st(handle)->receiveSamples(
                reinterpret_cast<soundtouch::SAMPLETYPE *>(buf),
                static_cast<uint>(maxFrames));
    } catch (const std::exception &e) {
        env->ReleaseShortArrayElements(out, buf, JNI_ABORT);
        throwRuntime(env, e.what());
        return 0;
    }
    env->ReleaseShortArrayElements(out, buf, 0);
    return static_cast<jint>(received);
}

JNIEXPORT void JNICALL
Java_io_github_neboyang_voicechanger_SoundTouch_nativeFlush(JNIEnv *env, jclass, jlong handle) {
    try {
        st(handle)->flush();
    } catch (const std::exception &e) {
        throwRuntime(env, e.what());
    }
}

JNIEXPORT jint JNICALL
Java_io_github_neboyang_voicechanger_SoundTouch_nativeNumSamples(JNIEnv *, jclass, jlong handle) {
    return static_cast<jint>(st(handle)->numSamples());
}

JNIEXPORT jstring JNICALL
Java_io_github_neboyang_voicechanger_SoundTouch_nativeGetVersion(JNIEnv *env, jclass) {
    return env->NewStringUTF(SoundTouch::getVersionString());
}

} // extern "C"
