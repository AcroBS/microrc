# ProGuard / R8 configuration for Google Play release builds (.aab / .apk)

# Preserve source file & line numbers for Google Play Console crash symbolication
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep all Firestore & Moshi data models (used via Firestore CustomClassMapper & Moshi codegen)
-keep class com.example.model.** { *; }

# Keep Moshi generated adapters
-keep class **JsonAdapter { *; }

# Keep Firebase Firestore & Auth reflection entrypoints
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}
