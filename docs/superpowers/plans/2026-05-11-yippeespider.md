# YippeeSpider Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a NeoForge Minecraft mod that replaces all spider and cave-spider sounds (ambient, hurt, death, step) with the supplied `yippeeeeeeeeeeeeee.mp3`, packaged as a single jar that drops into `<.minecraft>/mods/`.

**Architecture:** Pure resource-override mod. Ships built-in resources at `assets/minecraft/sounds.json` with `"replace": true` entries pointing all four `entity.spider.*` events at a bundled `yippeespider:yippee` ogg. Cave spiders inherit those events from `Spider` in vanilla, so they are covered automatically. A minimal `@Mod` Java class exists only so NeoForge recognizes the jar as a mod.

**Tech Stack:** Minecraft 1.21.1, NeoForge 21.1.x, NeoForge ModDevGradle 2.x, Java 21, Gradle 8.x (via wrapper), ffmpeg for one-time MP3→OGG conversion.

**Verification model:** There are no automated tests — this mod has no code logic to assert against. Verification is `./gradlew build` (succeeds, produces jar) plus manual in-game checks via `./gradlew runClient`. The plan calls out the manual checks explicitly.

---

## File Structure

```
YippeeSpider/
├── .gitignore                                        # Create
├── settings.gradle                                   # Create — Gradle project name + ModDevGradle plugin repo
├── build.gradle                                      # Create — NeoForge ModDevGradle config, dependencies, runs
├── gradle.properties                                 # Create — MC/NeoForge versions, mod metadata vars
├── gradle/wrapper/gradle-wrapper.properties          # Create — pins Gradle 8.10
├── gradle/wrapper/gradle-wrapper.jar                 # Create (via `gradle wrapper`)
├── gradlew, gradlew.bat                              # Create (via `gradle wrapper`)
├── src/main/java/com/yippeespider/YippeeSpider.java  # Create — minimal @Mod entrypoint
└── src/main/resources/
    ├── META-INF/neoforge.mods.toml                   # Create — mod metadata
    ├── pack.mcmeta                                   # Create — resource pack version
    └── assets/
        ├── minecraft/sounds.json                     # Create — overrides 4 vanilla spider events
        └── yippeespider/sounds/yippee.ogg            # Create (via ffmpeg from yippeeeeeeeeeeeeee.mp3)
```

Each file has one responsibility; the only file with any logic is `YippeeSpider.java`, and that logic is "exist."

---

## Task 1: Install ffmpeg and convert MP3 → OGG

**Files:**
- Read: `yippeeeeeeeeeeeeee.mp3`
- Create: `src/main/resources/assets/yippeespider/sounds/yippee.ogg`

Minecraft only loads Vorbis `.ogg`. The source file is MP3, so we convert once up front. The converted file is committed to git — it's the real input to the build, and regenerating it requires the same ffmpeg setup on every machine otherwise.

- [ ] **Step 1: Verify ffmpeg is missing (sanity check)**

Run: `which ffmpeg`
Expected: empty output, exit code 1 — confirming we need to install it. If it's already installed, skip Step 2.

- [ ] **Step 2: Install ffmpeg via Homebrew**

Run: `brew install ffmpeg`
Expected: "🍺  /opt/homebrew/Cellar/ffmpeg/..." (or similar) and `which ffmpeg` now returns a path.

If Homebrew itself is missing, install it first per https://brew.sh, then re-run.

- [ ] **Step 3: Create the target directory**

Run: `mkdir -p src/main/resources/assets/yippeespider/sounds`
Expected: no output, directory exists.

- [ ] **Step 4: Convert MP3 to mono Vorbis OGG**

Run:
```bash
ffmpeg -y -i yippeeeeeeeeeeeeee.mp3 -ac 1 -ar 44100 -c:a libvorbis -q:a 4 \
    src/main/resources/assets/yippeespider/sounds/yippee.ogg
```

Expected: ffmpeg prints stream info and "size=... time=..." on the final line. Output file exists.

- [ ] **Step 5: Verify the OGG is valid Vorbis**

Run: `file src/main/resources/assets/yippeespider/sounds/yippee.ogg`
Expected: `Ogg data, Vorbis audio, mono, 44100 Hz, ~64000 bps`

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/assets/yippeespider/sounds/yippee.ogg
git commit -m "feat: add converted yippee.ogg sound asset"
```

---

## Task 2: Create `.gitignore`

**Files:**
- Create: `.gitignore`

Keep generated Gradle output, IDE noise, and Minecraft runtime files out of git.

- [ ] **Step 1: Write `.gitignore`**

```gitignore
# Gradle
.gradle/
build/
out/

# IDE
.idea/
*.iml
.vscode/
.classpath
.project
.settings/

# NeoForge dev runs
run/
runs/

# OS
.DS_Store

# Local overrides
*.local
```

- [ ] **Step 2: Commit**

```bash
git add .gitignore
git commit -m "chore: add .gitignore"
```

---

## Task 3: Create Gradle project files

**Files:**
- Create: `settings.gradle`
- Create: `gradle.properties`
- Create: `build.gradle`

These three files together drive NeoForge ModDevGradle. `gradle.properties` holds the versions so they're easy to bump; `settings.gradle` registers the plugin repository; `build.gradle` applies the plugin and declares the NeoForge dependency.

- [ ] **Step 1: Write `settings.gradle`**

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = 'https://maven.neoforged.net/releases' }
    }
}

plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
}

rootProject.name = 'yippeespider'
```

- [ ] **Step 2: Write `gradle.properties`**

```properties
# Build
org.gradle.jvmargs=-Xmx2G
org.gradle.daemon=true

# Mod
mod_id=yippeespider
mod_name=YippeeSpider
mod_version=1.0.0
mod_group_id=com.yippeespider
mod_authors=Austin Moser
mod_description=Replaces all spider and cave-spider sounds with a yippee.

# Toolchain
minecraft_version=1.21.1
minecraft_version_range=[1.21.1,1.22)
neoforge_version=21.1.93
neoforge_version_range=[21.1,)
loader_version_range=[4,)
```

- [ ] **Step 3: Write `build.gradle`**

```groovy
plugins {
    id 'java-library'
    id 'net.neoforged.moddev' version '2.0.78'
}

version = mod_version
group = mod_group_id

base {
    archivesName = mod_id
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

neoForge {
    version = neoforge_version

    parchment {
        mappingsVersion = '2024.11.17'
        minecraftVersion = '1.21.1'
    }

    runs {
        client {
            client()
            systemProperty 'neoforge.enabledGameTestNamespaces', mod_id
        }
        server {
            server()
            systemProperty 'neoforge.enabledGameTestNamespaces', mod_id
            programArgument '--nogui'
        }
    }

    mods {
        "${mod_id}" {
            sourceSet sourceSets.main
        }
    }
}

// Substitute mod metadata variables into neoforge.mods.toml
var generateModMetadata = tasks.register('generateModMetadata', ProcessResources) {
    var replaceProperties = [
        minecraft_version: minecraft_version,
        minecraft_version_range: minecraft_version_range,
        neoforge_version: neoforge_version,
        neoforge_version_range: neoforge_version_range,
        loader_version_range: loader_version_range,
        mod_id: mod_id,
        mod_name: mod_name,
        mod_license: 'MIT',
        mod_version: mod_version,
        mod_authors: mod_authors,
        mod_description: mod_description,
    ]
    inputs.properties replaceProperties
    expand replaceProperties
    from 'src/main/templates'
    into "${layout.buildDirectory.get()}/generated/sources/modMetadata"
}
sourceSets.main.resources.srcDir generateModMetadata
neoForge.ideSyncTask generateModMetadata

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}
```

- [ ] **Step 4: Commit**

```bash
git add settings.gradle gradle.properties build.gradle
git commit -m "build: add Gradle config for NeoForge ModDevGradle"
```

---

## Task 4: Generate the Gradle wrapper

**Files:**
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `gradle/wrapper/gradle-wrapper.jar`
- Create: `gradle/wrapper/gradle-wrapper.properties`

The wrapper lets anyone build the project without a system-wide Gradle install. We need a one-time bootstrap.

- [ ] **Step 1: Install Gradle temporarily (if missing) and generate the wrapper**

If `which gradle` is empty:
```bash
brew install gradle
```

Then:
```bash
gradle wrapper --gradle-version 8.10 --distribution-type bin
```

Expected: creates `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`. "BUILD SUCCESSFUL".

- [ ] **Step 2: Verify the wrapper works**

Run: `./gradlew --version`
Expected: prints "Gradle 8.10" and the JVM info. Java should be reported as 21 (or the project will fail later — install JDK 21 via `brew install openjdk@21` if needed and re-run).

- [ ] **Step 3: Commit**

```bash
git add gradlew gradlew.bat gradle/wrapper/
git commit -m "build: add Gradle 8.10 wrapper"
```

---

## Task 5: Add the NeoForge mod metadata template

**Files:**
- Create: `src/main/templates/META-INF/neoforge.mods.toml`

ModDevGradle's `generateModMetadata` task in Task 3 reads templates from `src/main/templates/` and substitutes `${var}` placeholders from `gradle.properties`. Putting the file under `templates/` (not `resources/`) is what lets the version numbers flow from one place.

- [ ] **Step 1: Create the template directory**

Run: `mkdir -p src/main/templates/META-INF`
Expected: directory exists.

- [ ] **Step 2: Write `src/main/templates/META-INF/neoforge.mods.toml`**

```toml
modLoader="javafml"
loaderVersion="${loader_version_range}"
license="${mod_license}"

[[mods]]
modId="${mod_id}"
version="${mod_version}"
displayName="${mod_name}"
authors="${mod_authors}"
description='''${mod_description}'''

[[dependencies.${mod_id}]]
    modId="neoforge"
    type="required"
    versionRange="${neoforge_version_range}"
    ordering="NONE"
    side="BOTH"

[[dependencies.${mod_id}]]
    modId="minecraft"
    type="required"
    versionRange="${minecraft_version_range}"
    ordering="NONE"
    side="BOTH"
```

- [ ] **Step 3: Commit**

```bash
git add src/main/templates/META-INF/neoforge.mods.toml
git commit -m "feat: add neoforge.mods.toml template"
```

---

## Task 6: Add `pack.mcmeta`

**Files:**
- Create: `src/main/resources/pack.mcmeta`

Declares the bundled resource pack version so Minecraft 1.21.1 loads our assets without warnings. Format `34` matches 1.21.0–1.21.1.

- [ ] **Step 1: Write `src/main/resources/pack.mcmeta`**

```json
{
  "pack": {
    "description": "YippeeSpider resources",
    "pack_format": 34
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/pack.mcmeta
git commit -m "feat: add pack.mcmeta for 1.21.1 (pack_format 34)"
```

---

## Task 7: Add the minimal Java mod entrypoint

**Files:**
- Create: `src/main/java/com/yippeespider/YippeeSpider.java`

NeoForge requires at least one `@Mod`-annotated class for a jar to load as a mod. No game logic needed — the sound replacement is pure resources.

- [ ] **Step 1: Write `src/main/java/com/yippeespider/YippeeSpider.java`**

```java
package com.yippeespider;

import net.neoforged.fml.common.Mod;

@Mod(YippeeSpider.MOD_ID)
public final class YippeeSpider {
    public static final String MOD_ID = "yippeespider";

    public YippeeSpider() {
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/yippeespider/YippeeSpider.java
git commit -m "feat: add minimal @Mod entrypoint"
```

---

## Task 8: Add the `sounds.json` overrides

**Files:**
- Create: `src/main/resources/assets/minecraft/sounds.json`

This is the heart of the mod. Each entry uses `"replace": true` to fully replace the vanilla sound event (instead of layering with the vanilla entries) and points to our bundled `yippeespider:yippee` sound. Because cave spiders share these four events with regular spiders in vanilla, this single file covers both mobs.

- [ ] **Step 1: Write `src/main/resources/assets/minecraft/sounds.json`**

```json
{
  "entity.spider.ambient": {
    "replace": true,
    "sounds": [
      { "name": "yippeespider:yippee", "stream": false }
    ]
  },
  "entity.spider.hurt": {
    "replace": true,
    "sounds": [
      { "name": "yippeespider:yippee", "stream": false }
    ]
  },
  "entity.spider.death": {
    "replace": true,
    "sounds": [
      { "name": "yippeespider:yippee", "stream": false }
    ]
  },
  "entity.spider.step": {
    "replace": true,
    "sounds": [
      { "name": "yippeespider:yippee", "stream": false }
    ]
  }
}
```

Notes on the fields:
- `"replace": true` — required to drop the vanilla sounds entirely instead of mixing ours in alongside.
- `"name": "yippeespider:yippee"` — namespace + path; resolves to `assets/yippeespider/sounds/yippee.ogg`.
- `"stream": false` — load fully into memory (clip is ~45 KB, far below the streaming threshold).

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/assets/minecraft/sounds.json
git commit -m "feat: override entity.spider.* sounds with yippee"
```

---

## Task 9: Build the mod jar

**Files:**
- No new files. This task exercises the build.

Now that everything is in place, run a full build. ModDevGradle will download Minecraft, NeoForge, and dependencies on the first run — expect the first build to take several minutes.

- [ ] **Step 1: Run the build**

Run: `./gradlew build`
Expected: a lot of download output on first run, then "BUILD SUCCESSFUL". A jar appears at `build/libs/yippeespider-1.0.0.jar`.

If the build fails:
- "Toolchain ... Java 21 ... not found" → install JDK 21: `brew install openjdk@21`, then `sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-21.jdk`. Re-run.
- "Could not resolve net.neoforged:neoforge:21.1.93" → version is stale; check https://projects.neoforged.net/neoforged/neoforge for the latest 21.1.x and update `gradle.properties`.
- TOML parse errors → template variable typo; re-check `src/main/templates/META-INF/neoforge.mods.toml` and `gradle.properties`.

- [ ] **Step 2: Inspect the jar contents**

Run: `unzip -l build/libs/yippeespider-1.0.0.jar | head -40`
Expected: lists `META-INF/neoforge.mods.toml`, `pack.mcmeta`, `assets/minecraft/sounds.json`, `assets/yippeespider/sounds/yippee.ogg`, and `com/yippeespider/YippeeSpider.class`.

- [ ] **Step 3: Verify the metadata file has substituted variables**

Run: `unzip -p build/libs/yippeespider-1.0.0.jar META-INF/neoforge.mods.toml`
Expected: no `${...}` placeholders left — every variable resolved (e.g. `modId="yippeespider"`, `version="1.0.0"`).

- [ ] **Step 4: Commit (only if anything was tracked; build artifacts are gitignored)**

```bash
git status
```

If nothing is staged, skip the commit — the build produced only `.gitignore`d files.

---

## Task 10: Manual in-game verification

**Files:**
- No file changes.

The actual "does it sound like yippee" check has to happen with ears in Minecraft. ModDevGradle's `runClient` task launches a dev client with the mod already loaded.

- [ ] **Step 1: Launch the dev client**

Run: `./gradlew runClient`
Expected: a Minecraft client window opens at the title screen with "YippeeSpider" in the mod list (Mods button).

- [ ] **Step 2: Create a creative test world**

In-game: Singleplayer → Create New World → Game Mode: Creative → Cheats: ON → Create.

- [ ] **Step 3: Verify ambient + step**

Open chat (`T`) and run:
```
/summon spider ~ ~ ~1
```
Expected: a spider spawns next to you. Within a few seconds you should hear the yippee clip (ambient). Walk around the spider; as it moves, you should hear yippee clips overlap (step events firing ~4×/sec — chaos mode as designed).

- [ ] **Step 4: Verify hurt + death**

While near the spider, run:
```
/effect give @e[type=spider] minecraft:instant_damage 1 5
```
Or just hit the spider with a sword (give yourself one: `/give @s minecraft:diamond_sword`).
Expected: a yippee on the damage tick, and another yippee on death.

- [ ] **Step 5: Verify cave spider**

Run:
```
/summon cave_spider ~ ~ ~1
```
Then repeat Steps 3–4. Expected: identical yippee behavior — same sounds, since cave spiders inherit the events.

- [ ] **Step 6: Exit the client**

Close the Minecraft window. The Gradle task ends with "BUILD SUCCESSFUL" (or "FAILED" if the client crashed — investigate any stack trace before declaring done).

- [ ] **Step 7: Final ship-it commit (only if any tracked files changed)**

```bash
git status
```

If nothing is dirty, the mod is ready. The jar at `build/libs/yippeespider-1.0.0.jar` is the deliverable — drop it in `<.minecraft>/mods/` next to a NeoForge 21.1.x installation for MC 1.21.1.

---

## Definition of done

- `./gradlew build` produces `build/libs/yippeespider-1.0.0.jar` cleanly.
- The jar contains the four overridden sound events and the converted `yippee.ogg`.
- In dev client (`./gradlew runClient`): regular spiders and cave spiders play the yippee on ambient, hurt, death, and step events. No vanilla spider sounds remain.
