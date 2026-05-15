# YippeeSpider — Design Spec

**Date:** 2026-05-11
**Status:** Approved (design)

## Goal

Build a Minecraft mod that replaces every spider and cave-spider sound (ambient, hurt, death, step) with the supplied audio clip `yippeeeeeeeeeeeeee.mp3`. The mod ships as a single `.jar` the user drops into `<.minecraft>/mods/`.

## Target

- **Minecraft:** 1.21.x (latest stable in the 1.21 series). The user's original "26.1" referred to macOS, not Minecraft; there is no Minecraft 26.x.
- **Loader:** NeoForge for 1.21.x.
- **Java:** 21 (required by Minecraft 1.21+).

## Scope decisions

| Decision | Choice |
| --- | --- |
| Sounds replaced | `entity.spider.ambient`, `entity.spider.hurt`, `entity.spider.death`, `entity.spider.step` |
| Mobs covered | Regular spider + cave spider (cave spider inherits these sound events from `Spider` in vanilla, so overriding `entity.spider.*` covers both for free) |
| Step behavior | Chaos mode — full clip plays on every footstep. Overlap is intended. |
| Audio format | Vorbis `.ogg`, mono, 44.1 kHz (Minecraft requirement) |

## Approach

**Chosen: resource-override mod.** The mod ships built-in resources that override the four vanilla spider sound events via `assets/minecraft/sounds.json` with `"replace": true`, pointing each event at our bundled `yippeespider:yippee` sound. No Mixin, no event handler, no runtime code beyond a minimal `@Mod` entrypoint required by NeoForge for the jar to be recognized as a mod.

Rejected alternatives:
- **Mixin into `Spider#getAmbientSound` etc.** — overkill, fragile across MC versions.
- **Pure resource pack (no mod).** — works, but the user asked for a mod and a jar install is cleaner.

## File layout

```
YippeeSpider/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradlew, gradlew.bat
├── gradle/wrapper/gradle-wrapper.{jar,properties}
├── src/main/java/com/yippeespider/YippeeSpider.java
└── src/main/resources/
    ├── META-INF/neoforge.mods.toml
    ├── pack.mcmeta
    └── assets/
        ├── minecraft/sounds.json
        └── yippeespider/sounds/yippee.ogg
```

### Component responsibilities

- **`build.gradle` / `settings.gradle` / `gradle.properties`** — NeoForge ModDevGradle setup pinning MC 1.21.x, NeoForge version, Java 21. Produces `build/libs/yippeespider-1.0.0.jar`.
- **`YippeeSpider.java`** — minimal `@Mod("yippeespider")` class. No game logic; exists so NeoForge recognizes the artifact as a mod.
- **`neoforge.mods.toml`** — mod id, version, name, license, loader version range, MC version range.
- **`pack.mcmeta`** — resource pack format version matching MC 1.21.x.
- **`assets/minecraft/sounds.json`** — four entries (`entity.spider.ambient/hurt/death/step`), each `"replace": true`, each with a single sound `yippeespider:yippee`.
- **`assets/yippeespider/sounds/yippee.ogg`** — the converted audio.

## Audio conversion

`yippeeeeeeeeeeeeee.mp3` is currently 44.1 kHz stereo MP3. Conversion:

```
ffmpeg -i yippeeeeeeeeeeeeee.mp3 -ac 1 -ar 44100 -c:a libvorbis -q:a 4 \
    src/main/resources/assets/yippeespider/sounds/yippee.ogg
```

- `-ac 1` → mono (smaller, fine for an entity sound).
- `-c:a libvorbis` → Vorbis (Minecraft's required codec).
- `-q:a 4` → reasonable quality/size balance.

ffmpeg will be installed via Homebrew (`brew install ffmpeg`) as a one-time setup step.

## Build & install

1. `./gradlew build` → `build/libs/yippeespider-1.0.0.jar`.
2. User drops the jar into `<.minecraft>/mods/` alongside a NeoForge installation matching the targeted MC 1.21.x version.

## Testing

- **Dev verification:** `./gradlew runClient` launches a dev Minecraft client. Use `/summon spider` and `/summon cave_spider`; deal damage and observe death to exercise all four sound events.
- **No automated tests.** This mod is a pure resource override with no code logic to assert against. The verification surface is "the sound plays in-game," which requires a human ear.

## Error handling / failure modes

- **MP3 file missing at build time** → build script fails fast with a clear message.
- **Wrong MC/NeoForge version at install** → NeoForge's own version-range check refuses to load the mod and surfaces a dialog. No additional handling needed.
- **Audio doesn't play** → almost always a sounds.json typo or wrong namespace; verified at dev-run time.

## Out of scope

- Other mob sounds.
- Per-spider-variant sound differentiation.
- Config to toggle individual sound events.
- Multi-loader support (Fabric / Forge).
- Multiple sound variants with random selection.
