package com.railtheway

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.RandomXS128
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.viewport.FitViewport
import com.railtheway.track.Dirs
import com.railtheway.track.TrackNet
import com.railtheway.train.Train
import com.railtheway.train.TrainSim
import com.railtheway.town.Town
import com.railtheway.town.TownColor
import com.railtheway.world.GridMap
import com.railtheway.world.TerrainType
import com.railtheway.world.WorldGen

enum class GameState { MENU, PLAY, GAMEOVER }
enum class Mode(val label: String) { CONTROL("Control"), CONSTRUCTION("Construct"), BULLDOZER("Bulldoze"), ORDER("Order Train") }

class RailGame : ApplicationAdapter() {

    private lateinit var batch: SpriteBatch
    private lateinit var shape: ShapeRenderer
    private lateinit var font: BitmapFont
    private val layout = GlyphLayout()
    private lateinit var worldCam: OrthographicCamera
    private lateinit var hudCam: OrthographicCamera
    private lateinit var worldVp: FitViewport
    private lateinit var hudVp: FitViewport
    private val tmp = Vector3()

    private val textures = ArrayList<Texture>()
    private val region = HashMap<String, TextureRegion>()

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
    private var yearsToNextTown = 14f
    private var yearsToNextTrain = 2f

    private var mode = Mode.CONSTRUCTION
    private var orientation = 0          // 0..3 -> Dirs.AXES
    private var hoverX = 0; private var hoverY = 0

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
        loadSprites()
        Gdx.input.inputProcessor = GameInput()
    }

    private fun loadSprites() {
        val names = listOf(
            "grass", "water", "rocks", "trees",
            "rail_half_e", "loco_ew", "car_ew", "town", "badge",
        )
        for (n in names) {
            val t = Texture(Gdx.files.internal("sprites/$n.png"))
            t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            textures.add(t)
            region[n] = TextureRegion(t)
        }
    }

    private fun terrainSprite(tt: TerrainType): TextureRegion = region[when (tt) {
        TerrainType.GRASS -> "grass"; TerrainType.TREES -> "trees"
        TerrainType.WATER -> "water"; TerrainType.ROCKS -> "rocks"
    }]!!

    private fun startGame(d: Difficulty) {
        difficulty = d
        map = GridMap()
        gen = WorldGen(seed = 1234L + d.ordinal * 17L)
        gen.generateTerrain(map)
        towns.clear(); towns.addAll(gen.generateTowns(map, d))
        trains.clear()
        balance = Config.START_BALANCE
        yearF = Config.START_YEAR.toFloat()
        yearsToNextTown = 14f
        yearsToNextTrain = 2f
        speed = 1f; paused = false
        mode = Mode.CONSTRUCTION; orientation = 0
        rng = RandomXS128(99L + d.ordinal.toLong())
        worldCam.position.set(Config.VIEW_W / 2f, Config.VIEW_H / 2f, 0f)
        banner = "Year $year - build 3-tile track [2] (Q/E rotate). Trains depart on their own; switch junctions [1] to route them home."
        bannerTimer = 8f
        state = GameState.PLAY
    }

    // ---- helpers -----------------------------------------------------------

    private fun townAtTile(tileIdx: Int): Town? {
        val tx = tileIdx % map.width; val ty = tileIdx / map.width
        return towns.firstOrNull { it.tileX == tx && it.tileY == ty }
    }

    private fun townColorAt(tileIdx: Int): TownColor? = townAtTile(tileIdx)?.color

    // ---- update ------------------------------------------------------------

    override fun render() {
        val dt = Gdx.graphics.deltaTime.coerceAtMost(1f / 30f)
        if (state == GameState.PLAY) update(dt)
        draw()
    }

    private fun update(dt: Float) {
        panCamera(dt)
        if (!paused) {
            val ydt = dt * Config.YEARS_PER_SEC * speed
            yearF += ydt
            yearsToNextTown -= ydt
            if (yearsToNextTown <= 0f) { foundTown(); yearsToNextTown = 16f }
            yearsToNextTrain -= ydt
            if (yearsToNextTrain <= 0f) { autoSpawn(); yearsToNextTrain = 1f + rng.nextFloat() * 4f }
            updateTrains(dt * speed)
            if (year >= difficulty.endYear) state = GameState.GAMEOVER
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
        val mapW = map.width * Config.TILE; val mapH = map.height * Config.TILE
        val hw = Config.VIEW_W / 2f; val hh = Config.VIEW_H / 2f
        worldCam.position.x = MathUtils.clamp(worldCam.position.x, hw, maxOf(hw, mapW - hw))
        worldCam.position.y = MathUtils.clamp(worldCam.position.y, hh, maxOf(hh, mapH - hh))
        worldCam.update()
    }

    private fun updateTrains(edt: Float) {
        val it = trains.iterator()
        while (it.hasNext()) {
            val tr = it.next()
            if (!tr.alive) { it.remove(); continue }
            val res = TrainSim.step(tr, edt, map, Config.TRAIN_SPEED, ::townColorAt)
            if (res == TrainSim.Result.DELIVERED) {
                val dest = townAtTile(tr.toTile) ?: towns.firstOrNull { it.id == tr.destTownId }
                val pay = Config.DELIVERY_PAYOUT * tr.carriages
                balance += pay
                showToast("Delivered to ${dest?.name ?: tr.color.display}  +$$pay")
                it.remove()
            } else if (!tr.alive) it.remove()
        }
        detectCrashes()
    }

    private fun detectCrashes() {
        for (i in trains.indices) {
            val a = trains[i]; if (!a.alive) continue
            for (j in i + 1 until trains.size) {
                val b = trains[j]; if (!b.alive) continue
                if (a.occupantTile() == b.occupantTile()) {
                    val tile = a.occupantTile()
                    a.alive = false; b.alive = false
                    TrackNet.bulldoze(map, tile % map.width, tile / map.width)
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
            banner = "${t.name} was founded! Connect it to route ${color.display} trains home."
            bannerTimer = 5f
            return
        }
    }

    // ---- actions -----------------------------------------------------------

    private fun autoSpawn() {
        val connected = towns.filter { map.hasTrack(it.tileX, it.tileY) }
        if (connected.isEmpty() || towns.size < 2) return
        spawnTrain(connected[rng.nextInt(connected.size)], carriages = 1)
    }

    /** Spawn a train at a town heading outward; its colour is another town it must reach. */
    private fun spawnTrain(src: Town, carriages: Int): Boolean {
        val ti = map.idx(src.tileX, src.tileY)
        if (!map.hasTrack(ti)) { showToast("Build track onto ${src.name} first"); return false }
        val dirs = TrackNet.connectedDirs(map, ti)
        if (dirs.isEmpty()) return false
        val others = towns.filter { it.id != src.id }
        if (others.isEmpty()) return false
        val dest = others[rng.nextInt(others.size)]
        val pref = map.switchPref[ti]
        val d = if (pref >= 0 && dirs.contains(pref)) pref else dirs[0]
        val nb = map.idx(src.tileX + Dirs.DX[d], src.tileY + Dirs.DY[d])
        trains.add(Train(dest.color, dest.id, carriages, fromTile = ti, toTile = nb))
        showToast("${dest.color.display} train left ${src.name} -> heading to ${dest.name}")
        return true
    }

    private fun controlClick(tx: Int, ty: Int) {
        if (!map.inBounds(tx, ty)) return
        val tile = map.idx(tx, ty)
        val tr = trains.firstOrNull { it.alive && it.occupantTile() == tile }
        if (tr != null) { tr.reverse(); showToast("Train reversed"); return }
        if (TrackNet.degree(map, tile) >= 3) {
            TrackNet.cycleSwitch(map, tile)
            showToast("Switch set"); return
        }
        val town = townAtTile(tile)
        if (town != null) spawnTrain(town, carriages = 1)
    }

    private fun placeAt(tx: Int, ty: Int) {
        if (!TrackNet.pieceValid(map, tx, ty, orientation)) { showToast("Can't place there"); return }
        val cost = TrackNet.pieceCost(map, tx, ty, orientation)
        if (cost > balance) { showToast("Need $$cost - not enough money"); return }
        balance -= cost
        TrackNet.placePiece(map, tx, ty, orientation)
    }

    private fun bulldozeAt(tx: Int, ty: Int) {
        if (!map.inBounds(tx, ty)) return
        if (balance < Config.BULLDOZE_COST) { showToast("Not enough money"); return }
        if (TrackNet.bulldoze(map, tx, ty)) balance -= Config.BULLDOZE_COST
    }

    private fun showToast(s: String) { toast = s; toastTimer = 2.5f }

    // ---- input -------------------------------------------------------------

    private inner class GameInput : InputAdapter() {
        override fun keyDown(keycode: Int): Boolean {
            when (state) {
                GameState.MENU -> when (keycode) {
                    Input.Keys.NUM_1 -> startGame(Difficulty.EASY)
                    Input.Keys.NUM_2 -> startGame(Difficulty.NORMAL)
                    Input.Keys.NUM_3 -> startGame(Difficulty.HARD)
                }
                GameState.GAMEOVER -> when (keycode) {
                    Input.Keys.R -> startGame(difficulty)
                    Input.Keys.ESCAPE -> state = GameState.MENU
                }
                GameState.PLAY -> when (keycode) {
                    Input.Keys.NUM_1 -> mode = Mode.CONTROL
                    Input.Keys.NUM_2 -> mode = Mode.CONSTRUCTION
                    Input.Keys.NUM_3 -> mode = Mode.BULLDOZER
                    Input.Keys.NUM_4 -> mode = Mode.ORDER
                    Input.Keys.Q -> orientation = (orientation + 3) % 4
                    Input.Keys.E -> orientation = (orientation + 1) % 4
                    Input.Keys.SPACE -> paused = !paused
                    Input.Keys.TAB -> speed = if (speed >= 2f) 1f else 2f
                    Input.Keys.ESCAPE -> state = GameState.MENU
                }
            }
            return true
        }

        override fun touchDown(sx: Int, sy: Int, pointer: Int, button: Int): Boolean {
            if (state != GameState.PLAY) return false
            val (tx, ty) = screenToTile(sx, sy)
            when (mode) {
                Mode.CONSTRUCTION -> placeAt(tx, ty)
                Mode.BULLDOZER -> bulldozeAt(tx, ty)
                Mode.CONTROL -> controlClick(tx, ty)
                Mode.ORDER -> {
                    val town = townAtTile(map.idx(tx.coerceIn(0, map.width - 1), ty.coerceIn(0, map.height - 1)))
                    if (town != null && map.inBounds(tx, ty)) {
                        if (balance < Config.ORDER_TRAIN_COST) showToast("Not enough money")
                        else if (spawnTrain(town, carriages = 3)) balance -= Config.ORDER_TRAIN_COST
                    }
                }
            }
            return true
        }

        override fun touchDragged(sx: Int, sy: Int, pointer: Int): Boolean {
            val (tx, ty) = screenToTile(sx, sy); hoverX = tx; hoverY = ty
            return false
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

    // ---- render ------------------------------------------------------------

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
        val t = Config.TILE
        Gdx.gl.glEnable(GL20.GL_BLEND)

        // ---- sprite pass ----
        batch.projectionMatrix = worldCam.combined
        batch.begin()
        batch.setColor(Color.WHITE)
        // terrain
        for (y in 0 until map.height) for (x in 0 until map.width) {
            batch.draw(terrainSprite(map.terrainAt(x, y)), x * t, y * t, t, t)
        }
        // track: a half-edge sprite rotated toward each connected direction (dir d == d*45 deg)
        val half = region["rail_half_e"]!!
        for (i in 0 until map.width * map.height) {
            if (!map.hasTrack(i)) continue
            val bx = (i % map.width) * t; val by = (i / map.width) * t
            for (d in 0..7) if (TrackNet.connected(map, i, d)) {
                batch.draw(half, bx, by, t / 2f, t / 2f, t, t, 1f, 1f, d * 45f)
            }
        }
        // towns (white body tinted to town colour) + name label
        val townTex = region["town"]!!
        for (town in towns) {
            batch.color = town.color.color
            batch.draw(townTex, town.tileX * t, town.tileY * t, t, t)
            batch.setColor(Color.WHITE)
        }
        font.color = Color.BLACK; font.data.setScale(0.8f)
        for (town in towns) {
            layout.setText(font, town.name)
            font.draw(batch, town.name, map.centerX(town.tileX) - layout.width / 2f, town.tileY * t - 2f)
        }
        font.data.setScale(1f); font.color = Color.WHITE
        // trains: loco + carriages, rotated to travel angle and tinted to colour
        val loco = region["loco_ew"]!!; val car = region["car_ew"]!!
        for (tr in trains) {
            val fx = map.centerX(tr.fromTile % map.width); val fy = map.centerY(tr.fromTile / map.width)
            val tx2 = map.centerX(tr.toTile % map.width); val ty2 = map.centerY(tr.toTile / map.width)
            val px = MathUtils.lerp(fx, tx2, tr.t); val py = MathUtils.lerp(fy, ty2, tr.t)
            var ux = tx2 - fx; var uy = ty2 - fy
            val len = Math.hypot(ux.toDouble(), uy.toDouble()).toFloat().coerceAtLeast(0.001f)
            ux /= len; uy /= len
            val ang = MathUtils.atan2(uy, ux) * MathUtils.radiansToDegrees
            batch.color = tr.color.color
            for (c in tr.carriages downTo 1) {
                val bxp = px - ux * c * 15f; val byp = py - uy * c * 15f
                batch.draw(car, bxp - 11f, byp - 11f, 11f, 11f, 22f, 22f, 1f, 1f, ang)
            }
            batch.draw(loco, px - 13f, py - 13f, 13f, 13f, 26f, 26f, 1f, 1f, ang)
            batch.setColor(Color.WHITE)
        }
        // destination badges above each incoming train's target town
        val badge = region["badge"]!!
        for (tr in trains) {
            val dest = towns.firstOrNull { it.id == tr.destTownId } ?: continue
            batch.draw(badge, map.centerX(dest.tileX) - 9f, dest.tileY * t + t + 2f, 18f, 18f)
        }
        batch.end()

        // ---- overlay pass: switches + build preview ----
        shape.projectionMatrix = worldCam.combined
        shape.begin(ShapeRenderer.ShapeType.Filled)
        for (i in 0 until map.width * map.height) {
            if (TrackNet.degree(map, i) < 3) continue
            val cx = map.centerX(i % map.width); val cy = map.centerY(i / map.width)
            shape.color = SWITCH_COLOR; shape.circle(cx, cy, t * 0.20f, 12)
            val pref = map.switchPref[i]
            if (pref >= 0) {
                shape.color = SWITCH_SET
                shape.rectLine(cx, cy, cx + Dirs.DX[pref] * t * 0.42f, cy + Dirs.DY[pref] * t * 0.42f, t * 0.11f)
            }
        }
        if (mode == Mode.CONSTRUCTION) {
            val valid = TrackNet.pieceValid(map, hoverX, hoverY, orientation)
            val cost = if (valid) TrackNet.pieceCost(map, hoverX, hoverY, orientation) else -1
            shape.color = if (valid && cost <= balance) PREVIEW_OK else PREVIEW_BAD
            val pt = TrackNet.pieceTiles(hoverX, hoverY, orientation)
            var k = 0
            while (k < 6) {
                val px = pt[k]; val py = pt[k + 1]
                if (map.inBounds(px, py)) shape.rect(px * t + 3, py * t + 3, t - 6, t - 6)
                k += 2
            }
        }
        shape.end()

        // cost label
        if (mode == Mode.CONSTRUCTION && TrackNet.pieceValid(map, hoverX, hoverY, orientation)) {
            val cost = TrackNet.pieceCost(map, hoverX, hoverY, orientation)
            batch.projectionMatrix = worldCam.combined
            batch.begin()
            font.color = if (cost <= balance) Color.YELLOW else Color.RED
            font.draw(batch, "$$cost", hoverX * t, hoverY * t + t + 14)
            batch.end(); font.color = Color.WHITE
        }
    }

    private fun drawHud() {
        hudVp.apply()
        batch.projectionMatrix = hudCam.combined
        batch.begin()
        font.color = Color.WHITE
        val top = Config.VIEW_H - 10f
        font.draw(batch, "$ $balance", 12f, top)
        font.draw(batch, "Year $year / ${difficulty.endYear}", 170f, top)
        font.draw(batch, if (paused) "PAUSED" else "Speed ${speed.toInt()}x", 340f, top)
        font.draw(batch, "Mode: ${mode.label}", 470f, top)
        if (mode == Mode.CONSTRUCTION) font.draw(batch, "Piece: ${Dirs.AXIS_NAME[orientation]}  (Q/E rotate)", 640f, top)
        font.draw(batch, "[1]Control(reverse train / set switch / send) [2]Build [3]Bulldoze [4]Order  Space:pause Tab:speed WASD:pan", 12f, 24f)

        if (bannerTimer > 0f && banner.isNotEmpty()) {
            layout.setText(font, banner); font.color = Color.YELLOW
            font.draw(batch, banner, (Config.VIEW_W - layout.width) / 2f, top - 30f); font.color = Color.WHITE
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
        batch.begin(); font.color = Color.WHITE
        center("RAIL THE WAY", Config.VIEW_H - 140f, 2f)
        center("Build railways, switch the junctions, get each coloured train to its city.", Config.VIEW_H - 220f, 1f)
        center("[1]  Easy    (to 1900, 2-4 towns)", Config.VIEW_H - 280f, 1f)
        center("[2]  Normal  (to 1960, up to 6 towns)", Config.VIEW_H - 310f, 1f)
        center("[3]  Hard    (to 2020, up to 8 towns)", Config.VIEW_H - 340f, 1f)
        batch.end()
    }

    private fun drawGameOver() {
        hudVp.apply()
        batch.projectionMatrix = hudCam.combined
        batch.begin(); font.color = Color.WHITE
        center("GAME OVER - Year ${difficulty.endYear} reached", Config.VIEW_H - 200f, 2f)
        center("Final balance: $ $balance", Config.VIEW_H - 280f, 1.5f)
        center("[R] Play again      [Esc] Menu", Config.VIEW_H - 360f, 1f)
        batch.end()
    }

    private fun center(text: String, y: Float, scale: Float) {
        font.data.setScale(scale); layout.setText(font, text)
        font.draw(batch, text, (Config.VIEW_W - layout.width) / 2f, y); font.data.setScale(1f)
    }

    override fun resize(width: Int, height: Int) {
        worldVp.update(width, height)
        hudVp.update(width, height, true)
        if (state != GameState.MENU) clampCamera()
    }

    override fun dispose() {
        batch.dispose(); shape.dispose(); font.dispose()
        textures.forEach { it.dispose() }
    }

    companion object {
        private val SWITCH_COLOR = Color(0.95f, 0.85f, 0.25f, 0.9f)
        private val SWITCH_SET = Color(1f, 1f, 1f, 0.9f)
        private val PREVIEW_OK = Color(1f, 1f, 1f, 0.45f)
        private val PREVIEW_BAD = Color(1f, 0.2f, 0.2f, 0.5f)
    }
}
