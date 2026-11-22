# Core stays origin-agnostic; cross-cutting policy sits behind neutral hooks

The `core/` package must never import a franchise's classes, items or materials. When a
behaviour spans core and an origin, the mechanism lives in core and the origin supplies the
policy behind a neutral interface: for example movement locking now lives in `core/session`,
while which domains lock movement is answered by `DomainSession.isMovementLocked(...)` and the
lifecycle listener picks the outro. This keeps JJK-specific rules in `origins/jujutsukaisen`
without forcing core to know they exist.
