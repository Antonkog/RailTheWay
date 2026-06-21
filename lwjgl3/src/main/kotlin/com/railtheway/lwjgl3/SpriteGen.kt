package com.railtheway.lwjgl3

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.utils.GdxNativesLoader
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generates RailTheWay's 2D sprite set as PNGs (headless, via libGDX Pixmap - no GL).
 *
 * The art is ORIGINAL work, hand-drawn in code in the general style of classic top-down
 * railway-builder games (warm grass-green palette, brown-tie rails, little steam-loco
 * silhouettes, barn-style colored stations). No third-party / ripped assets are used.
 *
 * Train and station BODIES are drawn white so the renderer can tint them per town colour;
 * dark detailing (outlines, smokeboxes, roofs, wheels) survives the multiply tint.
 * Output: <repo>/assets/sprites/.
 */
private const val S = 48
private const val C = S / 2f

// palette
private val GRASS = Color(0.43f, 0.66f, 0.31f, 1f)
private val GRASS_DK = Color(0.36f, 0.58f, 0.26f, 1f)
private val LEAF = Color(0.13f, 0.34f, 0.15f, 1f)
private val LEAF_HI = Color(0.22f, 0.50f, 0.22f, 1f)
private val BARK = Color(0.34f, 0.22f, 0.11f, 1f)
private val WATER = Color(0.23f, 0.46f, 0.74f, 1f)
private val WATER_DK = Color(0.17f, 0.36f, 0.62f, 1f)
private val WATER_HI = Color(0.46f, 0.66f, 0.88f, 1f)
private val STONE = Color(0.58f, 0.55f, 0.51f, 1f)
private val STONE_DK = Color(0.42f, 0.40f, 0.37f, 1f)
private val STONE_HI = Color(0.72f, 0.70f, 0.66f, 1f)
private val TIE = Color(0.47f, 0.32f, 0.16f, 1f)
private val STEEL = Color(0.66f, 0.66f, 0.70f, 1f)
private val BALLAST = Color(0.74f, 0.71f, 0.64f, 1f)
private val SLEEPER = Color(0.44f, 0.37f, 0.30f, 1f)
private val RAILDK = Color(0.30f, 0.28f, 0.28f, 1f)
private val PINE = Color(0.16f, 0.40f, 0.20f, 1f)
private val PINE_DK = Color(0.09f, 0.28f, 0.13f, 1f)
private val PINE_HI = Color(0.26f, 0.54f, 0.27f, 1f)
private val DARK = Color(0.18f, 0.18f, 0.22f, 1f)
private val ROOF = Color(0.30f, 0.22f, 0.20f, 1f)
private val WIN = Color(0.72f, 0.86f, 0.96f, 1f)
private val FLOWER_Y = Color(0.96f, 0.86f, 0.22f, 1f)
private val FLOWER_P = Color(0.92f, 0.52f, 0.70f, 1f)

fun main() {
    GdxNativesLoader.load()
    val outDir = File(System.getProperty("user.dir"), "assets/sprites").apply { mkdirs() }
    fun save(name: String, pm: Pixmap) { PixmapIO.writePNG(FileHandle(File(outDir, "$name.png")), pm); pm.dispose() }

    save("grass", grass()); save("water", water()); save("rocks", rocks()); save("trees", trees())

    save("rail_ew", rail(0f)); save("rail_ns", rail(90f)); save("rail_d1", rail(45f)); save("rail_d2", rail(135f))
    save("rail_cross", railCross()); save("rail_half_e", railHalf())

    save("loco_ew", loco(0f)); save("loco_ns", loco(90f)); save("loco_d1", loco(45f)); save("loco_d2", loco(135f))
    save("car_ew", car(0f)); save("car_ns", car(90f)); save("car_d1", car(45f)); save("car_d2", car(135f))

    save("town", town()); save("badge", badge())

    File(outDir, "LICENSE.txt").writeText(
        "Sprites for RailTheWay - original procedural art (CC0). No third-party or ripped assets.\n"
    )
    println("Sprites written to ${outDir.absolutePath}")
}

// -------------------------------------------------------------------- helpers

private fun blank(): Pixmap {
    val pm = Pixmap(S, S, Pixmap.Format.RGBA8888)
    pm.blending = Pixmap.Blending.None
    pm.setColor(0f, 0f, 0f, 0f); pm.fill()
    return pm
}

private fun dir(deg: Float): Pair<Float, Float> {
    val a = Math.toRadians(deg.toDouble()); return cos(a).toFloat() to sin(a).toFloat()
}

private fun capsule(pm: Pixmap, cx: Float, cy: Float, dx: Float, dy: Float, half: Float, r: Int, col: Color) {
    pm.setColor(col)
    var s = -half
    while (s <= half) { pm.fillCircle((cx + dx * s).toInt(), (cy + dy * s).toInt(), r); s += 1f }
}

/** Deterministic tiny PRNG so terrain detail is stable between runs. */
private class Rnd(var s: Int) { fun next(m: Int): Int { s = s * 1103515245 + 12345; return ((s ushr 16) and 0x7fff) % m } }

private fun speckle(pm: Pixmap, base: Color) {
    pm.setColor(base); pm.fill()
    val r = Rnd(7)
    pm.setColor(GRASS_DK)
    repeat(10) { pm.fillCircle(r.next(S), r.next(S), 1 + r.next(2)) }
}

// -------------------------------------------------------------------- terrain

private fun grass(): Pixmap {
    val pm = Pixmap(S, S, Pixmap.Format.RGBA8888); speckle(pm, GRASS)
    val r = Rnd(91)
    repeat(3) { pm.setColor(FLOWER_Y); pm.fillCircle(r.next(S), r.next(S), 1) }
    repeat(2) { pm.setColor(FLOWER_P); pm.fillCircle(r.next(S), r.next(S), 1) }
    return pm
}

private fun water(): Pixmap {
    val pm = Pixmap(S, S, Pixmap.Format.RGBA8888)
    pm.setColor(WATER); pm.fill()
    pm.setColor(WATER_DK); pm.drawRectangle(0, 0, S, S); pm.drawRectangle(1, 1, S - 2, S - 2)
    pm.setColor(WATER_HI)
    for (i in 0 until 3) pm.drawLine(5, 12 + i * 12, S - 6, 16 + i * 12)
    return pm
}

private fun rocks(): Pixmap {
    val pm = Pixmap(S, S, Pixmap.Format.RGBA8888); speckle(pm, GRASS)
    val spots = arrayOf(intArrayOf(18, 28, 8), intArrayOf(30, 20, 6), intArrayOf(31, 31, 5))
    for (s in spots) {
        pm.setColor(STONE_DK); pm.fillCircle(s[0], s[1] + 1, s[2])
        pm.setColor(STONE); pm.fillCircle(s[0], s[1], s[2])
        pm.setColor(STONE_HI); pm.fillCircle(s[0] - 2, s[1] - 2, s[2] / 2)
    }
    return pm
}

/** A layered conifer (pine): a couple of stacked triangles with a highlight. */
private fun cone(pm: Pixmap, cx: Int, baseY: Int, h: Int, halfW: Int, col: Color) {
    pm.setColor(col)
    for (r in 0..h) {
        val w = (halfW * (1f - r.toFloat() / h)).toInt()
        pm.fillRectangle(cx - w, baseY - r, w * 2 + 1, 1)
    }
}

private fun pine(pm: Pixmap, cx: Int, baseY: Int, scale: Float) {
    val h = (16 * scale).toInt(); val hw = (8 * scale).toInt()
    pm.setColor(BARK); pm.fillRectangle(cx - 1, baseY, 3, (5 * scale).toInt())
    // shadow/outline cone, main cone, then a smaller upper tier + highlight
    cone(pm, cx, baseY + 1, h + 1, hw + 1, PINE_DK)
    cone(pm, cx, baseY, h, hw, PINE)
    cone(pm, cx, baseY - (h * 0.45f).toInt(), (h * 0.6f).toInt(), (hw * 0.7f).toInt(), PINE)
    cone(pm, cx - 1, baseY - (h * 0.2f).toInt(), (h * 0.45f).toInt(), (hw * 0.45f).toInt(), PINE_HI)
}

private fun trees(): Pixmap {
    val pm = Pixmap(S, S, Pixmap.Format.RGBA8888); speckle(pm, GRASS)
    pine(pm, 16, 40, 1.05f)
    pine(pm, 33, 34, 0.85f)
    pine(pm, 26, 26, 0.7f)
    return pm
}

// -------------------------------------------------------------------- rails

/** A grey gravel ballast bed with darker sleepers and two thin steel rails. */
private fun drawRailLine(pm: Pixmap, deg: Float, reach: Float) {
    val (dx, dy) = dir(deg); val px = -dy; val py = dx
    capsule(pm, C, C, dx, dy, reach, 8, BALLAST)                 // ballast band
    var s = -reach + 2
    while (s <= reach - 2) { capsule(pm, C + dx * s, C + dy * s, px, py, 7f, 1, SLEEPER); s += 5f } // sleepers
    for (off in floatArrayOf(-4f, 4f)) {                         // rails
        pm.setColor(RAILDK)
        var t = -reach
        while (t <= reach) { pm.fillCircle((C + dx * t + px * off).toInt(), (C + dy * t + py * off).toInt(), 1); t += 1f }
    }
}

private fun rail(deg: Float): Pixmap { val pm = blank(); drawRailLine(pm, deg, C); return pm }

private fun railHalf(): Pixmap {
    val pm = blank()
    // east-pointing half: ballast + sleepers + rails from center to east edge
    val px = 0f; val py = 1f
    var t = 0f
    capsule(pm, C + C / 2f, C, 1f, 0f, C / 2f, 8, BALLAST)
    var s = 2f
    while (s <= C - 2) { capsule(pm, C + s, C, px, py, 7f, 1, SLEEPER); s += 5f }
    for (off in floatArrayOf(-4f, 4f)) {
        pm.setColor(RAILDK); t = 0f
        while (t <= C) { pm.fillCircle((C + t).toInt(), (C + off).toInt(), 1); t += 1f }
    }
    return pm
}

private fun railCross(): Pixmap { val pm = blank(); drawRailLine(pm, 0f, C); drawRailLine(pm, 90f, C); return pm }

// -------------------------------------------------------------------- trains

private fun loco(deg: Float): Pixmap {
    val pm = blank(); val (dx, dy) = dir(deg)
    capsule(pm, C, C, dx, dy, 15f, 11, DARK)            // outline
    capsule(pm, C, C, dx, dy, 15f, 9, Color.WHITE)      // tintable body
    pm.setColor(DARK); pm.fillCircle((C + dx * 13).toInt(), (C + dy * 13).toInt(), 7) // smokebox front
    pm.setColor(DARK); pm.fillCircle((C + dx * 4).toInt(), (C + dy * 4).toInt(), 3)   // chimney
    pm.setColor(WIN); pm.fillCircle((C - dx * 7).toInt(), (C - dy * 7).toInt(), 4)    // cab window
    return pm
}

private fun car(deg: Float): Pixmap {
    val pm = blank(); val (dx, dy) = dir(deg); val px = -dy; val py = dx
    capsule(pm, C, C, dx, dy, 11f, 10, DARK)            // outline
    capsule(pm, C, C, dx, dy, 11f, 8, Color.WHITE)      // tintable body
    // dark roof ridge down the middle + coupler dots
    capsule(pm, C, C, dx, dy, 9f, 2, ROOF)
    pm.setColor(DARK)
    pm.fillCircle((C + dx * 12 + px * 0).toInt(), (C + dy * 12).toInt(), 2)
    pm.fillCircle((C - dx * 12).toInt(), (C - dy * 12).toInt(), 2)
    return pm
}

// -------------------------------------------------------------------- town / badge

private fun town(): Pixmap {
    val pm = blank()
    // walls (tintable) with dark outline
    pm.setColor(DARK); pm.fillRectangle(8, 16, S - 16, S - 24)
    pm.setColor(Color.WHITE); pm.fillRectangle(10, 18, S - 20, S - 28)
    // pitched roof
    for (i in 0..9) { pm.setColor(ROOF); pm.drawLine(7 + i, 16 - i + 9, S - 8 - i, 16 - i + 9) }
    // door + windows
    pm.setColor(ROOF); pm.fillRectangle((C - 4).toInt(), S - 16, 8, 8)
    pm.setColor(WIN); pm.fillRectangle(14, 24, 5, 5); pm.fillRectangle(S - 19, 24, 5, 5)
    return pm
}

private fun badge(): Pixmap {
    val pm = blank()
    pm.setColor(Color.WHITE); pm.fillCircle(C.toInt(), C.toInt(), 11)
    pm.setColor(0.86f, 0.16f, 0.13f, 1f); pm.fillCircle(C.toInt(), C.toInt(), 9)
    pm.setColor(Color.WHITE)
    pm.fillRectangle((C - 1).toInt(), (C - 6).toInt(), 3, 8)
    pm.fillRectangle((C - 1).toInt(), (C + 4).toInt(), 3, 3)
    return pm
}
