package com.railtheway.world

import com.railtheway.Config

/**
 * The tile grid. Terrain is the authoring layer. The movement graph is stored as
 * per-tile 8-direction connection masks (`conn`): bit d set means this tile links to
 * its neighbour in direction d (see TrackNet.Dirs). Masks are kept symmetric.
 * `switchRoute` holds a junction's active route as a packed matching: four bits per
 * direction, 0 = direction is not paired (closed / not connected), d+1 = this
 * direction is paired with direction d. At a fork only the paired route is traversable;
 * a train arriving along a closed branch reverses. 0 = unset (recompute from topology).
 */
class GridMap(val width: Int = Config.MAP_W, val height: Int = Config.MAP_H) {
    val terrain = Array(width * height) { TerrainType.GRASS }
    val conn = ByteArray(width * height)
    val switchRoute = IntArray(width * height)

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
