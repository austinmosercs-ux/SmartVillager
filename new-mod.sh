#!/usr/bin/env bash
# Stamp out a new NeoForge mod from this template.
#
# Usage:
#   ./new-mod.sh <mod_id> [mod_name] [target_dir]
#
#   mod_id      required; lowercase alphanumeric (and underscores), must start with a letter
#   mod_name    optional; display name (defaults to capitalized mod_id)
#   target_dir  optional; output path (defaults to ../<mod_id>)
#
# Examples:
#   ./new-mod.sh creepertunes CreeperTunes
#   ./new-mod.sh zombiebop "Zombie Bop" ~/Documents/ZombieBop

set -euo pipefail

usage() {
    sed -n '2,/^$/p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
}

[[ $# -ge 1 ]] || usage

MOD_ID="$1"
MOD_NAME="${2:-}"
TARGET_DIR="${3:-}"

if ! [[ "$MOD_ID" =~ ^[a-z][a-z0-9_]*$ ]]; then
    echo "Error: mod_id must match ^[a-z][a-z0-9_]*\$ (got: $MOD_ID)" >&2
    exit 1
fi

if [[ -z "$MOD_NAME" ]]; then
    MOD_NAME="$(echo "$MOD_ID" | awk '{print toupper(substr($0,1,1)) substr($0,2)}')"
fi

TEMPLATE_DIR="$(cd "$(dirname "$0")" && pwd)"
if [[ -z "$TARGET_DIR" ]]; then
    TARGET_DIR="$(dirname "$TEMPLATE_DIR")/$MOD_ID"
fi

if [[ -e "$TARGET_DIR" ]]; then
    echo "Error: target already exists: $TARGET_DIR" >&2
    exit 1
fi

OLD_MOD_ID="yippeespider"
OLD_MOD_NAME="YippeeSpider"
OLD_PKG="com.${OLD_MOD_ID}"
NEW_PKG="com.${MOD_ID}"

# BSD/macOS sed needs '' after -i; GNU sed does not.
sed_inplace() {
    if sed --version >/dev/null 2>&1; then
        sed -i "$@"
    else
        local script="$1"; shift
        sed -i '' "$script" "$@"
    fi
}

echo "→ Copying template to $TARGET_DIR"
cp -R "$TEMPLATE_DIR" "$TARGET_DIR"
cd "$TARGET_DIR"

echo "→ Cleaning template-only files"
rm -rf .git build .gradle bin
find . -name '.DS_Store' -delete 2>/dev/null || true
# new-mod.sh and CREATING-A-NEW-MOD.md are template-only; new repos don't need them
rm -f new-mod.sh docs/CREATING-A-NEW-MOD.md
# drop empty docs dir if nothing else lives there
[[ -d docs ]] && find docs -type d -empty -delete 2>/dev/null || true

echo "→ Renaming Java package $OLD_PKG → $NEW_PKG"
mv "src/main/java/${OLD_PKG//.//}" "src/main/java/${NEW_PKG//.//}"

echo "→ Renaming class $OLD_MOD_NAME → $MOD_NAME"
mv "src/main/java/${NEW_PKG//.//}/${OLD_MOD_NAME}.java" \
   "src/main/java/${NEW_PKG//.//}/${MOD_NAME}.java"

JAVA_FILE="src/main/java/${NEW_PKG//.//}/${MOD_NAME}.java"
sed_inplace "s/${OLD_PKG}/${NEW_PKG}/g" "$JAVA_FILE"
sed_inplace "s/${OLD_MOD_NAME}/${MOD_NAME}/g" "$JAVA_FILE"
sed_inplace "s/\"${OLD_MOD_ID}\"/\"${MOD_ID}\"/g" "$JAVA_FILE"

echo "→ Patching gradle.properties"
sed_inplace "s/^mod_id=.*/mod_id=${MOD_ID}/" gradle.properties
sed_inplace "s/^mod_name=.*/mod_name=${MOD_NAME}/" gradle.properties
sed_inplace "s/^mod_group_id=.*/mod_group_id=${NEW_PKG}/" gradle.properties
sed_inplace "s/^mod_version=.*/mod_version=1.0.0/" gradle.properties
sed_inplace "s|^mod_description=.*|mod_description=A NeoForge mod.|" gradle.properties

echo "→ Moving assets directory"
if [[ -d "src/main/resources/assets/${OLD_MOD_ID}" ]]; then
    mv "src/main/resources/assets/${OLD_MOD_ID}" "src/main/resources/assets/${MOD_ID}"
fi

echo "→ Updating resource references"
if [[ -f src/main/resources/assets/minecraft/sounds.json ]]; then
    sed_inplace "s/${OLD_MOD_ID}:/${MOD_ID}:/g" src/main/resources/assets/minecraft/sounds.json
fi
sed_inplace "s/${OLD_MOD_NAME}/${MOD_NAME}/g" src/main/resources/pack.mcmeta

echo "→ Rewriting README.md and CLAUDE.md"
for f in README.md CLAUDE.md; do
    [[ -f "$f" ]] || continue
    sed_inplace "s/${OLD_MOD_NAME}/${MOD_NAME}/g" "$f"
    sed_inplace "s/${OLD_MOD_ID}/${MOD_ID}/g" "$f"
done

echo "→ git init + initial commit"
git init -q -b main
git add .
git commit -qm "feat: initial commit (from YippeeSpider template)"

cat <<EOF

✓ Created $TARGET_DIR

  mod_id:    $MOD_ID
  mod_name:  $MOD_NAME
  package:   $NEW_PKG

Next steps:
  cd $TARGET_DIR
  # Replace assets under src/main/resources/assets/${MOD_ID}/ with your content.
  # Edit src/main/resources/assets/minecraft/sounds.json (or delete it) for what you want overridden.
  # Then:
  JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \\
  PATH=\$JAVA_HOME/bin:\$PATH \\
  ./gradlew build
EOF
