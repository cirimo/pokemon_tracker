# kotlinx.serialization keeps the generated serializers for @Serializable types.
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room entities are constructed reflectively by generated code.
-keep class net.pokedex.core.data.**Entity { <init>(); }

# The backup format is the app-loss insurance. Obfuscating its field names would make a
# recovered file unreadable by a future build, so the DTOs keep their names.
-keep class net.pokedex.core.model.backup.** { *; }
