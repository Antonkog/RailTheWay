package com.railtheway.track

import com.railtheway.world.GridMap
import kotlin.math.abs
import kotlin.math.max

/**
 * Runtime-mutable track layer + cost model + pathfinding, all derived from the
 * GridMap's `track` flags. Track tiles form the movement graph (8-neighbourhood);
 * a town is "connected" once a track tile is laid on its station tile.
 */
object TrackNet {

    /** Tiles a straight drag from (x0,y0) to (x1,y1) would cover (Bresenham). */
    fun lineTiles(x0: Int, y0: Int, x1: Int, y1: Int): List<Int> {
        val out = ArrayList<Int>()
        var x = x0; var y = y0
        val dx = abs(x1 - x0); val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1; val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            out.add(y * 100000 + x) // packed temporarily; unpack by caller via helpers below
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
        return out
    }

    fun packX(packed: Int) = packed % 100000
    fun packY(packed: Int) = packed / 100000

    /** Cost to build along a line; tiles that already have track are free (reuse). */
    fun lineCost(map: GridMap, packedTiles: List<Int>): Int {
        var sum = 0
        for (p in packedTiles) {
            val x = packX(p); val y = packY(p)
            if (!map.inBounds(x, y)) continue
            if (map.hasTrack(x, y)) continue
            sum += map.terrainAt(x, y).buildCost
        }
        return sum
    }

    fun commitLine(map: GridMap, packedTiles: List<Int>) {
        for (p in packedTiles) {
            val x = packX(p); val y = packY(p)
            if (map.inBounds(x, y)) map.setTrack(x, y, true)
        }
    }

    private val NEI_X = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
    private val NEI_Y = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)

    /**
     * BFS shortest path over track tiles from src tile-index to dst tile-index.
     * Returns the path as a list of tile indices (inclusive of both ends), or null.
     */
    fun findPath(map: GridMap, srcIdx: Int, dstIdx: Int): IntArray? {
        if (!map.track[srcIdx] || !map.track[dstIdx]) return null
        val w = map.width
        val prev = IntArray(map.width * map.height) { -2 }
        val queue = ArrayDeque<Int>()
        queue.addLast(srcIdx)
        prev[srcIdx] = -1
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            if (cur == dstIdx) return rebuild(prev, dstIdx)
            val cx = cur % w; val cy = cur / w
            for (k in NEI_X.indices) {
                val nx = cx + NEI_X[k]; val ny = cy + NEI_Y[k]
                if (!map.inBounds(nx, ny)) continue
                val ni = map.idx(nx, ny)
                if (!map.track[ni] || prev[ni] != -2) continue
                prev[ni] = cur
                queue.addLast(ni)
            }
        }
        return null
    }

    private fun rebuild(prev: IntArray, dst: Int): IntArray {
        val rev = ArrayList<Int>()
        var c = dst
        while (c != -1) { rev.add(c); c = prev[c] }
        rev.reverse()
        return rev.toIntArray()
    }

    fun maxAxisDistance(x0: Int, y0: Int, x1: Int, y1: Int) = max(abs(x1 - x0), abs(y1 - y0))
}
