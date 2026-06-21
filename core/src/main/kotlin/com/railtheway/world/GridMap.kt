package com.railtheway.world

import com.railtheway.Config

/**
 * The tile grid. Terrain is the authoring layer. The movement graph is stored as
 * per-tile 8-direction connection masks (`conn`): bit d set means this tile links to
 * its neighbour in direction d (see TrackNet.Dirs). Masks are kept symmetric.
 * `switchPref` holds a junction's chosen exit direction (-1 = none).
 */
class GridMap(val width: Int = Config.MAP_W, val height: Int = Config.MAP_H) {
    val terrain = Array(width * height) { TerrainType.GRASS }
    val conn = ByteArray(width * height)
    val switchPref = IntArray(width * height) { -1 }

    fun idx(x: Int, y: Int) = y * width + x
    fun inBounds(x: Int, y: Int) = x in 0 until width && y in 0 until height

    fun terrainAt(x: Int, y: Int): TerrainType = terrain[idx(x, y)]

    fun mask(i: Int): Int = conn[i].toInt() and 0xFF
    fun hasTrack(i: Int): Boolean = mask(i) != 0
    fun hasTrack(x: Int, y: Int): Boolean = inBounds(x, y) && hasTrack(idx(x, y))

    /** World-space center of a tile. */
    fun centerX(x: Int) = (x + 0.5f) * Config.TILE
    fun centerY(y: Int) = (y + 0.5f) * Config.TILE
}
