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

    // 4. a crossing junction starts on the straight route and can be cycled
    val j = m.idx(11, 10)
    val crossingRoute = TrackNet.activeRoute(m, j)
    check("crossing defaults to a straight route",
        TrackNet.partnerOf(crossingRoute, Dirs.W) == Dirs.E &&
        TrackNet.partnerOf(crossingRoute, Dirs.N) == Dirs.S)
    check("cycleSwitch changes the route",
        TrackNet.cycleSwitch(m, j) && TrackNet.activeRoute(m, j) != crossingRoute)

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

    // 9. paired-route fork: straight route open, side branch closed, train bounces
    val t = GridMap()
    for (cx in 2..14 step 2) TrackNet.placePiece(t, cx, 5, 0) // horizontal y=5, x in 1..15
    TrackNet.placePiece(t, 8, 6, 1) // branch north: (8,5)-(8,6)-(8,7)
    val tj = t.idx(8, 5)
    check("T junction has degree 3", TrackNet.degree(t, tj) == 3)
    val forkDefault = TrackNet.activeRoute(t, tj)
    check("T junction default is the straight E-W route",
        TrackNet.partnerOf(forkDefault, Dirs.E) == Dirs.W &&
        TrackNet.partnerOf(forkDefault, Dirs.W) == Dirs.E &&
        TrackNet.partnerOf(forkDefault, Dirs.N) == -1)

    val fromBranch = Train(homeColor, 0, 1, fromTile = t.idx(8, 6), toTile = tj)
    TrainSim.step(fromBranch, 1f, t, 1f, ::noTown)
    check("train from a closed branch reverses",
        fromBranch.fromTile == tj && fromBranch.toTile == t.idx(8, 6))

    val fromTrunk = Train(homeColor, 0, 1, fromTile = t.idx(7, 5), toTile = tj)
    TrainSim.step(fromTrunk, 1f, t, 1f, ::noTown)
    check("train on the open route passes straight",
        fromTrunk.fromTile == tj && fromTrunk.toTile == t.idx(9, 5))

    // 10. cycling the fork opens the branch and closes a trunk
    check("cycleSwitch on a fork reports a change", TrackNet.cycleSwitch(t, tj))
    val forkSecond = TrackNet.activeRoute(t, tj)
    check("fork cycle 1 pairs N with E, W closed",
        TrackNet.partnerOf(forkSecond, Dirs.N) == Dirs.E &&
        TrackNet.partnerOf(forkSecond, Dirs.E) == Dirs.N &&
        TrackNet.partnerOf(forkSecond, Dirs.W) == -1)

    val branchNowOpen = Train(homeColor, 0, 1, fromTile = t.idx(8, 6), toTile = tj)
    TrainSim.step(branchNowOpen, 1f, t, 1f, ::noTown)
    check("open fork routes the branch onto E",
        branchNowOpen.fromTile == tj && branchNowOpen.toTile == t.idx(9, 5))

    val trunkNowClosed = Train(homeColor, 0, 1, fromTile = t.idx(7, 5), toTile = tj)
    TrainSim.step(trunkNowClosed, 1f, t, 1f, ::noTown)
    check("closed trunk reverses", trunkNowClosed.fromTile == tj && trunkNowClosed.toTile == t.idx(7, 5))

    // 11. a 4-way crossing cycles straight <-> crossover routes
    val x = GridMap()
    for (cx in 2..14 step 2) TrackNet.placePiece(x, cx, 5, 0)
    TrackNet.placePiece(x, 8, 5, 1) // vertical through (8,5)
    val xj = x.idx(8, 5)
    check("crossing has degree 4", TrackNet.degree(x, xj) == 4)
    val crossDefault = TrackNet.activeRoute(x, xj)
    check("4-way default is a straight crossing",
        TrackNet.partnerOf(crossDefault, Dirs.W) == Dirs.E &&
        TrackNet.partnerOf(crossDefault, Dirs.N) == Dirs.S)
    val straightTrain = Train(homeColor, 0, 1, fromTile = x.idx(7, 5), toTile = xj)
    TrainSim.step(straightTrain, 1f, x, 1f, ::noTown)
    check("straight crossing passes through", straightTrain.toTile == x.idx(9, 5))

    check("cycleSwitch on a crossing reports a change", TrackNet.cycleSwitch(x, xj))
    val crossR1 = TrackNet.activeRoute(x, xj)
    check("crossing cycle 1 is a crossover (E-S / N-W)",
        TrackNet.partnerOf(crossR1, Dirs.E) == Dirs.S &&
        TrackNet.partnerOf(crossR1, Dirs.S) == Dirs.E &&
        TrackNet.partnerOf(crossR1, Dirs.N) == Dirs.W &&
        TrackNet.partnerOf(crossR1, Dirs.W) == Dirs.N)

    check("cycleSwitch again reports a change", TrackNet.cycleSwitch(x, xj))
    val crossR2 = TrackNet.activeRoute(x, xj)
    check("crossing cycle 2 is the other crossover (E-N / W-S)",
        TrackNet.partnerOf(crossR2, Dirs.E) == Dirs.N &&
        TrackNet.partnerOf(crossR2, Dirs.N) == Dirs.E &&
        TrackNet.partnerOf(crossR2, Dirs.W) == Dirs.S &&
        TrackNet.partnerOf(crossR2, Dirs.S) == Dirs.W)

    // 12. bulldozing the branch reduces the fork to plain track (state resets)
    TrackNet.bulldoze(t, 8, 6)
    check("bulldozing the branch reduces the junction to degree 2", TrackNet.degree(t, tj) == 2)
    check("plain track has no switch route", TrackNet.activeRoute(t, tj) == 0)

    // 13. diagonal fork: straight NE-SW route open, N branch closed
    val dg = GridMap()
    for (c in 2..6 step 2) TrackNet.placePiece(dg, c, c, 2) // diagonal (1,1)..(7,7)
    TrackNet.placePiece(dg, 4, 5, 1) // branch north into (4,4)
    val dj = dg.idx(4, 4)
    check("diagonal fork has degree 3", TrackNet.degree(dg, dj) == 3)
    val diagRoute = TrackNet.activeRoute(dg, dj)
    check("diagonal fork defaults to straight NE-SW",
        TrackNet.partnerOf(diagRoute, Dirs.NE) == Dirs.SW &&
        TrackNet.partnerOf(diagRoute, Dirs.SW) == Dirs.NE &&
        TrackNet.partnerOf(diagRoute, Dirs.N) == -1)
    val diagBlocked = Train(homeColor, 0, 1, fromTile = dg.idx(4, 5), toTile = dj)
    TrainSim.step(diagBlocked, 1f, dg, 1f, ::noTown)
    check("diagonal branch is closed -> train reverses",
        diagBlocked.fromTile == dj && diagBlocked.toTile == dg.idx(4, 5))
    val diagOpen = Train(homeColor, 0, 1, fromTile = dg.idx(3, 3), toTile = dj)
    TrainSim.step(diagOpen, 2f, dg, 1f, ::noTown) // diagonal edge is sqrt(2) long
    check("diagonal straight passes through", diagOpen.toTile == dg.idx(5, 5))

    println(if (failures == 0) "\nALL CHECKS PASSED" else "\n$failures CHECK(S) FAILED")
    if (failures != 0) System.exit(1)
}
