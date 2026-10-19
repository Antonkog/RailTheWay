package com.railtheway.train

import com.railtheway.town.TownColor
import com.railtheway.track.Dirs
import com.railtheway.track.TrackNet
import com.railtheway.world.GridMap

/**
 * GL-free train movement so it can be unit-tested headlessly. Advances a train along
 * its edge; at each tile it follows the junction's paired route (and reverses when its
 * branch is closed), reverses at dead ends, and reports a colour-matched delivery when
 * the train reaches its matching town.
 */
object TrainSim {
    enum class Result { MOVING, DELIVERED }

    fun step(
        tr: Train,
        dt: Float,
        map: GridMap,
        speedTilesPerSec: Float,
        townColorAt: (Int) -> TownColor?,
    ): Result {
        if (tr.fromTile < 0 || tr.toTile < 0) return Result.MOVING
        val w = map.width
        val segLen = segLen(tr.fromTile, tr.toTile, w)
        tr.t += speedTilesPerSec * dt / segLen

        var guard = 0
        while (tr.t >= 1f && guard++ < 64) {
            tr.t -= 1f
            val b = tr.toTile
            val color = townColorAt(b)
            if (color != null && color == tr.color) {
                tr.alive = false
                return Result.DELIVERED
            }
            val a = tr.fromTile
            val bx = b % w; val by = b / w
            val ax = a % w; val ay = a / w
            val backDir = Dirs.between(bx, by, ax, ay)
            val exit = chooseExit(map, b, backDir)
            if (exit < 0) {
                // dead end -> reverse back toward A
                tr.fromTile = b; tr.toTile = a
            } else {
                val nx = bx + Dirs.DX[exit]; val ny = by + Dirs.DY[exit]
                tr.fromTile = b; tr.toTile = map.idx(nx, ny)
            }
        }
        return Result.MOVING
    }

    fun segLen(from: Int, to: Int, width: Int): Float {
        val ax = from % width; val ay = from / width
        val bx = to % width; val by = to / width
        return if (ax != bx && ay != by) 1.41421f else 1f
    }

    /** Pick the outgoing direction at tile b, having arrived from direction backDir. */
    private fun chooseExit(map: GridMap, b: Int, backDir: Int): Int {
        val dirs = TrackNet.connectedDirs(map, b)
        if (dirs.size < 3) {
            // Plain track / dead end: the only other connection, or reverse.
            var first = -1; var count = 0
            for (d in dirs) if (d != backDir) { count++; if (first < 0) first = d }
            return if (count == 0) -1 else first
        }
        // Junction/crossing: only the direction paired with the arrival is open.
        // An unpaired (closed) branch returns -1, so the train reverses.
        return TrackNet.partnerOf(TrackNet.activeRoute(map, b), backDir)
    }
}
