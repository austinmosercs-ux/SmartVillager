# Creating a new mod from this template

This walks through cloning YippeeSpider into a fresh NeoForge mod. The shape generalizes to any small mod — sound replacement, texture swap, simple item, simple block — without restructuring.

## Quick start (automated)

There's a bootstrap script that runs steps 1–7 below for you:

```sh
./new-mod.sh <mod_id> [mod_name] [target_dir]

# example
./new-mod.sh creepertunes CreeperTunes
```

It copies the repo, renames the Java package + `@Mod` class, patches `gradle.properties`, moves the assets directory, rewrites README/CLAUDE.md, and runs `git init` with an initial commit. Then jump to **step 8 (Build)**.

The manual walkthrough below documents what the script does, in case you need to do it by hand or want to understand the moving parts.

## Two ways to start a new repo

- **Local:** run `./new-mod.sh ...` from this directory (above).
- **GitHub:** mark this repo as a [template repository](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-template-repository) in its GitHub settings, then click "Use this template" on github.com to create a new repo. You'll still need to run `./new-mod.sh` (or do the renaming by hand) after cloning, because the script renames things GitHub's template feature doesn't.

## 0. Prerequisites

- JDK 25 installed (`brew install openjdk@25` on macOS)
- Familiarity with the matrix in [CLAUDE.md](../CLAUDE.md) (Gradle 9.0.0, NeoForge 26.1.2.44-beta, foojay-resolver 1.0.0). If you bump any of these, also check the gotchas section there.

## 1. Copy the directory

```sh
cp -R ~/Documents/YippeeSpider ~/Documents/MyNewMod
cd ~/Documents/MyNewMod
rm -rf .git build .gradle bin
git init
```

## 2. Pick names

Decide on:

| Property         | YippeeSpider example          | Yours |
| ---------------- | ----------------------------- | ----- |
| `mod_id`         | `yippeespider` (lowercase, no spaces) | |
| `mod_name`       | `YippeeSpider`                | |
| `mod_group_id`   | `com.yippeespider`            | |
| `mod_version`    | `1.0.0`                       | |
| `mod_description`| short sentence                | |

`mod_id` and `mod_group_id` are the load-bearing ones; everything else can be edited later.

## 3. Update `gradle.properties`

Change every value under `# Mod`:

```properties
mod_id=mynewmod
mod_name=MyNewMod
mod_version=1.0.0
mod_group_id=com.mynewmod
mod_authors=Your Name
mod_description=One-line summary.
```

Leave the `# Toolchain` block alone unless you have a reason to target a different MC/NeoForge version.

## 4. Rename the Java package and `@Mod` class

```sh
# Move the package directory
mv src/main/java/com/yippeespider src/main/java/com/mynewmod

# Rename the class file
mv src/main/java/com/mynewmod/YippeeSpider.java src/main/java/com/mynewmod/MyNewMod.java
```

Open [src/main/java/com/mynewmod/MyNewMod.java](../src/main/java/com/yippeespider/YippeeSpider.java) and change three things:

```java
package com.mynewmod;                     // was com.yippeespider

import net.neoforged.fml.common.Mod;

@Mod(MyNewMod.MOD_ID)                     // was @Mod(YippeeSpider.MOD_ID)
public final class MyNewMod {             // was YippeeSpider
    public static final String MOD_ID = "mynewmod";   // was "yippeespider"

    public MyNewMod() {                   // was YippeeSpider()
    }
}
```

The class name **must** match the filename. `MOD_ID` **must** match `mod_id` in gradle.properties.

## 5. Move the resource-pack asset directory

```sh
mv src/main/resources/assets/yippeespider src/main/resources/assets/mynewmod
```

Update [src/main/resources/pack.mcmeta](../src/main/resources/pack.mcmeta) `description` if you want it to read differently.

## 6. Edit `assets/minecraft/sounds.json` (if doing sound overrides)

Each entry under [src/main/resources/assets/minecraft/sounds.json](../src/main/resources/assets/minecraft/sounds.json) maps a vanilla sound event to your replacement clip. Update the namespace:

```json
{
  "entity.zombie.ambient": {
    "replace": true,
    "sounds": [
      { "name": "mynewmod:my_sound", "stream": false }
    ]
  }
}
```

Replace `yippee.ogg` under `assets/mynewmod/sounds/` with your audio file. The filename (minus `.ogg`) must match the `name` field. Audio must be **OGG Vorbis**, mono recommended for entity sounds.

Common entity sound event names (replace `<entity>` with `zombie`, `creeper`, `skeleton`, etc.):

- `entity.<entity>.ambient`
- `entity.<entity>.hurt`
- `entity.<entity>.death`
- `entity.<entity>.step`

If you're not doing sound overrides, delete `assets/minecraft/sounds.json` and the `sounds/` folder, and put your textures/models/lang files under `assets/mynewmod/` per the [vanilla resource-pack layout](https://minecraft.wiki/w/Resource_pack).

## 7. Verify `neoforge.mods.toml`

[src/main/templates/META-INF/neoforge.mods.toml](../src/main/templates/META-INF/neoforge.mods.toml) is templated — it pulls `${mod_id}`, `${mod_name}`, `${mod_description}` etc. from `gradle.properties` at build time. You usually don't need to edit it.

## 8. Build

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
PATH=$JAVA_HOME/bin:$PATH \
./gradlew build
```

If you've never built in this directory, the first run will download NeoForge + Minecraft artifacts (a few minutes). Subsequent builds are seconds.

Output: `build/libs/mynewmod-1.0.0.jar`.

## 9. Test in-game

1. Install [NeoForge 26.1.2.44-beta](https://neoforged.net/) for MC 26.1.2.
2. Drop the jar in `.minecraft/mods/`.
3. Launch. The Mods screen should list your mod by `mod_name`.

If MC crashes at launch with `JsonParseException: Pack declares support for version newer than 64...`, your `pack.mcmeta` is missing the `min_format`/`max_format` arrays — see [CLAUDE.md](../CLAUDE.md#packmcmeta--pack_format--64).

## 10. Iterate

- Bump `mod_version` in `gradle.properties` before each release so the output jar gets a new filename.
- Old jars in `build/libs/` are not auto-cleaned. `./gradlew clean build` removes them.
- For each new content addition, add the asset under `assets/<mod_id>/` and (if it's a new sound) extend `sounds.json`. No Java changes are needed for resource-only mods.

## When you need to write Java

Sound replacement and texture/model swaps are 100% data — keep the `@Mod` class empty. You only need Java once you want to:

- Register new items, blocks, entities, or recipes → use `DeferredRegister` in the `@Mod` class constructor
- Listen to events → subscribe with `@EventBusSubscriber` or `NeoForge.EVENT_BUS.addListener(...)`
- Add commands → register on the `RegisterCommandsEvent`

For any of those, ping Claude with what you want to add and a reference to the [NeoForge documentation](https://docs.neoforged.net/) entry for the API surface.
