package com.bommer.stacktower.game

/** Palette de couleurs de la tour et du décor. */
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
    val lightBackground: Boolean = false,
)

/** Rendu des blocs. */
enum class BlockSkin(val id: String, val title: String, val price: Int) {
    CLASSIC("classic", "Classique", 0),
    GLASS("glass", "Verre", 400),
    NEON("neon", "Néon", 700),
    STRIPES("stripes", "Rayures", 900),
    GEM("gem", "Cristal", 1600);

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

/** Effet d'ambiance animé en arrière-plan. */
enum class BackgroundFx(val id: String, val title: String, val price: Int) {
    NONE("none", "Aucun", 0),
    STARS("stars", "Étoiles", 250),
    BUBBLES("bubbles", "Bulles", 350),
    SNOW("snow", "Neige", 450),
    FIREFLIES("fireflies", "Lucioles", 600),
    CONFETTI("confetti", "Confettis", 900);

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id } ?: NONE
    }
}

object Themes {
    val all = listOf(
        GameTheme("aurora", "Aurore", 0, 0xFF141E3C, 0xFF3B2A6B, 165f, 6f, 0.55f, 0.95f),
        GameTheme("sunset", "Crépuscule", 150, 0xFF2B1640, 0xFFE0645A, 345f, 5f, 0.6f, 1f),
        GameTheme("ocean", "Océan", 250, 0xFF04243B, 0xFF1A7FC1, 185f, 3f, 0.6f, 0.95f),
        GameTheme("forest", "Forêt", 350, 0xFF0A2620, 0xFF2F6B4F, 80f, 4f, 0.5f, 0.85f),
        GameTheme("candy", "Bonbon", 500, 0xFFFFD6E8, 0xFFB8E1FF, 300f, 9f, 0.38f, 1f, lightBackground = true),
        GameTheme("lava", "Lave", 650, 0xFF1A0505, 0xFF8C1C13, 0f, 3f, 0.8f, 1f),
        GameTheme("mint", "Menthe", 800, 0xFFE6FFF6, 0xFF9FE8D0, 150f, 5f, 0.35f, 0.92f, lightBackground = true),
        GameTheme("synth", "Synthwave", 1000, 0xFF120326, 0xFFB0127A, 280f, 7f, 0.7f, 1f),
        GameTheme("gold", "Or royal", 1400, 0xFF15120A, 0xFF4A3A0E, 40f, 2f, 0.75f, 1f),
        GameTheme("arctic", "Arctique", 1800, 0xFFDFF4FF, 0xFF7FB8E6, 200f, 2f, 0.25f, 1f, lightBackground = true),
        GameTheme("cosmos", "Cosmos", 2400, 0xFF02010A, 0xFF1C1446, 230f, 11f, 0.5f, 1f),
        GameTheme("mono", "Minimal", 3000, 0xFFF4F4F4, 0xFFD5D5D5, 0f, 0f, 0f, 0.3f, lightBackground = true),
    )

    fun byId(id: String): GameTheme = all.firstOrNull { it.id == id } ?: all.first()
}
