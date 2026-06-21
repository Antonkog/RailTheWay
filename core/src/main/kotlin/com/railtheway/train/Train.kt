package com.railtheway.train

import com.railtheway.town.TownColor

/**
 * A train travelling the track graph. State is the directed edge it occupies:
 * fromTile -> toTile, with `t` the 0..1 progress along it. It follows switches at
 * junctions and must reach the town whose colour matches `color`.
 */
class Train(
    val color: TownColor,
    val destTownId: Int,
    val carriages: Int,
    var fromTile: Int,
    var toTile: Int,
) {
    var t: Float = 0f
    var alive: Boolean = true

    /** Tile index used for crash detection. */
    fun occupantTile(): Int = if (t >= 0.5f) toTile else fromTile

    /** Flip travel direction (click-to-reverse); reversing again resumes forward. */
    fun reverse() {
        val tmp = fromTile; fromTile = toTile; toTile = tmp
        t = 1f - t
    }
}
