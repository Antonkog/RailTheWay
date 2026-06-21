package com.railtheway

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.RandomXS128
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.viewport.FitViewport
import com.railtheway.track.TrackNet
import com.railtheway.train.Train
import com.railtheway.town.Town
import com.railtheway.town.TownColor
import com.railtheway.world.GridMap
import com.railtheway.world.TerrainType
import com.railtheway.world.WorldGen
import kotlin.math.hypot

enum class GameState { MENU, PLAY, GAMEOVER }
enum class Mode(val label: String) { CONTROL("Control"), CONSTRUCTION("Construct"), BULLDOZER("Bulldoze"), ORDER("Order Train") }

class RailGame : ApplicationAdapter() {

    // rendering
    private lateinit var batch: SpriteBatch
    private lateinit var shape: ShapeRenderer
    private lateinit var font: BitmapFont
    private val layout = GlyphLayout()
    private lateinit var worldCam: OrthographicCamera
    private lateinit var hudCam: OrthographicCamera
    private lateinit var worldVp: FitViewport
    private lateinit var hudVp: FitViewport
    private val tmp = Vector3()

    // state
    private var state = GameState.MENU
    private var difficulty = Difficulty.EASY
    private lateinit var map: GridMap
    private lateinit var gen: WorldGen
    private val towns = ArrayList<Town>()
    private val trains = ArrayList<Train>()
    private lateinit var rng: RandomXS128

    private var balance = Config.START_BALANCE
    private var yearF = Config.START_YEAR.toFloat()
    private val year get() = yearF.toInt()
    private var speed = 1f
    private var paused = false
    private var yearsPerSec = 3f
    private var yearsToNextTown = 14f

    // input / build
    private var mode = Mode.CONSTRUCTION
    private var dragging = false
    private var dragStartX = 0; private var dragStartY = 0
    private var hoverX = 0; private var hoverY = 0

    // messages (newspaper banner + transient toasts)
    private var banner = ""
    private var bannerTimer = 0f
    private var toast = ""
    private var toastTimer = 0f

    override fun create() {
        batch = SpriteBatch()
        shape = ShapeRenderer()
        font = BitmapFont()
        worldCam = OrthographicCamera()
        hudCam = OrthographicCamera()
        worldVp = FitViewport(Config.VIEW_W, Config.VIEW_H, worldCam)
        hudVp = FitViewport(Config.VIEW_W, Config.VIEW_H, hudCam)
        Gdx.input.inputProcessor = GameInput()
    }

    private fun startGame(d: Difficulty) {
        difficulty = d
        map = GridMap()
        gen = WorldGen(seed = 1234L + d.ordinal * 17L)
        gen.generateTerrain(map)
        towns.clear()
        towns.addAll(gen.generateTowns(map, d))
        trains.clear()
        balance = Config.START_BALANCE
        yearF = Config.START_YEAR.toFloat()
        yearsToNextTown = 14f
        speed = 1f; paused = false
        mode = Mode.CONSTRUCTION
        rng = RandomXS128(99L + d.ordinal.toLong())
        worldCam.position.set(Config.VIEW_W / 2f, Config.VIEW_H / 2f, 0f)
        banner = "Year $year - connect the towns! Build track [2], then release trains [1]."
        bannerTimer = 6f
        state = GameState.PLAY
    }

    // ---------------------------------------------------------------- update

    override fun render() {
        val dt = Gdx.graphics.deltaTime.coerceAtMost(1f / 30f)
        when (state) {
            GameState.MENU -> Unit
            GameState.PLAY -> update(dt)
            GameState.GAMEOVER -> Unit
        }
        draw()
    }

    private fun update(dt: Float) {
        panCamera(dt)
        if (!paused) {
            val ydt = dt * yearsPerSec * speed
            yearF += ydt
            yearsToNextTown -= ydt
            if (yearsToNextTown <= 0f) { foundTown(); yearsToNextTown = 16f }
            updateTrains(dt)
            if (year >= difficulty.endYear) { state = GameState.GAMEOVER }
        }
        if (bannerTimer > 0f) bannerTimer -= dt
        if (toastTimer > 0f) toastTimer -= dt
    }

    private fun panCamera(dt: Float) {
        var dx = 0f; var dy = 0f
        val k = Gdx.input
        if (k.isKeyPressed(Input.Keys.A) || k.isKeyPressed(Input.Keys.LEFT)) dx -= 1f
        if (k.isKeyPressed(Input.Keys.D) || k.isKeyPressed(Input.Keys.RIGHT)) dx += 1f
        if (k.isKeyPressed(Input.Keys.S) || k.isKeyPressed(Input.Keys.DOWN)) dy -= 1f
        if (k.isKeyPressed(Input.Keys.W) || k.isKeyPressed(Input.Keys.UP)) dy += 1f
        worldCam.position.x += dx * Config.PAN_SPEED * dt
        worldCam.position.y += dy * Config.PAN_SPEED * dt
        clampCamera()
    }

    private fun clampCamera() {
        val mapW = map.width * Config.TILE
        val mapH = map.height * Config.TILE
        val hw = Config.VIEW_W / 2f; val hh = Config.VIEW_H / 2f
        worldCam.position.x = MathUtils.clamp(worldCam.position.x, hw, maxOf(hw, mapW - hw))
        worldCam.position.y = MathUtils.clamp(worldCam.position.y, hh, maxOf(hh, mapH - hh))
        worldCam.update()
    }

    private fun updateTrains(dt: Float) {
        val it = trains.iterator()
        while (it.hasNext()) {
            val tr = it.next()
            if (!tr.alive) { it.remove(); continue }
            advance(tr, dt)
            if (tr.finished) {
                val dest = towns.firstOrNull { it.id == tr.destTownId }
                if (dest != null && dest.color == tr.color) {
                    val pay = Config.DELIVERY_PAYOUT * tr.carriages
                    balance += pay
                    showToast("Delivered to ${dest.name}  +$$pay")
                }
                tr.alive = false
                it.remove()
            }
        }
        detectCrashes()
    }

    private fun advance(tr: Train, dt: Float) = tr.step(dt, map.width, Config.TRAIN_SPEED)

    private fun detectCrashes() {
        for (i in trains.indices) {
            val a = trains[i]; if (!a.alive) continue
            for (j in i + 1 until trains.size) {
                val b = trains[j]; if (!b.alive) continue
                if (a.occupantTile() == b.occupantTile()) {
                    val tile = a.occupantTile()
                    a.alive = false; b.alive = false
                    if (tile in 0 until map.width * map.height) map.track[tile] = false
                    banner = "TERRIBLE TRAIN CRASH! Two trains met on the same track - both destroyed, a rail section is damaged."
                    bannerTimer = 5f
                }
            }
        }
    }

    private fun foundTown() {
        if (towns.size >= difficulty.maxTowns) return
        val color = gen.nextFreeColor(towns) ?: return
        var guard = 0
        while (guard++ < 200) {
            val (tx, ty) = gen.randomEdgeTile(map, rng)
            if (towns.any { kotlin.math.abs(it.tileX - tx) + kotlin.math.abs(it.tileY - ty) < 8 }) continue
            map.terrain[map.idx(tx, ty)] = TerrainType.GRASS
            val t = Town(towns.size, color, tx, ty)
            towns.add(t)
            banner = "${t.name} was founded! Connect it to earn from ${color.display} trains."
            bannerTimer = 5f
            return
        }
    }

    // ---------------------------------------------------------------- actions

    /** Release a train from the town at (tx,ty), routed to a matching-colour town. */
    private fun releaseTrain(tx: Int, ty: Int, carriages: Int, paid: Boolean) {
        val src = towns.firstOrNull { it.tileX == tx && it.tileY == ty } ?: return
        if (!map.hasTrack(tx, ty)) { showToast("Build track onto ${src.name} first"); return }
        val srcIdx = map.idx(tx, ty)
        // candidate destinations: any other connected town with a path.
        val candidates = towns.filter { it.id != src.id && map.hasTrack(it.tileX, it.tileY) }
            .mapNotNull { d -> TrackNet.findPath(map, srcIdx, map.idx(d.tileX, d.tileY))?.let { d to it } }
        if (candidates.isEmpty()) { showToast("No route from ${src.name} yet"); return }
        if (paid) {
            if (balance < Config.ORDER_TRAIN_COST) { showToast("Not enough money"); return }
            balance -= Config.ORDER_TRAIN_COST
        }
        val (dest, path) = candidates[rng.nextInt(candidates.size)]
        trains.add(Train(color = dest.color, destTownId = dest.id, path = path, carriages = carriages))
        showToast("${dest.color.display} train dispatched -> ${dest.name}")
    }

    private fun bulldoze(tx: Int, ty: Int) {
        if (!map.inBounds(tx, ty)) return
        if (map.hasTrack(tx, ty)) {
            if (balance < Config.BULLDOZE_COST) { showToast("Not enough money"); return }
            balance -= Config.BULLDOZE_COST
            map.setTrack(tx, ty, false)
        } else if (map.terrainAt(tx, ty) != TerrainType.GRASS) {
            if (balance < Config.BULLDOZE_COST) { showToast("Not enough money"); return }
            balance -= Config.BULLDOZE_COST
            map.terrain[map.idx(tx, ty)] = TerrainType.GRASS
        }
    }

    private fun commitBuild() {
        if (!map.inBounds(dragStartX, dragStartY) || !map.inBounds(hoverX, hoverY)) return
        val tiles = TrackNet.lineTiles(dragStartX, dragStartY, hoverX, hoverY)
        val cost = TrackNet.lineCost(map, tiles)
        if (cost > balance) { showToast("Need $$cost - not enough money"); return }
        balance -= cost
        TrackNet.commitLine(map, tiles)
    }

    private fun showToast(s: String) { toast = s; toastTimer = 2.5f }

    // ---------------------------------------------------------------- input

    private inner class GameInput : InputAdapter() {
        override fun keyDown(keycode: Int): Boolean {
            when (state) {
                GameState.MENU -> when (keycode) {
                    com.badlogic.gdx.Input.Keys.NUM_1 -> startGame(Difficulty.EASY)
                    com.badlogic.gdx.Input.Keys.NUM_2 -> startGame(Difficulty.NORMAL)
                    com.badlogic.gdx.Input.Keys.NUM_3 -> startGame(Difficulty.HARD)
                }
                GameState.GAMEOVER -> when (keycode) {
                    com.badlogic.gdx.Input.Keys.R -> startGame(difficulty)
                    com.badlogic.gdx.Input.Keys.ESCAPE -> state = GameState.MENU
                }
                GameState.PLAY -> when (keycode) {
                    com.badlogic.gdx.Input.Keys.NUM_1 -> mode = Mode.CONTROL
                    com.badlogic.gdx.Input.Keys.NUM_2 -> mode = Mode.CONSTRUCTION
                    com.badlogic.gdx.Input.Keys.NUM_3 -> mode = Mode.BULLDOZER
                    com.badlogic.gdx.Input.Keys.NUM_4 -> mode = Mode.ORDER
                    com.badlogic.gdx.Input.Keys.SPACE -> paused = !paused
                    com.badlogic.gdx.Input.Keys.TAB -> speed = if (speed >= 2f) 1f else 2f
                    com.badlogic.gdx.Input.Keys.ESCAPE -> state = GameState.MENU
                }
            }
            return true
        }

        override fun touchDown(sx: Int, sy: Int, pointer: Int, button: Int): Boolean {
            if (state != GameState.PLAY) return false
            val (tx, ty) = screenToTile(sx, sy)
            when (mode) {
                Mode.CONSTRUCTION -> { dragging = true; dragStartX = tx; dragStartY = ty; hoverX = tx; hoverY = ty }
                Mode.BULLDOZER -> bulldoze(tx, ty)
                Mode.CONTROL -> releaseTrain(tx, ty, carriages = 1, paid = false)
                Mode.ORDER -> releaseTrain(tx, ty, carriages = 3, paid = true)
            }
            return true
        }

        override fun touchDragged(sx: Int, sy: Int, pointer: Int): Boolean {
            if (state != GameState.PLAY) return false
            val (tx, ty) = screenToTile(sx, sy); hoverX = tx; hoverY = ty
            return true
        }

        override fun touchUp(sx: Int, sy: Int, pointer: Int, button: Int): Boolean {
            if (state == GameState.PLAY && mode == Mode.CONSTRUCTION && dragging) {
                val (tx, ty) = screenToTile(sx, sy); hoverX = tx; hoverY = ty
                commitBuild()
            }
            dragging = false
            return true
        }

        override fun mouseMoved(sx: Int, sy: Int): Boolean {
            val (tx, ty) = screenToTile(sx, sy); hoverX = tx; hoverY = ty
            return false
        }
    }

    private fun screenToTile(sx: Int, sy: Int): Pair<Int, Int> {
        tmp.set(sx.toFloat(), sy.toFloat(), 0f)
        worldVp.unproject(tmp)
        return (tmp.x / Config.TILE).toInt() to (tmp.y / Config.TILE).toInt()
    }

    // ---------------------------------------------------------------- render

    private fun draw() {
        Gdx.gl.glClearColor(0.10f, 0.12f, 0.14f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        when (state) {
            GameState.MENU -> drawMenu()
            GameState.PLAY -> { drawWorld(); drawHud() }
            GameState.GAMEOVER -> { drawWorld(); drawGameOver() }
        }
    }

    private fun drawWorld() {
        worldVp.apply()
        shape.projectionMatrix = worldCam.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)

        // terrain
        shape.begin(ShapeRenderer.ShapeType.Filled)
        val t = Config.TILE
        for (y in 0 until map.height) for (x in 0 until map.width) {
            shape.color = map.terrainAt(x, y).color
            shape.rect(x * t, y * t, t, t)
        }
        // track
        shape.color = TRACK_COLOR
        for (y in 0 until map.height) for (x in 0 until map.width) {
            if (map.track[map.idx(x, y)]) shape.rect(x * t + 6, y * t + 6, t - 12, t - 12)
        }
        // towns: dark base + colored inset
        for (town in towns) {
            shape.color = Color.BLACK
            shape.rect(town.tileX * t, town.tileY * t, t, t)
            shape.color = town.color.color
            shape.rect(town.tileX * t + 3, town.tileY * t + 3, t - 6, t - 6)
        }
        // build preview
        if (mode == Mode.CONSTRUCTION && dragging) {
            val tiles = TrackNet.lineTiles(dragStartX, dragStartY, hoverX, hoverY)
            val cost = TrackNet.lineCost(map, tiles)
            shape.color = if (cost <= balance) PREVIEW_OK else PREVIEW_BAD
            for (p in tiles) {
                val px = TrackNet.packX(p); val py = TrackNet.packY(p)
                if (map.inBounds(px, py)) shape.rect(px * t + 4, py * t + 4, t - 8, t - 8)
            }
        }
        // trains + carriages
        for (tr in trains) {
            val (wx, wy) = trainWorldPos(tr)
            shape.color = Color.DARK_GRAY
            shape.rect(wx - 9, wy - 9, 18f, 18f)
            shape.color = tr.color.color
            shape.rect(wx - 6, wy - 6, 12f, 12f)
        }
        shape.end()

        // build-cost label above cursor
        if (mode == Mode.CONSTRUCTION && dragging) {
            val tiles = TrackNet.lineTiles(dragStartX, dragStartY, hoverX, hoverY)
            val cost = TrackNet.lineCost(map, tiles)
            batch.projectionMatrix = worldCam.combined
            batch.begin()
            font.color = if (cost <= balance) Color.YELLOW else Color.RED
            font.draw(batch, "$$cost", hoverX * t, hoverY * t + t + 14)
            batch.end()
            font.color = Color.WHITE
        }
    }

    private fun trainWorldPos(tr: Train): Pair<Float, Float> {
        if (tr.path.isEmpty()) return 0f to 0f
        val w = map.width
        val a = tr.path[tr.seg]
        val b = tr.path[(tr.seg + 1).coerceAtMost(tr.path.size - 1)]
        val ax = map.centerX(a % w); val ay = map.centerY(a / w)
        val bx = map.centerX(b % w); val by = map.centerY(b / w)
        return MathUtils.lerp(ax, bx, tr.t) to MathUtils.lerp(ay, by, tr.t)
    }

    private fun drawHud() {
        hudVp.apply()
        batch.projectionMatrix = hudCam.combined
        batch.begin()
        font.color = Color.WHITE
        val top = Config.VIEW_H - 10f
        font.draw(batch, "$ $balance", 12f, top)
        font.draw(batch, "Year $year / ${difficulty.endYear}", 180f, top)
        font.draw(batch, if (paused) "PAUSED" else "Speed ${speed.toInt()}x", 360f, top)
        font.draw(batch, "Mode: ${mode.label}", 520f, top)
        font.draw(batch, "[1]Control [2]Build [3]Bulldoze [4]Order  Space:pause Tab:speed  WASD:pan", 12f, 24f)

        if (bannerTimer > 0f && banner.isNotEmpty()) {
            layout.setText(font, banner)
            font.color = Color.YELLOW
            font.draw(batch, banner, (Config.VIEW_W - layout.width) / 2f, top - 30f)
            font.color = Color.WHITE
        }
        if (toastTimer > 0f && toast.isNotEmpty()) {
            layout.setText(font, toast)
            font.draw(batch, toast, (Config.VIEW_W - layout.width) / 2f, 70f)
        }
        batch.end()
    }

    private fun drawMenu() {
        hudVp.apply()
        batch.projectionMatrix = hudCam.combined
        batch.begin()
        font.color = Color.WHITE
        center("RAIL THE WAY", Config.VIEW_H - 140f, 2f)
        center("A railway-building game.  Choose a mode:", Config.VIEW_H - 220f, 1f)
        center("[1]  Easy    (to 1900, 2-4 towns)", Config.VIEW_H - 280f, 1f)
        center("[2]  Normal  (to 1960, up to 6 towns)", Config.VIEW_H - 310f, 1f)
        center("[3]  Hard    (to 2020, up to 8 towns)", Config.VIEW_H - 340f, 1f)
        center("Build track between same-coloured towns; deliver colour-matched trains to earn.", 120f, 1f)
        batch.end()
    }

    private fun drawGameOver() {
        hudVp.apply()
        batch.projectionMatrix = hudCam.combined
        batch.begin()
        font.color = Color.WHITE
        center("GAME OVER - Year ${difficulty.endYear} reached", Config.VIEW_H - 200f, 2f)
        center("Final balance: $ $balance", Config.VIEW_H - 280f, 1.5f)
        center("[R] Play again      [Esc] Menu", Config.VIEW_H - 360f, 1f)
        batch.end()
    }

    private fun center(text: String, y: Float, scale: Float) {
        font.data.setScale(scale)
        layout.setText(font, text)
        font.draw(batch, text, (Config.VIEW_W - layout.width) / 2f, y)
        font.data.setScale(1f)
    }

    override fun resize(width: Int, height: Int) {
        worldVp.update(width, height)
        hudVp.update(width, height, true)
        clampCameraIfPlaying()
    }

    private fun clampCameraIfPlaying() { if (state != GameState.MENU) clampCamera() }

    override fun dispose() {
        batch.dispose(); shape.dispose(); font.dispose()
    }

    companion object {
        private val TRACK_COLOR = Color(0.32f, 0.30f, 0.28f, 1f)
        private val PREVIEW_OK = Color(1f, 1f, 1f, 0.45f)
        private val PREVIEW_BAD = Color(1f, 0.2f, 0.2f, 0.5f)
    }
}
