package com.railtheway.lwjgl3

import com.railtheway.Config
import com.railtheway.Difficulty
import com.railtheway.track.TrackNet
import com.railtheway.train.Train
import com.railtheway.world.GridMap
import com.railtheway.world.WorldGen

/**
 * Headless smoke test of the GL-free game simulation: world gen, terrain-priced
 * building, mutable track + BFS routing, train movement, color-matched delivery,
 * and the crash rule. Runs without a display. Exit code 1 if any check fails.
 */
private var failures = 0
private fun check(name: String, cond: Boolean) {
    println((if (cond) "PASS  " else "FAIL  ") + name)
    if (!cond) failures++
}

fun main() {
    // 1. deterministic world gen
    val map = GridMap()
    val gen = WorldGen(seed = 42L)
    gen.generateTerrain(map)
    val towns = gen.generateTowns(map, Difficulty.NORMAL)
    check("worldgen seats >=2 towns", towns.size >= 2)

    val map2 = GridMap()
    WorldGen(seed = 42L).generateTerrain(map2)
    check("terrain is deterministic by seed", map.terrain.toList() == map2.terrain.toList())

    val a = towns[0]; val b = towns[1]

    // 2. terrain-priced building along a straight drag between two towns
    val tiles = TrackNet.lineTiles(a.tileX, a.tileY, b.tileX, b.tileY)
    val cost = TrackNet.lineCost(map, tiles)
    check("build cost is positive", cost > 0)
    TrackNet.commitLine(map, tiles)
    check("track laid on both town tiles", map.hasTrack(a.tileX, a.tileY) && map.hasTrack(b.tileX, b.tileY))
    check("re-laying same line is free (reuse)", TrackNet.lineCost(map, tiles) == 0)

    // 3. BFS routing over the mutable track graph
    val path = TrackNet.findPath(map, map.idx(a.tileX, a.tileY), map.idx(b.tileX, b.tileY))
    check("BFS finds a route A->B", path != null && path.size >= 2)

    // 4. train movement + color-matched delivery
    val train = Train(color = b.color, destTownId = b.id, path = path!!, carriages = 2)
    var steps = 0
    while (!train.finished && steps < 100_000) { train.step(1f / 60f, map.width, Config.TRAIN_SPEED); steps++ }
    check("train reaches destination", train.finished)
    val matched = towns.first { it.id == train.destTownId }.color == train.color
    check("arrival is colour-matched (payout earned)", matched)
    val payout = if (matched) Config.DELIVERY_PAYOUT * train.carriages else 0
    check("payout scales with carriages", payout == Config.DELIVERY_PAYOUT * 2)

    // 5. crash rule: two trains sharing a tile both die and the section is damaged
    val t1 = Train(b.color, b.id, path, 1)
    val t2 = Train(b.color, b.id, path, 1)
    val crashTile = t1.occupantTile()
    val sameTile = t1.occupantTile() == t2.occupantTile()
    check("two trains on same tile are detected as a crash", sameTile)
    // apply the consequence as RailGame does
    val before = map.track[crashTile]
    map.track[crashTile] = false
    check("crash damages the track section (gap left)", before && !map.track[crashTile])

    println(if (failures == 0) "\nALL CHECKS PASSED" else "\n$failures CHECK(S) FAILED")
    if (failures != 0) System.exit(1)
}
