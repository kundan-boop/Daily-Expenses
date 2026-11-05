# ProGuard / R8 Rules for Daily Expense & Ledger

# Keep all Room entities, DAOs, and database classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.example.data.** { *; }
-keepclassmembers class * {
    @androidx.room.PrimaryKey *;
    @androidx.room.ColumnInfo *;
}
-dontwarn androidx.room.paging.**

# Keep Moshi and JSON serialization classes
-keep class com.example.domain.** { *; }
-keep class com.example.utils.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
    @com.squareup.moshi.JsonClass *;
}

# Preserve line numbers for crash debugging
-keepattributes SourceFile,LineNumberTable
