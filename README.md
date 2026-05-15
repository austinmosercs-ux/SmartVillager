# NeoForge Mod Template

This repository is a minimal NeoForge mod template you can use to scaffold a new Minecraft mod. Replace the placeholder values in `gradle.properties` and `src/main/java/com/example/mod/ModTemplate.java` when creating a new project.

Quick start:

```sh
# Edit `gradle.properties` with your `mod_id`, `mod_name`, and authors
./new-mod.sh <mod_id> [mod_name]
```

Build (example macOS/JDK 25):

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
PATH=$JAVA_HOME/bin:$PATH \
./gradlew build
```

See `docs/CREATING-A-NEW-MOD.md` for full instructions and the `new-mod.sh` helper.
