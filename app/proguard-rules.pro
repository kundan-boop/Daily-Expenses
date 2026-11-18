# ProGuard / R8 Rules for Daily Expense & Ledger

# Keep all Room entities, DAOs, migrations, and database classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.example.data.** { *; }
-keepclassmembers class * {
    @androidx.room.PrimaryKey *;
    @androidx.room.ColumnInfo *;
    @androidx.room.Index *;
    @androidx.room.ForeignKey *;
    @androidx.room.TypeConverters *;
}
-dontwarn androidx.room.paging.**

# Keep Moshi and JSON serialization classes
-keep class com.example.domain.** { *; }
-keep class com.example.utils.** { *; }
-keep class com.example.auth.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
    @com.squareup.moshi.JsonClass *;
}

# Keep Credential Manager & Google Identity
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }

# Keep Firebase Auth & Firestore
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.firebase.firestore.** { *; }
-keep class com.google.firebase.auth.** { *; }

# Keep Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Preserve line numbers for crash debugging
-keepattributes SourceFile,LineNumberTable
