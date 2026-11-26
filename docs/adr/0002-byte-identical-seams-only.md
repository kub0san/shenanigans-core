# Fold only byte-identical helper bodies into shared seams; keep rigs minimal

A local helper is moved into a shared `core/` seam only when its body is byte-identical across
callers and it can take explicit primitives rather than a builder-local `Rig` or domain type
(e.g. `tumble`, `poseHalos`, `MathUtil.scaledCount`). Anything that would require rewiring
call sites around a technique-specific type — such as `centerOf` or the one-line marker
records — stays local. Rigs stay deliberately thin: `DisplayRig` exposes only `entities()`, and
`LightingRig` adds only `lights()`.

_Consequences_: seams like `TechniqueDisplayOps`, `TechniqueEraseOps` and `TechniqueLighting`
are narrow by design. A helper that needs a new parameter is a signal it is technique-specific,
not a reason to widen the shared interface.
