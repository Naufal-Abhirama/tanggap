# Add project specific ProGuard rules here.
# For more details, see http://developer.android.com/guide/developing/tools/proguard.html

-keepattributes SourceFile,LineNumberTable

-keep class com.google.ai.edge.** { *; }
-keep class org.tensorflow.** { *; }
-keep class id.tanggap.app.data.** { *; }
-keep class id.tanggap.app.inference.** { *; }
-keep class com.halilibo.** { *; }