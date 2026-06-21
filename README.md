# Rail The Way

A 2D railway-building game (a *Railway Valley 2*-style tycoon) built with **libGDX + Kotlin**,
playable on the **desktop** and in the **browser** (compiled to JavaScript/WebGL via **TeaVM**).

See `architecture-plan.md` for the full design.

## Gameplay

- Procedurally generated terrain: grass, forests (trees), rocks, lakes (water).
- Colored towns are seated at the map edges; new towns are founded as the years pass.
- **Build track** between towns — the cost depends on terrain (forest/water cost more to clear/bridge).
- **Release trains**: a colored train must reach the town of its matching color to earn money
  (more carriages → more income).
- Two trains meeting on the same track **crash** — both are destroyed and the rail section is damaged.
- Race the year clock; your final balance is your score.

### Controls

- `1` Control (release trains from a town) · `2` Construction (drag to lay track) ·
  `3` Bulldozer (remove track/clear terrain) · `4` Order Train (paid, 3 carriages)
- `WASD` / arrows: pan camera · `Space`: pause · `Tab`: toggle 1×/2× speed · `Esc`: menu

## Project layout

```
core/      shared Kotlin game logic + rendering (no platform deps)
lwjgl3/    desktop launcher + headless logic test
teavm/     web launcher + TeaVM build (-> JavaScript/WebGL)
```

Stack: libGDX 1.14.0, Kotlin 1.9.24, gdx-teavm 1.4.0, Gradle 8.10.2 (wrapper), JDK 17 target.

> Note: the web build uses **TeaVM, not GWT** — GWT cannot compile Kotlin; TeaVM compiles JVM
> bytecode to JS, so it works with Kotlin.

## Build & run

### Desktop

```bash
./gradlew :lwjgl3:run
```

### Headless logic smoke test (no display needed)

```bash
./gradlew :lwjgl3:verify
```

### Web (browser)

Compile the game to JavaScript:

```bash
./gradlew :teavm:buildJavaScript
```

Output is written to `teavm/build/dist/webapp/` (`index.html` + `app.js`). Serve it over HTTP and open
it — the game launches on page load:

```bash
cd teavm/build/dist/webapp && python3 -m http.server 8000
# open http://localhost:8000
```

(It must be served over HTTP, not opened as a `file://` URL.)
