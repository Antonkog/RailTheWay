package com.railtheway.world

import com.badlogic.gdx.math.RandomXS128
import com.railtheway.Difficulty
import com.railtheway.town.Town
import com.railtheway.town.TownColor

/**
 * Deterministic procedural generation of terrain (tree/rock clusters, water ponds)
 * and the initial colored towns seated at the map edges.
 */
class WorldGen(private val seed: Long) {

    fun generateTerrain(map: GridMap) {
        val rng = RandomXS128(seed)
        // base grass already set by GridMap.

        // Water ponds (lakes): a handful of small blobs.
        repeat(6 + rng.nextInt(4)) { blob(map, rng, TerrainType.WATER, 1 + rng.nextInt(2)) }
        // Rock clusters.
        repeat(5 + rng.nextInt(4)) { blob(map, rng, TerrainType.ROCKS, 1) }
        // Tree clusters (forests) - the expensive-to-clear terrain.
        repeat(14 + rng.nextInt(8)) { blob(map, rng, TerrainType.TREES, 1 + rng.nextInt(2)) }
    }

    private fun blob(map: GridMap, rng: RandomXS128, type: TerrainType, radius: Int) {
        val cx = 3 + rng.nextInt(map.width - 6)
        val cy = 3 + rng.nextInt(map.height - 6)
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                if (dx * dx + dy * dy > radius * radius + 1) continue
                if (rng.nextFloat() < 0.35f) continue
                val x = cx + dx; val y = cy + dy
                if (map.inBounds(x, y)) map.terrain[map.idx(x, y)] = type
            }
        }
    }

    /** Seat `count` towns at the map edges, each a distinct color, on clear-able tiles. */
    fun generateTowns(map: GridMap, difficulty: Difficulty): MutableList<Town> {
        val rng = RandomXS128(seed xor 0x9E3779B97F4A7C15uL.toLong())
        val colors = TownColor.values().toMutableList()
        val towns = ArrayList<Town>()
        val count = difficulty.startTowns
        var id = 0
        var guard = 0
        while (towns.size < count && guard++ < 500) {
            val (tx, ty) = randomEdgeTile(map, rng)
            if (towns.any { kotlin.math.abs(it.tileX - tx) + kotlin.math.abs(it.tileY - ty) < 8 }) continue
            val color = colors.removeAt(rng.nextInt(colors.size))
            // towns sit on clear grass.
            map.terrain[map.idx(tx, ty)] = TerrainType.GRASS
            towns.add(Town(id++, color, tx, ty))
        }
        return towns
    }

    /** Returns a free color for a newly founded town, or null if all are used. */
    fun nextFreeColor(used: List<Town>): TownColor? =
        TownColor.values().firstOrNull { c -> used.none { it.color == c } }

    fun randomEdgeTile(map: GridMap, rng: RandomXS128): Pair<Int, Int> {
        return when (rng.nextInt(4)) {
            0 -> 1 to (2 + rng.nextInt(map.height - 4))                 // left
            1 -> (map.width - 2) to (2 + rng.nextInt(map.height - 4))   // right
            2 -> (2 + rng.nextInt(map.width - 4)) to 1                  // bottom
            else -> (2 + rng.nextInt(map.width - 4)) to (map.height - 2) // top
        }
    }
}
