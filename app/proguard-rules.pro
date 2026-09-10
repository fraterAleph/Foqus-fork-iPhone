# Room / Kotlin serialization keep rules
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class app.foqos.android.** {
    *** Companion;
}
-keepclasseswithmembers class app.foqos.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Services declared in the manifest must survive shrinking
-keep class app.foqos.android.blocking.** { *; }
