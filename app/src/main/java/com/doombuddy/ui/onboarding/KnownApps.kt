package com.doombuddy.ui.onboarding

/** Curated list of common doomscroll destinations (user can't add arbitrary packages in V1). */
object KnownApps {
    data class KnownApp(val label: String, val packages: List<String>)

    val list = listOf(
        KnownApp("Instagram", listOf("com.instagram.android", "com.instagram.lite")),
        KnownApp("YouTube", listOf("com.google.android.youtube", "com.google.android.youtube.tv")),
        KnownApp("TikTok", listOf("com.zhiliaoapp.musically", "com.zhiliaoapp.musically.go")),
        KnownApp("Reddit", listOf("com.reddit.frontpage")),
        KnownApp("X (Twitter)", listOf("com.twitter.android", "com.twitter.android.lite")),
        KnownApp("Facebook", listOf("com.facebook.katana", "com.facebook.lite")),
        KnownApp("Snapchat", listOf("com.snapchat.android")),
        KnownApp("Chrome", listOf("com.android.chrome")),
    )
}
