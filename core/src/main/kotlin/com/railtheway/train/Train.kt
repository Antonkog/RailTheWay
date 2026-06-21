package com.railtheway.train

import com.railtheway.town.TownColor

/**
 * A train travelling a fixed tile-index path toward the town whose colour matches
 * `color`. Movement is continuous via (seg, t) interpolation between tile centers.
 */
class Train(
    val color: TownColor,
    val destTownId: Int,
    val path: IntArray,
    val carriages: Int,
) {
    var seg: Int = 0          // index into path of the segment start
    var t: Float = 0f         // 0..1 progress along current segment
    var alive: Boolean = true

    val finished: Boolean get() = seg >= path.size - 1

    /** Tile index the train currently occupies (for crash detection). */
    fun occupantTile(): Int {
        if (path.isEmpty()) return -1
        val i = if (t < 0.5f) seg else seg + 1
        return path[i.coerceIn(0, path.size - 1)]
    }

    /** Advance continuously along the path; segment length accounts for diagonals. */
    fun step(dt: Float, mapWidth: Int, speedTilesPerSec: Float) {
        if (path.size < 2) return
        val a = path[seg]
        val b = path[(seg + 1).coerceAtMost(path.size - 1)]
        val ax = a % mapWidth; val ay = a / mapWidth
        val bx = b % mapWidth; val by = b / mapWidth
        val dx = (bx - ax).toFloat(); val dy = (by - ay).toFloat()
        val segLen = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.0001f)
        t += speedTilesPerSec * dt / segLen
        while (t >= 1f && seg < path.size - 1) { t -= 1f; seg++ }
        if (seg >= path.size - 1) t = 0f
    }
}
