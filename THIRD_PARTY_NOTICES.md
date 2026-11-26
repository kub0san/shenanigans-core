# Third-party notices

This file records the third-party components bundled into the `shenanigans-core` shadow
jar. It lists versions and license names only; the full license text is available from
each upstream project. The shadow jar also embeds the license/notice files shipped by
those dependencies under `META-INF/` and a name-only `META-INF/NOTICE-*.txt` for every
runtime dependency.

| Component | Coordinates | Version | License |
| --- | --- | --- | --- |
| Configurate (YAML) | `org.spongepowered:configurate-yaml` (with `configurate-core`) | 4.2.0 | Apache License, Version 2.0 |
| Geantyref | `io.leangen.geantyref:geantyref` | 1.3.16 (transitive via Configurate; resolved by Gradle, not pinned in `build.gradle.kts`) | The Apache Software License, Version 2.0 |
| InventoryFramework (IF) | `com.github.stefvanschie.inventoryframework:IF` | 0.12.2 | The Unlicense |
| HikariCP | `com.zaxxer:HikariCP` | 5.1.0 | The Apache Software License, Version 2.0 |
| sqlite-jdbc | `org.xerial:sqlite-jdbc` | 3.54.1.0 | The Apache Software License, Version 2.0 |

Versions are taken from `build.gradle.kts` where the dependency is declared. Geantyref
is pulled in transitively by Configurate, so its resolved version is recorded instead.
License names come from the POM metadata of the resolved artifacts.
