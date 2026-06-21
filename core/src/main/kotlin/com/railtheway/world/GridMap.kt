package com.railtheway.world

import com.railtheway.Config

/**
 * The tile grid: the authoring/occupancy/terrain layer. `track` marks whether rail
 * has been laid on a tile (the movement graph in TrackNet is derived from this).
 */
class GridMap(val width: Int = Config.MAP_W, val height: Int = Config.MAP_H) {
    val terrain = Array(width * height) { TerrainType.GRASS }
    val track = BooleanArray(width * height)

    fun idx(x: Int, y: Int) = y * width + x
    fun inBounds(x: Int, y: Int) = x in 0 until width && y in 0 until height

    fun terrainAt(x: Int, y: Int): TerrainType = terrain[idx(x, y)]
    fun hasTrack(x: Int, y: Int): Boolean = inBounds(x, y) && track[idx(x, y)]

    fun setTrack(x: Int, y: Int, value: Boolean) {
        if (inBounds(x, y)) track[idx(x, y)] = value
    }

    /** World-space center of a tile. */
    fun centerX(x: Int) = (x + 0.5f) * Config.TILE
    fun centerY(y: Int) = (y + 0.5f) * Config.TILE
}
