#!/bin/bash
# Builds Broom Home without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Needs: Android SDK (build-tools + one platform android.jar, API 28 recommended), JDK 17+, Python 3.
# Works in Git Bash on Windows, and on macOS / Linux.
#
# Optional environment overrides:
#   ANDROID_HOME / ANDROID_SDK_ROOT   SDK location
#   ANDROID_JAR                       path to platforms/android-XX/android.jar
#   KEYSTORE_PASS                     password for the signing key created on first build (default: broom-home)
set -e
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ]; then
  for d in "$LOCALAPPDATA/Android/Sdk" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do [ -d "$d" ] && SDK="$d" && break; done
fi
[ -d "$SDK" ] || { echo "Android SDK not found. Set ANDROID_HOME."; exit 1; }
BT="$(ls -d "$SDK"/build-tools/[0-9]*/ 2>/dev/null | sort -V | tail -1)"
[ -n "$BT" ] || BT="$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -1)"
BT="${BT%/}"
[ -n "${JAVA_HOME:-}" ] && export PATH="$JAVA_HOME/bin:$PATH"
command -v javac >/dev/null || { echo "javac not found. Install JDK 17+ or set JAVA_HOME."; exit 1; }
AJ="${ANDROID_JAR:-$(ls "$SDK"/platforms/*/android.jar | sort -V | tail -1)}"
[ -f "$AJ" ] || { echo "No platforms/*/android.jar in $SDK. Install one (API 28 recommended)."; exit 1; }
EXE=""; [ -f "$BT/aapt2.exe" ] && EXE=".exe"
win() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi; }
PY="$(command -v python3 || command -v python)"
PASS="${KEYSTORE_PASS:-broom-home}"
echo "SDK: $SDK  ·  build-tools: $(basename "$BT")  ·  android.jar: $AJ"

rm -rf build; mkdir -p build/res build/gen build/classes build/dex
"$BT/aapt2$EXE" compile --dir res -o build/res/res.zip
"$BT/aapt2$EXE" link -o build/app-unsigned.apk -I "$AJ" --manifest AndroidManifest.xml \
   -A assets --java build/gen build/res/res.zip --min-sdk-version 21 --target-sdk-version 28
javac -encoding UTF-8 -source 8 -target 8 -nowarn -Xlint:-options -bootclasspath "$AJ" -d build/classes \
   $(find src build/gen -name '*.java')
rm -f build/classes.jar; "$PY" -I zipadd.py build/classes.jar build/classes
java -cp "$(win "$BT/lib/d8.jar")" com.android.tools.r8.D8 --release --min-api 21 --lib "$(win "$AJ")" --output build/dex build/classes.jar
"$PY" -I zipadd.py build/app-unsigned.apk build/dex classes.dex
"$BT/zipalign$EXE" -f 4 build/app-unsigned.apk build/app-aligned.apk
# Your own signing key, created once. Keep it: updates must be signed with the same key.
[ -f broom.keystore ] || keytool -genkeypair -keystore broom.keystore -storepass "$PASS" -keypass "$PASS" \
   -alias broom -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Broom Home" >/dev/null 2>&1
java -jar "$(win "$BT/lib/apksigner.jar")" sign --ks broom.keystore --ks-pass "pass:$PASS" --out build/BroomHome.apk build/app-aligned.apk
ls -la build/BroomHome.apk
