package com.bommer.stacktower.game

/** Un thème visuel achetable dans la boutique avec des pièces. */
data class GameTheme(
    val id: String,
    val name: String,
    val price: Int,
    val bgTop: Long,
    val bgBottom: Long,
    val hueStart: Float,
    val hueStep: Float,
    val saturation: Float,
    val value: Float,
)

object Themes {
    val all = listOf(
        GameTheme("neon", "Néon", 0, 0xFF1B1D3A, 0xFF3A1F5C, 170f, 7f, 0.55f, 0.95f),
        GameTheme("sunset", "Coucher de soleil", 150, 0xFF2D1B3D, 0xFFB5485C, 10f, 5f, 0.6f, 1f),
        GameTheme("ocean", "Océan", 300, 0xFF06283D, 0xFF1363DF, 180f, 3f, 0.6f, 0.95f),
        GameTheme("forest", "Forêt", 500, 0xFF0B2B26, 0xFF235347, 85f, 4f, 0.5f, 0.85f),
        GameTheme("candy", "Bonbon", 800, 0xFFFFD6E8, 0xFFB8E1FF, 300f, 9f, 0.4f, 1f),
        GameTheme("gold", "Or royal", 1500, 0xFF1A1A1A, 0xFF3B2F0B, 40f, 2f, 0.75f, 1f),
        GameTheme("mono", "Minimal", 2500, 0xFFF2F2F2, 0xFFD9D9D9, 0f, 0f, 0f, 0.25f),
    )

    fun byId(id: String): GameTheme = all.firstOrNull { it.id == id } ?: all.first()
}
