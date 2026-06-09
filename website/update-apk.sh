#!/usr/bin/env bash
# Publish the latest built APK to the website's download folder.
#
#   ./website/update-apk.sh            # copy the existing debug APK
#   ./website/update-apk.sh --build    # assemble first, then copy
#
# Produces:
#   website/downloads/MedAboutYou-latest.apk   (the package the site serves)
#   website/downloads/latest.json              (version/size/date the page reads)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK_SRC="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
OUT_DIR="$ROOT/website/downloads"
OUT_APK="$OUT_DIR/MedAboutYou-latest.apk"

if [[ "${1:-}" == "--build" ]]; then
  : "${JAVA_HOME:=/opt/android-studio/jbr}"
  : "${ANDROID_HOME:=$HOME/Android/Sdk}"
  export JAVA_HOME ANDROID_HOME
  ( cd "$ROOT" && ./gradlew :app:assembleDebug )
fi

[[ -f "$APK_SRC" ]] || { echo "APK not found: $APK_SRC (run with --build)"; exit 1; }

mkdir -p "$OUT_DIR"
cp -f "$APK_SRC" "$OUT_APK"

VERSION="$(grep -oE 'versionName *= *"[^"]+"' "$ROOT/app/build.gradle.kts" | grep -oE '"[^"]+"' | tr -d '"' || echo "0.0.0")"
SIZE_BYTES="$(stat -c%s "$OUT_APK" 2>/dev/null || wc -c < "$OUT_APK")"
SIZE_MB="$(awk "BEGIN{printf \"%.1f\", $SIZE_BYTES/1048576}")"
BUILT_AT="$(date -u +%Y-%m-%d)"

cat > "$OUT_DIR/latest.json" <<JSON
{
  "version": "$VERSION",
  "file": "MedAboutYou-latest.apk",
  "sizeMB": $SIZE_MB,
  "builtAt": "$BUILT_AT"
}
JSON

echo "Published v$VERSION  (${SIZE_MB} MB)  → $OUT_APK"
