# ExifInterface usa reflection minima; manteniamo le classi per sicurezza.
-keep class androidx.exifinterface.** { *; }
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
