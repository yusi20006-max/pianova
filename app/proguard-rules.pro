# Preserve source locations for useful crash reports.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# JNI methods are referenced from C++ JNI registration/symbol lookup.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keepclasseswithmembers class * {
    native <methods>;
}

# Kotlin Serialization: keep annotated models and generated serializers under R8.
-keep class **$$serializer { *; }
-keep @kotlinx.serialization.Serializable class ** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
