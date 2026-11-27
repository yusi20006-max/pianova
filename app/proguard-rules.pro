# Preserve useful source locations in crash reports.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# JNI methods are looked up by their exact native symbol names from C++.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Kotlin serialization metadata is used by generated serializers.
-keepclassmembers class **$$serializer {
    *** descriptor;
    *** childSerializers(...);
    *** typeParametersSerializers(...);
}
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
