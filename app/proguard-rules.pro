# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.uallsi.medaboutyou.** {
    kotlinx.serialization.KSerializer serializer(...);
}
