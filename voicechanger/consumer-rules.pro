# JNI 通过方法签名查找 native 方法，混淆后会导致 UnsatisfiedLinkError
-keepclasseswithmembernames class io.github.neboyang.voicechanger.SoundTouch {
    native <methods>;
}
