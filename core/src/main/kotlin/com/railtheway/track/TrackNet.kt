package com.railtheway.track

import com.railtheway.world.GridMap
import com.railtheway.world.TerrainType

/** 8-direction tables. 0=E,1=NE,2=N,3=NW,4=W,5=SW,6=S,7=SE. */
object Dirs {
    const val E = 0
    const val NE = 1
    const val N = 2
    const val NW = 3
    const val W = 4
    const val SW = 5
    const val S = 6
    const val SE = 7

    val DX = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
    val DY = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
    fun opp(d: Int) = (d + 4) and 7

    /** Forward directions for the four straight-piece orientations. */
    val AXES = intArrayOf(E, N, NE, NW) // E (horizontal), N (vertical), NE (/), NW (\)
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
        val old = map.mask(i)
        val next = old or (1 shl d)
        if (next != old) {
            map.conn[i] = next.toByte()
            map.switchRoute[i] = 0 // topology changed -> recompute the route
        }
    }
    private fun clearBit(map: GridMap, i: Int, d: Int) {
        val old = map.mask(i)
        val next = old and (1 shl d).inv()
        if (next != old) {
            map.conn[i] = next.toByte()
            map.switchRoute[i] = 0
        }
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

    // ---- switch routes ------------------------------------------------------
    //
    // A switch state is a matching of the tile's connected directions, packed as
    // four bits per direction: 0 = unpaired (closed), d+1 = paired with direction d.
    // A train leaving a junction may only use the direction paired with the one it
    // arrived from; an unpaired arrival reverses. The first option is always the
    // straightest matching, so the default is "straight through" and the side branch
    // of a fork starts closed.

    /** Pack pair (a,b) into a route encoding. */
    private fun encode(enc: Int, a: Int, b: Int): Int =
        enc or ((b + 1) shl (a * 4)) or ((a + 1) shl (b * 4))

    /** Sum of pair "straightness" (opposite dirs score 4, 45° apart score 1). */
    private fun totalArc(enc: Int): Int {
        var sum = 0
        for (d in 0..7) {
            val code = (enc ushr (d * 4)) and 0xF
            if (code != 0) {
                val p = code - 1
                if (p > d) sum += Dirs.arc(d, p)
            }
        }
        return sum
    }

    /** Sort options straightest-first, then by encoding for a deterministic tie-break. */
    private fun sortOptions(opts: IntArray): IntArray {
        for (i in 1 until opts.size) {
            val x = opts[i]; val ax = totalArc(x)
            var j = i - 1
            while (j >= 0) {
                val aj = totalArc(opts[j])
                if (aj > ax || (aj == ax && opts[j] < x)) break
                opts[j + 1] = opts[j]; j--
            }
            opts[j + 1] = x
        }
        return opts
    }

    /**
     * All switch routes for a connection mask, straightest first.
     * degree 3 -> the 3 two-branch routes (one branch closed);
     * degree 4 -> the 3 perfect pairings (crossing / crossovers);
     * degree >= 5 -> one greedy straightest matching (fixed junction, no cycling);
     * degree < 3 -> none (plain track / dead end).
     */
    fun routeOptions(mask: Int): IntArray {
        val dirs = ArrayList<Int>(8)
        for (d in 0..7) if ((mask ushr d) and 1 == 1) dirs.add(d)
        when (dirs.size) {
            3 -> {
                val a = dirs[0]; val b = dirs[1]; val c = dirs[2]
                return sortOptions(intArrayOf(
                    encode(0, a, b), // c closed
                    encode(0, a, c), // b closed
                    encode(0, b, c), // a closed
                ))
            }
            4 -> {
                val a = dirs[0]; val b = dirs[1]; val c = dirs[2]; val d = dirs[3]
                return sortOptions(intArrayOf(
                    encode(encode(0, a, b), c, d),
                    encode(encode(0, a, c), b, d),
                    encode(encode(0, a, d), b, c),
                ))
            }
            in 5..8 -> {
                val free = dirs.toMutableList()
                var enc = 0
                while (free.size >= 2) {
                    var bi = 0; var bj = 1; var best = -1
                    for (ii in free.indices) for (jj in ii + 1 until free.size) {
                        val arc = Dirs.arc(free[ii], free[jj])
                        if (arc > best) { best = arc; bi = ii; bj = jj }
                    }
                    enc = encode(enc, free[bi], free[bj])
                    free.removeAt(bj); free.removeAt(bi)
                }
                return intArrayOf(enc)
            }
            else -> return IntArray(0)
        }
    }

    /** A stored route is valid while every pair is symmetric and still connected. */
    fun isValidRoute(mask: Int, route: Int): Boolean {
        if (route == 0) return false
        for (d in 0..7) {
            val code = (route ushr (d * 4)) and 0xF
            val linked = (mask ushr d) and 1 == 1
            if (!linked) {
                if (code != 0) return false
                continue
            }
            if (code == 0) continue
            val p = code - 1
            if (p !in 0..7) return false
            if (((mask ushr p) and 1) == 0) return false
            if (((route ushr (p * 4)) and 0xF) != d + 1) return false
        }
        return true
    }

    /** The junction's active route, lazily initialised to the straightest matching. */
    fun activeRoute(map: GridMap, i: Int): Int {
        val mask = map.mask(i)
        if (Integer.bitCount(mask) < 3) {
            map.switchRoute[i] = 0
            return 0
        }
        val cur = map.switchRoute[i]
        if (isValidRoute(mask, cur)) return cur
        val opts = routeOptions(mask)
        val def = if (opts.isEmpty()) 0 else opts[0]
        map.switchRoute[i] = def
        return def
    }

    /** The direction paired with [d] in [route], or -1 when [d] is closed/unpaired. */
    fun partnerOf(route: Int, d: Int): Int {
        val code = (route ushr (d * 4)) and 0xF
        return if (code == 0) -1 else code - 1
    }

    /** Advance a junction to the next route. Returns false for plain track / fixed junctions. */
    fun cycleSwitch(map: GridMap, i: Int): Boolean {
        val mask = map.mask(i)
        if (Integer.bitCount(mask) < 3) return false
        val opts = routeOptions(mask)
        if (opts.size <= 1) return false
        val cur = activeRoute(map, i)
        var found = -1
        for (o in opts.indices) if (opts[o] == cur) { found = o; break }
        if (found < 0) {
            map.switchRoute[i] = opts[0]
        } else {
            map.switchRoute[i] = opts[(found + 1) % opts.size]
        }
        return true
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
            map.switchRoute[i] = 0
            return true
        }
        if (map.terrainAt(x, y) != TerrainType.GRASS) {
            map.terrain[i] = TerrainType.GRASS
            return true
        }
        return false
    }
}
