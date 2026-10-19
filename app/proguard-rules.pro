# Keep crash/stack trace info useful in release builds.
-keepattributes SourceFile,LineNumberTable

# Strip verbose/debug logging from release builds (privacy: no session-event logs in prod).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# Room entities are accessed via reflection-free generated code; nothing extra needed.
# Compose is kept automatically by AGP's default rules.
