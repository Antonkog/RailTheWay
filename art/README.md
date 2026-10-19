# Blender sprite experiment

Status: **experimental**. This directory contains a Blender-based art pipeline and a
side-by-side comparison against the current hand-drawn sprites. It does **not** replace
`assets/sprites/`; the game still loads the existing 48 px PNGs.

## What is here

| Path | Purpose |
| --- | --- |
| `blender/render_sprites.py` | Builds the 3D scenes and renders 256 px PNGs into `renders/raw/`. |
| `postprocess_sprites.py` | Adds dark outlines/shadows and composites the 3/4 pine icon over the grass tile into `renders/test/`. |
| `compare_sprites.py` | Per-asset sheet: current 32 px, Blender 32 px, Blender 64 px, and the 256 px source. |
| `scene_compare.py` | Small in-game mock-up showing both sprite sets at the real 32 px tile size. |
| `models/` | Editable `.blend` sources and portable `.glb` models for every rendered asset. |
| `renders/raw/` | Raw Blender renders. |
| `renders/test/` | Final test sprites, `compare.png`, and `scene_compare.png`. |

## Models

`blender/render_sprites.py` saves each scene before rendering it:

- `art/models/<name>.blend` — editable Blender source with camera, lights, meshes, and
  materials (Blender 5.2's compressed `.blend` format).
- `art/models/<name>.glb` — portable glTF 2.0 binary containing the selected meshes, with
  modifiers applied and without cameras/lights.

Names: `grass`, `forest` (the 3/4 pine icon composited into `trees.png`),
`forest_topdown` (the alternative straight-down fir), `water`, `rocks`, `rail_half`,
`loco`, `car`, `town`.

The GLB materials are intentionally simple: Blender's procedural noise/shingle nodes are
not fully representable in glTF, so the `.blend` is the authoritative source and the
`.glb` is the portable geometry/material preview.

## Run it

Blender 5.2.2 is installed at `/opt/homebrew/bin/blender`. The post-processing scripts use
the bundled Python (Pillow) from the DSH runtime:

```bash
blender --background --factory-startup --python art/blender/render_sprites.py

/Users/anton/.dsh/dsh-runtimes/dsh-primary-runtime/dependencies/python/bin/python3 \
    art/postprocess_sprites.py

/Users/anton/.dsh/dsh-runtimes/dsh-primary-runtime/dependencies/python/bin/python3 \
    art/compare_sprites.py

/Users/anton/.dsh/dsh-runtimes/dsh-primary-runtime/dependencies/python/bin/python3 \
    art/scene_compare.py
```

## What the experiment found

- **At the current 32 px tile size, the existing procedural sprites are still the better
  choice.** They were drawn for that size: strong silhouettes, dark outlines, flat bright
  colours. The 3D renders lose much of their detail when downscaled to 32 px, and the
  forest, track and town read less clearly than the current art.
- **At 64 px (simulated in `compare.png`), the 3D sources show their advantage**: shading,
  roof shingles, boiler bands, platform edges, and the pine layers become visible.
- The current code hardcodes `Config.TILE = 32f` and the train draw sizes (26 px loco,
  22 px carriage). A larger-tile experiment therefore needs both a tile-size/zoom change
  and proportional train draw sizes, not just new PNGs.
- The Blender set is a useful **source** for a future 2.5D pass: orthographic top-down for
  terrain/track/trains, 3/4-view icons for trees and the station, then outlines and soft
  shadows in Pillow to keep the 32 px silhouettes readable.

## Pipeline notes

- Materials use an explicit **sRGB → linear** conversion; Blender's Principled base colours
  are linear, while the game palette is sRGB.
- Terrain/track/trains use an orthographic top-down camera. Trees and the station use a
  3/4 camera and are composited over the grass tile, because a straight-down 3D tree reads
  as a bush.
- Transparent props get a dark silhouette outline and a soft contact shadow in
  `postprocess_sprites.py`; the town and trees also get a shadow. The game multiplies these
  sprites by the town colour, so white bodies/roofs are used for tintable parts.
- Sources are 256 px so they can be used unchanged at a larger tile size or with mipmaps
  later. The test itself does not modify `Config.TILE`.

## About the `dsh-blender` plugin

The linked `CheshireJCat/blender` repository is a **DeepSeek Harness plugin**, not a Blender
add-on or a libGDX dependency. It would add agent-side Blender tools/skills, but the same
asset pipeline can run with the installed Blender directly, as done here. Installing it
would change the DSH profile and require a harness restart; it is not needed for these test
renders. See the repo's README for the exact compatibility matrix.

## Next options

1. **Try a larger tile/zoom** (e.g. `Config.TILE = 64` plus scaled train draw sizes) and
   re-render the comparison; this is where the 3D set is most likely to win.
2. **Refine the weak assets** before any replacement: forest silhouettes, track rails, town
   walls/roof balance, and train body shape.
3. **Keep the current art** and improve it in `SpriteGen.kt` instead; it is already tuned for
   32 px.
4. **Install `dsh-blender`** only if you want the full agent-driven 3D modeling/validation
   workflow for future assets.
