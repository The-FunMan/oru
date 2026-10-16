-dontobfuscate
-dontoptimize

-keep class app.morphe.manager.patcher.runtime.process.* { *; }
-keep class app.morphe.manager.plugin.** { *; }
-keep class app.morphe.patcher.** { *; }
-keep class com.android.tools.smali.** { *; }
-keep class kotlin.** { *; }
-keepnames class com.android.apksig.internal.** { *; }
-keepnames class org.xmlpull.** { *; }

# apksig builds its ASN.1 models reflectively, so the no-arg constructors and the annotated
# fields must survive shrinking or signing degrades to a fallback public key encoding
-keepclassmembers class com.android.apksig.internal.** {
    <init>();
    <fields>;
}

-dontwarn android.content.res.**
-dontwarn com.google.j2objc.annotations.*
-dontwarn java.awt.**
-dontwarn javax.**
-dontwarn org.slf4j.**
# PhaseProfiler reads GC and thread counts from the desktop management beans only when it runs on a JVM; Android has none of these classes
-dontwarn java.lang.management.**
