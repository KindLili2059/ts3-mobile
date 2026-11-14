# Add project specific ProGuard rules here.
# Keep JNI classes and native callback methods referenced from C++.

-keep class com.example.ts3.nativebridge.** { *; }

# Keep members accessed via JNI reflection
-keepclasseswithmembernames class * {
    native <methods>;
}
