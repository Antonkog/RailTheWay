package com.railtheway

/** Global tuning constants. World units == pixels at default zoom. */
object Config {
    const val TILE = 32f
    const val MAP_W = 48
    const val MAP_H = 30

    // Camera viewport in world units (the window content size).
    const val VIEW_W = 960f
    const val VIEW_H = 600f

    const val START_BALANCE = 200_000

    // Build costs per tile by terrain (see CostModel).
    const val COST_BASE = 800
    const val COST_TREES = 2_200      // clearing forest
    const val COST_ROCKS = 1_600
    const val COST_WATER = 4_000      // bridging a lake

    const val BULLDOZE_COST = 400

    const val ORDER_TRAIN_COST = 3_000
    const val DELIVERY_PAYOUT = 6_000 // per carriage on a color-matched arrival

    const val TRAIN_SPEED = 4.0f      // tiles / second
    const val PAN_SPEED = 600f        // world units / second

    const val START_YEAR = 1800
    const val PADDING = 0.5f
}

enum class Difficulty(val display: String, val endYear: Int, val startTowns: Int, val maxTowns: Int) {
    EASY("Easy", 1900, 2, 4),
    NORMAL("Normal", 1960, 3, 6),
    HARD("Hard", 2020, 3, 8),
}
