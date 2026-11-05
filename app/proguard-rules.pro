# ShowFee – rules for shrinking and obfuscating the app (R8), as Google Play asks.

# Keep line numbers in crash reports (the mapping file is uploaded with each release)
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Microsoft sign-in (MSAL) reads its settings and tokens by name, so its classes must keep their names
-keep class com.microsoft.identity.** { *; }
-keep class com.microsoft.device.display.** { *; }
-keep class com.nimbusds.** { *; }
-keep class net.minidev.** { *; }
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keepclassmembers,allowobfuscation class * { @com.google.gson.annotations.SerializedName <fields>; }
-dontwarn com.microsoft.identity.**
-dontwarn com.microsoft.device.display.**
-dontwarn com.nimbusds.**
-dontwarn net.minidev.**
-dontwarn com.google.crypto.tink.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn io.opentelemetry.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn lombok.**
-dontwarn org.slf4j.**

# Google sign-in, Play billing and in-app updates ship their own rules; nothing extra needed.
