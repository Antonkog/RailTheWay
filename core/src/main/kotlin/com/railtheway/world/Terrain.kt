package com.railtheway.world

import com.badlogic.gdx.graphics.Color
import com.railtheway.Config

enum class TerrainType(val color: Color, val buildCost: Int) {
    GRASS(Color(0.34f, 0.62f, 0.27f, 1f), Config.COST_BASE),
    TREES(Color(0.16f, 0.40f, 0.18f, 1f), Config.COST_TREES),
    ROCKS(Color(0.55f, 0.52f, 0.48f, 1f), Config.COST_ROCKS),
    WATER(Color(0.24f, 0.44f, 0.72f, 1f), Config.COST_WATER),
}
