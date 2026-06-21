package com.railtheway.town

import com.badlogic.gdx.graphics.Color

enum class TownColor(val display: String, val color: Color) {
    RED("Redtown", Color(0.86f, 0.20f, 0.18f, 1f)),
    GREEN("Greenhill", Color(0.30f, 0.72f, 0.26f, 1f)),
    BLUE("Bluewater", Color(0.22f, 0.45f, 0.90f, 1f)),
    YELLOW("Yellowburg", Color(0.95f, 0.82f, 0.18f, 1f)),
    WHITE("Whitebridge", Color(0.93f, 0.93f, 0.93f, 1f)),
    ORANGE("Orangedale", Color(0.95f, 0.55f, 0.12f, 1f)),
    PURPLE("Purplevale", Color(0.62f, 0.30f, 0.78f, 1f)),
    BROWN("Brownwood", Color(0.55f, 0.36f, 0.18f, 1f)),
}

class Town(
    val id: Int,
    val color: TownColor,
    val tileX: Int,
    val tileY: Int,
) {
    val name: String get() = color.display
}
