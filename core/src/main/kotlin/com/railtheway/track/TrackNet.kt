package com.railtheway.track

import com.railtheway.world.GridMap
import com.railtheway.world.TerrainType

/** 8-direction tables. 0=E,1=NE,2=N,3=NW,4=W,5=SW,6=S,7=SE. */
object Dirs {
    val DX = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
    val DY = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
    fun opp(d: Int) = (d + 4) and 7

    /** Forward directions for the four straight-piece orientations. */
    val AXES = intArrayOf(0, 2, 1, 3) // E (horizontal), N (vertical), NE (/), NW (\)
    val AXIS_NAME = arrayOf("Horizontal", "Vertical", "Diagonal /", "Diagonal \\")

    /** Smallest circular step distance between two directions (0..4). */
    fun arc(a: Int, b: Int): Int {
        val d = ((a - b) and 7)
        return minOf(d, 8 - d)
    }

    /** Direction from tile A to adjacent tile B, or -1 if not 8-adjacent. */
    fun between(ax: Int, ay: Int, bx: Int, by: Int): Int {
        val dx = bx - ax; val dy = by - ay
        for (d in 0..7) if (DX[d] == dx && DY[d] == dy) return d
        return -1
    }
}

/**
 * Runtime-mutable track layer: 3-tile straight pieces, junctions/switches, bulldoze,
 * all expressed as symmetric connection masks on the GridMap.
 */
object TrackNet {
    private fun setBit(map: GridMap, i: Int, d: Int) {
        map.conn[i] = (map.mask(i) or (1 shl d)).toByte()
    }
    private fun clearBit(map: GridMap, i: Int, d: Int) {
        map.conn[i] = (map.mask(i) and (1 shl d).inv()).toByte()
    }

    fun connected(map: GridMap, i: Int, d: Int) = (map.mask(i) ushr d) and 1 == 1
    fun degree(map: GridMap, i: Int) = Integer.bitCount(map.mask(i))

    fun connectedDirs(map: GridMap, i: Int): IntArray {
        val m = map.mask(i)
        val out = ArrayList<Int>(8)
        for (d in 0..7) if ((m ushr d) and 1 == 1) out.add(d)
        return out.toIntArray()
    }

    /** Link tile (ax,ay) to its neighbour in direction d (symmetric). */
    private fun link(map: GridMap, ax: Int, ay: Int, d: Int) {
        val bx = ax + Dirs.DX[d]; val by = ay + Dirs.DY[d]
        if (!map.inBounds(ax, ay) || !map.inBounds(bx, by)) return
        setBit(map, map.idx(ax, ay), d)
        setBit(map, map.idx(bx, by), Dirs.opp(d))
    }

    /** The three tile (x,y) cells a piece at center (cx,cy) along orientation index occupies. */
    fun pieceTiles(cx: Int, cy: Int, orientation: Int): IntArray {
        val d = Dirs.AXES[orientation]
        val dx = Dirs.DX[d]; val dy = Dirs.DY[d]
        return intArrayOf(cx - dx, cy - dy, cx, cy, cx + dx, cy + dy) // (x0,y0,x1,y1,x2,y2)
    }

    fun pieceValid(map: GridMap, cx: Int, cy: Int, orientation: Int): Boolean {
        val t = pieceTiles(cx, cy, orientation)
        return map.inBounds(t[0], t[1]) && map.inBounds(t[2], t[3]) && map.inBounds(t[4], t[5])
    }

    /** Build cost: only tiles without track yet are charged (terrain-priced). */
    fun pieceCost(map: GridMap, cx: Int, cy: Int, orientation: Int): Int {
        val t = pieceTiles(cx, cy, orientation)
        var sum = 0
        var k = 0
        while (k < 6) {
            val x = t[k]; val y = t[k + 1]
            if (map.inBounds(x, y) && !map.hasTrack(map.idx(x, y))) sum += map.terrainAt(x, y).buildCost
            k += 2
        }
        return sum
    }

    /** Place the piece: link the three tiles and auto-extend onto abutting track. */
    fun placePiece(map: GridMap, cx: Int, cy: Int, orientation: Int) {
        val d = Dirs.AXES[orientation]
        val t = pieceTiles(cx, cy, orientation)
        val x0 = t[0]; val y0 = t[1]; val x1 = t[2]; val y1 = t[3]; val x2 = t[4]; val y2 = t[5]
        link(map, x0, y0, d) // t0 -> t1
        link(map, x1, y1, d) // t1 -> t2
        // extend backward off t0 and forward off t2 if they abut existing track
        val bx = x0 - Dirs.DX[d]; val by = y0 - Dirs.DY[d]
        if (map.hasTrack(bx, by)) link(map, bx, by, d)
        val fx = x2 + Dirs.DX[d]; val fy = y2 + Dirs.DY[d]
        if (map.hasTrack(fx, fy)) link(map, x2, y2, d)
    }

    /** Cycle a junction's switch preference to the next connected direction. */
    fun cycleSwitch(map: GridMap, i: Int) {
        val dirs = connectedDirs(map, i)
        if (dirs.size < 3) return
        val cur = map.switchPref[i]
        val pos = dirs.indexOf(cur)
        map.switchPref[i] = dirs[(pos + 1) % dirs.size]
    }

    /** Remove all track on a tile (and the matching bits on its neighbours); else clear terrain. */
    fun bulldoze(map: GridMap, x: Int, y: Int): Boolean {
        if (!map.inBounds(x, y)) return false
        val i = map.idx(x, y)
        if (map.hasTrack(i)) {
            for (d in 0..7) if (connected(map, i, d)) {
                val nx = x + Dirs.DX[d]; val ny = y + Dirs.DY[d]
                if (map.inBounds(nx, ny)) clearBit(map, map.idx(nx, ny), Dirs.opp(d))
            }
            map.conn[i] = 0
            map.switchPref[i] = -1
            return true
        }
        if (map.terrainAt(x, y) != TerrainType.GRASS) {
            map.terrain[i] = TerrainType.GRASS
            return true
        }
        return false
    }
}
