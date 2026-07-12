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

SoundTouch *checkedSt(JNIEnv *env, jlong handle) {
    SoundTouch *instance = st(handle);
    if (instance == nullptr) {
        throwRuntime(env, "SoundTouch native handle is null");
    }
    return instance;
}

void throwUnknown(JNIEnv *env) {
    throwRuntime(env, "Unknown native SoundTouch error");
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_io_github_neboyang_voicechanger_SoundTouch_nativeNew(JNIEnv *env, jclass) {
    try {
        return reinterpret_cast<jlong>(new SoundTouch());
    } catch (const std::exception &e) {
        throwRuntime(env, e.what());
    } catch (...) {
        throwUnknown(env);
    }
    return 0;
}

JNIEXPORT void JNICALL
Java_io_github_neboyang_voicechanger_SoundTouch_nativeDelete(JNIEnv *, jclass, jlong handle) {
    delete st(handle);
}

JNIEXPORT void JNICALL
Java_io_github_neboyang_voicechanger_SoundTouch_nativeSetSampleRate(JNIEnv *env, jclass, jlong handle, jint sampleRate) {
    try {
        SoundTouch *instance = checkedSt(env, handle);
        if (instance != nullptr) instance->setSampleRate(static_cast<uint>(sampleRate));
    } catch (const std::exception &e) {
        throwRuntime(env, e.what());
    } catch (...) {
        throwUnknown(env);
    }
}

JNIEXPORT void JNICALL
Java_io_github_neboyang_voicechanger_SoundTouch_nativeSetChannels(JNIEnv *env, jclass, jlong handle, jint channels) {
    try {
        SoundTouch *instance = checkedSt(env, handle);
        if (instance != nullptr) instance->setChannels(static_cast<uint>(channels));
    } catch (const std::exception &e) {
        throwRuntime(env, e.what());
    } catch (...) {
        throwUnknown(env);
    }
}

JNIEXPORT void JNICALL
Java_io_github_neboyang_voicechanger_SoundTouch_nativeSetTempo(JNIEnv *env, jclass, jlong handle, jfloat tempo) {
    try {
        SoundTouch *instance = checkedSt(env, handle);
        if (instance != nullptr) instance->setTempo(tempo);
    } catch (const std::exception &e) {
        throwRuntime(env, e.what());
    } catch (...) {
        throwUnknown(env);
    }
}

JNIEXPORT void JNICALL
Java_io_github_neboyang_voicechanger_SoundTouch_nativeSetRate(JNIEnv *env, jclass, jlong handle, jfloat rate) {
    try {
        SoundTouch *instance = checkedSt(env, handle);
        if (instance != nullptr) instance->setRate(rate);
    } catch (const std::exception &e) {
        throwRuntime(env, e.what());
    } catch (...) {
        throwUnknown(env);
    }
}

JNIEXPORT void JNICALL
Java_io_github_neboyang_voicechanger_SoundTouch_nativeSetPitchSemiTones(JNIEnv *env, jclass, jlong handle, jfloat semiTones) {
    try {
        SoundTouch *instance = checkedSt(env, handle);
        if (instance != nullptr) instance->setPitchSemiTones(semiTones);
    } catch (const std::exception &e) {
        throwRuntime(env, e.what());
    } catch (...) {
        throwUnknown(env);
    }
}

JNIEXPORT void JNICALL
Java_io_github_neboyang_voicechanger_SoundTouch_nativePutSamples(
        JNIEnv *env, jclass, jlong handle, jshortArray samples, jint numFrames) {
    SoundTouch *instance = checkedSt(env, handle);
    if (instance == nullptr || env->ExceptionCheck()) return;
    jshort *buf = env->GetShortArrayElements(samples, nullptr);
    if (buf == nullptr) return; // OutOfMemoryError 已抛出
    try {
        instance->putSamples(reinterpret_cast<soundtouch::SAMPLETYPE *>(buf),
                             static_cast<uint>(numFrames));
    } catch (const std::exception &e) {
        env->ReleaseShortArrayElements(samples, buf, JNI_ABORT);
        throwRuntime(env, e.what());
        return;
    } catch (...) {
        env->ReleaseShortArrayElements(samples, buf, JNI_ABORT);
        throwUnknown(env);
        return;
    }
    env->ReleaseShortArrayElements(samples, buf, JNI_ABORT);
}

JNIEXPORT jint JNICALL
Java_io_github_neboyang_voicechanger_SoundTouch_nativeReceiveSamples(
        JNIEnv *env, jclass, jlong handle, jshortArray out, jint maxFrames) {
    SoundTouch *instance = checkedSt(env, handle);
    if (instance == nullptr || env->ExceptionCheck()) return 0;
    jshort *buf = env->GetShortArrayElements(out, nullptr);
    if (buf == nullptr) return 0;
    uint received = 0;
    try {
        received = instance->receiveSamples(
                reinterpret_cast<soundtouch::SAMPLETYPE *>(buf),
                static_cast<uint>(maxFrames));
    } catch (const std::exception &e) {
        env->ReleaseShortArrayElements(out, buf, JNI_ABORT);
        throwRuntime(env, e.what());
        return 0;
    } catch (...) {
        env->ReleaseShortArrayElements(out, buf, JNI_ABORT);
        throwUnknown(env);
        return 0;
    }
    env->ReleaseShortArrayElements(out, buf, 0);
    return static_cast<jint>(received);
}

JNIEXPORT void JNICALL
Java_io_github_neboyang_voicechanger_SoundTouch_nativeFlush(JNIEnv *env, jclass, jlong handle) {
    SoundTouch *instance = checkedSt(env, handle);
    if (instance == nullptr || env->ExceptionCheck()) return;
    try {
        instance->flush();
    } catch (const std::exception &e) {
        throwRuntime(env, e.what());
    } catch (...) {
        throwUnknown(env);
    }
}

JNIEXPORT jint JNICALL
Java_io_github_neboyang_voicechanger_SoundTouch_nativeNumSamples(JNIEnv *env, jclass, jlong handle) {
    SoundTouch *instance = checkedSt(env, handle);
    if (instance == nullptr || env->ExceptionCheck()) return 0;
    try {
        return static_cast<jint>(instance->numSamples());
    } catch (const std::exception &e) {
        throwRuntime(env, e.what());
    } catch (...) {
        throwUnknown(env);
    }
    return 0;
}

JNIEXPORT jstring JNICALL
Java_io_github_neboyang_voicechanger_SoundTouch_nativeGetVersion(JNIEnv *env, jclass) {
    return env->NewStringUTF(SoundTouch::getVersionString());
}

} // extern "C"
