#!/usr/bin/env bash
# Run the CI and release pipeline steps locally, so problems surface before a paid GitHub Actions run.
#
# Usage:
#   scripts/local-ci.sh            same as CI: unit tests + debug APKs (foss, gms)
#   scripts/local-ci.sh test       unit tests only (fastest feedback)
#   scripts/local-ci.sh release    same as the release workflow: tests + signed release APKs in dist/
#
# Release signing uses the same variables as the workflow:
#   HHO_KEYSTORE_FILE, HHO_KEYSTORE_PASSWORD, HHO_KEY_ALIAS, HHO_KEY_PASSWORD
# (or hho.keystore.* properties in ~/.gradle/gradle.properties).

set -euo pipefail

mode="${1:-ci}"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
app_dir="$repo_root/android-app"

step() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
fail() { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

case "$mode" in
  ci | test | release) ;;
  -h | --help) sed -n '2,12p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
  *) fail "unknown mode '$mode' (expected: ci, test, release)" ;;
esac

java_major() { "$1/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -n1; }

# CI uses Temurin 21. Keep JAVA_HOME if it points at a JDK 21, otherwise look for one.
find_jdk21() {
  if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] && [ "$(java_major "$JAVA_HOME")" = 21 ]; then
    echo "$JAVA_HOME"; return
  fi
  if [ -x /usr/libexec/java_home ] && /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
    /usr/libexec/java_home -v 21; return
  fi
  local candidate
  for candidate in /usr/lib/jvm/*21* "$HOME"/.sdkman/candidates/java/21* "$HOME"/.jdks/*21*; do
    if [ -x "$candidate/bin/java" ] && [ "$(java_major "$candidate")" = 21 ]; then
      echo "$candidate"; return
    fi
  done
}

find_android_sdk() {
  local sdk_dir
  sdk_dir=$(sed -n 's/^sdk\.dir=//p' "$app_dir/local.properties" 2>/dev/null || true)
  local candidate
  for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$sdk_dir" "$HOME/Android/Sdk" "$HOME/Library/Android/sdk"; do
    if [ -n "$candidate" ] && [ -d "$candidate/platforms" ]; then
      echo "$candidate"; return
    fi
  done
}

step "Checking environment"
JAVA_HOME="$(find_jdk21)"
[ -n "$JAVA_HOME" ] || fail "JDK 21 not found. Install one (e.g. sudo apt install openjdk-21-jdk) or set JAVA_HOME."
ANDROID_HOME="$(find_android_sdk)"
[ -n "$ANDROID_HOME" ] || fail "Android SDK not found. Set ANDROID_HOME or sdk.dir in android-app/local.properties."
export JAVA_HOME ANDROID_HOME
echo "JDK:         $JAVA_HOME"
echo "Android SDK: $ANDROID_HOME"

version="$(tr -d '[:space:]' < "$repo_root/VERSION")"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "VERSION must contain MAJOR.MINOR.PATCH (e.g. 1.4.2), got '$version'"
echo "App version: $version"

cd "$app_dir"

if [ "$mode" = release ]; then
  step "Verifying release signing"
  keystore="${HHO_KEYSTORE_FILE:-}"
  if [ -z "$keystore" ]; then
    keystore=$(sed -n 's/^hho\.keystore\.file=//p' "$HOME/.gradle/gradle.properties" 2>/dev/null || true)
  fi
  [ -n "$keystore" ] && [ -f "$keystore" ] \
    || fail "No keystore: set HHO_KEYSTORE_FILE (plus HHO_KEYSTORE_PASSWORD, HHO_KEY_ALIAS, HHO_KEY_PASSWORD)."
  if [ -n "${HHO_KEYSTORE_PASSWORD:-}" ] && [ -n "${HHO_KEY_ALIAS:-}" ]; then
    "$JAVA_HOME/bin/keytool" -list -keystore "$keystore" -storepass "$HHO_KEYSTORE_PASSWORD" -alias "$HHO_KEY_ALIAS" >/dev/null 2>&1 \
      || fail "Keystore check failed: wrong HHO_KEYSTORE_PASSWORD, or alias '$HHO_KEY_ALIAS' is not in $keystore."
    echo "Keystore and alias OK: $keystore"
  else
    echo "Keystore: $keystore (passwords come from gradle.properties)"
  fi
fi

step "Build logic tests (buildSrc)"
./gradlew -p buildSrc test

step "Unit tests (foss + gms)"
if ! ./gradlew testFossDebugUnitTest testGmsDebugUnitTest; then
  echo "Test reports: $app_dir/app/build/reports/tests/" >&2
  fail "unit tests failed"
fi

case "$mode" in
  ci)
    step "Assembling debug APKs"
    ./gradlew assembleFossDebug assembleGmsDebug
    step "Done"
    ls -1 app/build/outputs/apk/*/debug/*.apk
    ;;
  release)
    step "Assembling release APKs"
    ./gradlew assembleFossRelease assembleGmsRelease
    dist="$repo_root/dist"
    rm -rf "$dist" && mkdir -p "$dist"
    for flavor in foss gms; do
      apk="app/build/outputs/apk/$flavor/release/app-$flavor-release.apk"
      [ -f "$apk" ] || fail "$apk missing (an unsigned build is named *-unsigned.apk; check signing settings)."
      cp "$apk" "$dist/hho-$version-$flavor.apk"
    done
    (cd "$dist" && sha256sum -- *.apk > SHA256SUMS)
    step "Done"
    ls -1 "$dist"
    ;;
  test)
    step "Done"
    ;;
esac
