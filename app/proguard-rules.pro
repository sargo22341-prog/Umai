# kotlinx.serialization keeps generated serializers referenced only by reflection-free lookups.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    static **$* *;
}
-keepclasseswithmembers class **$$serializer {
    static **$$serializer INSTANCE;
    *** serializer(...);
}

# Retrofit service interfaces are resolved reflectively.
-keep,allowobfuscation interface retrofit2.** { *; }
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations

# OkHttp / Okio optional platform classes.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# llm_bridge.cpp calls back GenerationListener.onProgress by name, and binds the
# native methods of LlamaNative by their class and method names.
-keep interface org.opensources.umai.llm.data.GenerationListener { *; }
-keepclasseswithmembernames class org.opensources.umai.llm.data.LlamaNative {
    native <methods>;
}
