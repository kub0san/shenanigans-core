plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.hizeric"
version = "1.0-SNAPSHOT"

// E2E for the Paper 26.3 target is handled by Craftwire (native 26.3, MCP-callable).
// Plugwright was removed because it stops at MC 26.1. See the E2E spec in
// ~/.config/opencode/.planning/2026-11-22-mcp-plugin-skill-trim/e2e-spec-26.3.md.

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    // WorldEdit: optional at runtime, used only by the battleground wand + arena snapshot.
    maven("https://maven.enginehub.org/repo/")
    // InventoryFramework (battleground GUIs) is published through jitpack.
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation("org.spongepowered:configurate-yaml:4.2.0")
    // SQL-backed storage for the global kit, player records and the shop catalogue.
    // Both are bundled + relocated in shadowJar below (never expose their packages to callers).
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.54.1.0")
    // Battleground GUI foundation. Relocated in shadowJar below; never expose IF packages to callers.
    implementation("com.github.stefvanschie.inventoryframework:IF:0.12.2")
    compileOnly("org.joml:joml:1.10.9")
    // PacketEvents: provided at runtime by the PacketEvents plugin (paper-plugin.yml: required: true, load: BEFORE,
    // join-classpath: true). Never bundle it with shadow — the prebuilt plugin owns the API singleton and its injector.
    // Bump the version only together with the PacketEvents jar on the server.
    compileOnly("com.github.retrooper:packetevents-spigot:2.10.0")
    // WorldEdit is optional at runtime: provided by the server. Never bundle it with shadow.
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.2.15")

    // Tests only. Paper and WorldEdit are compileOnly for the plugin, but tests instantiate their types.
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    // MockBukkit v1.21 targets Paper 1.21.11 exactly (Java 21 bytecode, runs on our Java 25 toolchain).
    // Use for deterministic unit tests of pure logic; packet/GUI behaviour still needs the shared server.
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.116.3")
    testImplementation("com.sk89q.worldedit:worldedit-core:7.2.15")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // PacketEvents is compileOnly for the plugin, but loading PacketEventsTransport for its
    // unit tests makes the JVM verify methods whose types extend PacketWrapper. No test installs
    // an API singleton; only the fail-closed guard path is exercised at runtime.
    testRuntimeOnly("com.github.retrooper:packetevents-spigot:2.10.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

// Build the resource pack zip from the pixel-art sources and embed it in the jar.
val resourcePackZip = tasks.register<Zip>("resourcePackZip") {
    from("resourcepack")
    archiveFileName.set("shenanigans-resource-pack.zip")
    destinationDirectory.set(layout.buildDirectory.dir("generated/resourcepack"))
    exclude("**/.DS_Store")
}
tasks.processResources {
    from(resourcePackZip)
}

tasks {
    build {
        dependsOn(shadowJar)
    }
    shadowJar {
        relocate("org.spongepowered.configurate", "com.hizeric.shenaniganscore.libs.configurate")
        relocate("io.leangen.geantyref", "com.hizeric.shenaniganscore.libs.geantyref")
        relocate("com.github.stefvanschie.inventoryframework", "com.hizeric.shenaniganscore.libs.if")
        relocate("com.zaxxer.hikari", "com.hizeric.shenaniganscore.libs.hikari")
        // NEVER relocate org.sqlite: sqlitejdbc.dll exports JNI symbols for org.sqlite.core.NativeDB
        // by name — relocating the package makes the prebuilt natives unreachable by JNI mangling.
        // Hikari loads the driver by name ("org.sqlite.JDBC"), so leaving org.sqlite unrelocated is required.
        // Rewrite META-INF/services/java.sql.Driver to the relocated JDBC class name.
        mergeServiceFiles()
        // sqlite-jdbc ships a native library for every OS/arch it supports (~22 MB raw). This plugin
        // deploys only to ordinary Linux web servers, so keep glibc + musl for x86_64/aarch64 and
        // drop the rest. An allowlist also catches new platforms added by a future sqlite-jdbc bump.
        // Unit tests resolve the full dependency from Gradle's cache, so this only trims the shipped jar.
        val retainedSqliteNatives = listOf(
            "org/sqlite/native/Linux/x86_64/",
            "org/sqlite/native/Linux/aarch64/",
            "org/sqlite/native/Linux-Musl/x86_64/",
            "org/sqlite/native/Linux-Musl/aarch64/",
        )
        exclude { element: org.gradle.api.file.FileTreeElement ->
            val path = element.path
            path.startsWith("org/sqlite/native/") &&
                retainedSqliteNatives.none { prefix -> path.startsWith(prefix) }
        }
        minimize {
            // IF wires its own listeners reflectively; class-level reachability analysis would strip them.
            exclude(dependency("com.github.stefvanschie.inventoryframework:IF"))
            // Hikari loads the driver by name; sqlite-jdbc ships native resources loaded by class name.
            exclude(dependency("com.zaxxer:HikariCP"))
            exclude(dependency("org.xerial:sqlite-jdbc"))
        }
    }
    compileJava {
        options.encoding = "UTF-8"
    }
    compileTestJava {
        options.encoding = "UTF-8"
    }
    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "failed", "skipped")
            showStandardStreams = false
        }
    }
}