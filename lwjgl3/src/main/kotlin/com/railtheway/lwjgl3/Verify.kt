package com.railtheway.lwjgl3

import com.railtheway.Config
import com.railtheway.Difficulty
import com.railtheway.town.TownColor
import com.railtheway.track.Dirs
import com.railtheway.track.TrackNet
import com.railtheway.train.Train
import com.railtheway.train.TrainSim
import com.railtheway.world.GridMap
import com.railtheway.world.TerrainType
import com.railtheway.world.WorldGen

/**
 * Headless smoke test of the GL-free simulation: world gen, 3-tile piece building,
 * junctions/switches, edge-based train movement following switches, reverse, colour-
 * matched delivery, and the crash rule. Exit code 1 on any failure.
 */
private var failures = 0
private fun check(name: String, cond: Boolean) {
    println((if (cond) "PASS  " else "FAIL  ") + name)
    if (!cond) failures++
}

fun main() {
    // 1. deterministic world gen
    val w = GridMap(); val gen = WorldGen(seed = 42L)
    gen.generateTerrain(w)
    val towns = gen.generateTowns(w, Difficulty.NORMAL)
    check("worldgen seats >=2 towns", towns.size >= 2)
    val w2 = GridMap(); WorldGen(seed = 42L).generateTerrain(w2)
    check("terrain deterministic by seed", w.terrain.toList() == w2.terrain.toList())

    // 2. 3-tile horizontal piece building (clean map of grass)
    val m = GridMap()
    val cost = TrackNet.pieceCost(m, 10, 10, 0)
    check("piece cost = 3 grass tiles", cost == 3 * TerrainType.GRASS.buildCost)
    TrackNet.placePiece(m, 10, 10, 0)
    check("piece lays track on its 3 tiles", m.hasTrack(9, 10) && m.hasTrack(10, 10) && m.hasTrack(11, 10))
    check("consecutive tiles are connected", TrackNet.connected(m, m.idx(10, 10), 0) && TrackNet.connected(m, m.idx(11, 10), 4))
    check("re-cost over existing track is free", TrackNet.pieceCost(m, 10, 10, 0) == 0)

    // 3. abutting piece extends the line; crossing piece makes a junction(switch)
    TrackNet.placePiece(m, 13, 10, 0) // tiles 12,13,14 ; 12 abuts 11 -> auto-link
    check("abutting piece links into existing line", TrackNet.connected(m, m.idx(11, 10), 0))
    TrackNet.placePiece(m, 11, 10, 1) // vertical through 11 -> junction at (11,10)
    check("crossing creates a junction (degree>=3)", TrackNet.degree(m, m.idx(11, 10)) >= 3)

    // 4. switch cycling changes the preferred exit
    val j = m.idx(11, 10); TrackNet.cycleSwitch(m, j)
    check("cycleSwitch sets a preferred exit", m.switchPref[j] >= 0)

    // 5. train movement + colour-matched delivery on a straight line into a town
    val line = GridMap()
    for (cx in 2..14 step 2) TrackNet.placePiece(line, cx, 5, 0) // continuous track y=5, x in 1..15
    val homeColor = TownColor.RED
    fun colorAt(idx: Int): TownColor? = if (idx == line.idx(15, 5)) homeColor else null
    val train = Train(homeColor, destTownId = 0, carriages = 2,
        fromTile = line.idx(2, 5), toTile = line.idx(3, 5))
    var res = TrainSim.Result.MOVING; var steps = 0
    while (res == TrainSim.Result.MOVING && steps < 200_000) {
        res = TrainSim.step(train, 1f / 60f, line, Config.TRAIN_SPEED, ::colorAt); steps++
    }
    check("train delivered at colour-matched town", res == TrainSim.Result.DELIVERED)

    // 6. reverse flips travel direction
    val r = Train(homeColor, 0, 1, fromTile = line.idx(5, 5), toTile = line.idx(6, 5)); r.t = 0.25f
    val of = r.fromTile; val ot = r.toTile
    r.reverse()
    check("reverse swaps from/to and mirrors t", r.fromTile == ot && r.toTile == of && r.t == 0.75f)

    // 7. dead-end reversal: a short isolated stub turns the train around
    val stub = GridMap(); TrackNet.placePiece(stub, 5, 5, 0) // tiles 4,5,6 at y=5
    fun noTown(@Suppress("UNUSED_PARAMETER") i: Int): TownColor? = null
    val d = Train(homeColor, 0, 1, fromTile = stub.idx(4, 5), toTile = stub.idx(5, 5))
    repeat(2000) { TrainSim.step(d, 1f / 60f, stub, Config.TRAIN_SPEED, ::noTown) }
    check("train survives a dead end by reversing", d.alive && d.fromTile in intArrayOf(stub.idx(4,5), stub.idx(5,5), stub.idx(6,5)).toList())

    // 8. crash rule: two trains on the same tile both die; section damaged
    val crashTile = line.idx(8, 5)
    val a = Train(homeColor, 0, 1, fromTile = line.idx(8, 5), toTile = line.idx(9, 5))
    val b = Train(homeColor, 0, 1, fromTile = line.idx(8, 5), toTile = line.idx(7, 5))
    check("two trains share a tile (crash detected)", a.occupantTile() == b.occupantTile())
    val had = line.hasTrack(crashTile)
    TrackNet.bulldoze(line, 8, 5)
    check("crash damages the track section", had && !line.hasTrack(crashTile))

    println(if (failures == 0) "\nALL CHECKS PASSED" else "\n$failures CHECK(S) FAILED")
    if (failures != 0) System.exit(1)
}
