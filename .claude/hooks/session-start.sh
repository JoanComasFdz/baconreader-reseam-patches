#!/bin/bash
# SPDX-FileCopyrightText: 2026 JoanComasFdz
# SPDX-License-Identifier: GPL-3.0-only
# SessionStart hook for Claude Code cloud sessions on this repo (patcher-specific-build.md, "Cloud sessions"):
#   1. install the tools of the Android loop: .NET 10 SDK, socat, ffmpeg, Tailscale, adb, and Appium with its
#      UiAutomator2 driver (it runs in the session; tools/e2e/connect.sh starts it);
#   2. install what AGENTS.md needs to build and check the bundle: JDK 17, the Android SDK, apktool, ktfmt, the
#      reseam CLI of the plugin version in settings.gradle.kts, and a bundle signing key for the session's builds.
# Idempotent and quiet; does nothing outside cloud sessions. Joining the tailnet, waking the phone and fetching the
# base APK happen only when a session needs them (tools/e2e/connect.sh), so a session that only edits code never wakes
# the phone.
# Environment needs: network access "Full", and the variables TS_AUTHKEY_CLOUD (a reusable, ephemeral Tailscale auth
# key), E2E_EMULATOR_HOST (the device host's name on the tailnet) and E2E_APK_CHECKSUM (the clean base APK's sha256).
set -uo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

sudo=""
if [ "$(id -u)" -ne 0 ]; then
  sudo="sudo"
fi
warn() { echo "session-start: $*" >&2; }

export DOTNET_NOLOGO=1
export DOTNET_CLI_TELEMETRY_OPTOUT=1
export DOTNET_SKIP_FIRST_TIME_EXPERIENCE=1

# apt: Ubuntu 24.04 (noble) ships dotnet-sdk-10.0 in its own archive.
need=()
command -v dotnet >/dev/null 2>&1 || need+=(dotnet-sdk-10.0)
command -v socat >/dev/null 2>&1 || need+=(socat)       # the ADB forward through `tailscale nc`
command -v ffmpeg >/dev/null 2>&1 || need+=(ffmpeg)     # GIFs, small screenshot frames
command -v unzip >/dev/null 2>&1 || need+=(unzip)
if [ ${#need[@]} -gt 0 ]; then
  $sudo apt-get update -qq >/dev/null 2>&1
  DEBIAN_FRONTEND=noninteractive $sudo apt-get install -y -qq "${need[@]}" >/dev/null 2>&1 ||
    warn "apt install failed: ${need[*]}"
fi

# Tailscale, static tarball (tailscaled runs in userspace mode, started by tools/e2e/connect.sh).
if ! command -v tailscaled >/dev/null 2>&1; then
  ver=$(curl -fsSL 'https://pkgs.tailscale.com/stable/?mode=json' |
        python3 -c 'import json,sys;print(json.load(sys.stdin)["TarballsVersion"])') &&
    curl -fsSL "https://pkgs.tailscale.com/stable/tailscale_${ver}_amd64.tgz" | $sudo tar xz -C /opt &&
    $sudo ln -sf "/opt/tailscale_${ver}_amd64/tailscale" "/opt/tailscale_${ver}_amd64/tailscaled" /usr/local/bin/ ||
    warn "tailscale install failed"
fi

# What the bundle needs to build and pass its checks (AGENTS.md): JDK 17 for its Gradle toolchain, the Android SDK
# with ANDROID_HOME, apktool and ktfmt.
TOOLS=/opt/patch-tools
JDK17="$TOOLS/jdk17"
$sudo mkdir -p "$TOOLS"
if [ ! -x "$JDK17/bin/java" ]; then
  $sudo mkdir -p "$JDK17" &&
    curl -fsSL https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse |
    $sudo tar xz -C "$JDK17" --strip-components=1 || warn "JDK 17 install failed"
fi
ANDROID_SDK=/opt/android-sdk
# adb comes with the SDK's platform-tools package, installed by sdkmanager so the SDK knows it has it. A platform-tools
# folder sdkmanager didn't install (no package.xml, such as an adb link from an older version of this hook) makes the
# Android Gradle plugin install its own copy, as platform-tools-2: remove it so sdkmanager installs the real one.
if [ -d "$ANDROID_SDK/platform-tools" ] && [ ! -f "$ANDROID_SDK/platform-tools/package.xml" ]; then
  $sudo rm -rf "$ANDROID_SDK/platform-tools" "$ANDROID_SDK/platform-tools-2"
fi
if [ ! -d "$ANDROID_SDK/build-tools/35.0.0" ] || [ ! -d "$ANDROID_SDK/platforms/android-35" ] ||
  [ ! -f "$ANDROID_SDK/platform-tools/package.xml" ]; then
  if [ ! -x "$ANDROID_SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
    curl -fsSL -o /tmp/cmdline-tools.zip \
      https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip &&
      $sudo mkdir -p "$ANDROID_SDK/cmdline-tools" &&
      $sudo unzip -qo /tmp/cmdline-tools.zip -d /tmp/cmdline-tools &&
      $sudo rm -rf "$ANDROID_SDK/cmdline-tools/latest" &&
      $sudo mv /tmp/cmdline-tools/cmdline-tools "$ANDROID_SDK/cmdline-tools/latest" ||
      warn "Android command-line tools install failed"
  fi
  (yes 2>/dev/null || true) | $sudo env JAVA_HOME="$JDK17" "$ANDROID_SDK/cmdline-tools/latest/bin/sdkmanager" \
    --sdk_root="$ANDROID_SDK" "platform-tools" "platforms;android-35" "build-tools;35.0.0" >/dev/null 2>&1 ||
    warn "Android SDK packages install failed"
fi
if [ ! -f "$TOOLS/apktool.jar" ]; then
  $sudo curl -fsSL -o "$TOOLS/apktool.jar" \
    https://github.com/iBotPeaches/Apktool/releases/download/v2.10.0/apktool_2.10.0.jar ||
    warn "apktool install failed"
fi
if [ ! -f "$TOOLS/ktfmt.jar" ]; then
  $sudo curl -fsSL -o "$TOOLS/ktfmt.jar" \
    https://maven-central.storage-download.googleapis.com/maven2/com/facebook/ktfmt/0.64/ktfmt-0.64-with-dependencies.jar ||
    warn "ktfmt install failed"
fi
for tool in apktool ktfmt; do
  if [ -f "$TOOLS/$tool.jar" ] && [ ! -x "/usr/local/bin/$tool" ]; then
    printf '#!/bin/sh\nexec "%s/bin/java" -jar "%s/%s.jar" "$@"\n' "$JDK17" "$TOOLS" "$tool" |
      $sudo tee "/usr/local/bin/$tool" >/dev/null && $sudo chmod +x "/usr/local/bin/$tool"
  fi
done

# Appium, run in the session by tools/e2e/connect.sh against the phone over adb (the device host runs none).
# Pinned: the UiAutomator2 driver's helper apps on the device must match it (server 10.6.6, settings 8.0.9).
# Appium finds adb in $ANDROID_HOME/platform-tools.
APPIUM=/opt/appium
if [ ! -x "$APPIUM/node_modules/.bin/appium" ] || [ ! -d "$APPIUM/home/node_modules/appium-uiautomator2-driver" ]; then
  if command -v npm >/dev/null 2>&1; then
    $sudo mkdir -p "$APPIUM" &&
      $sudo npm install --prefix "$APPIUM" --no-audit --no-fund --loglevel=error appium@3.8.0 >/dev/null 2>&1 &&
      $sudo env APPIUM_HOME="$APPIUM/home" "$APPIUM/node_modules/.bin/appium" driver install uiautomator2@8.7.0 >/dev/null 2>&1 ||
      warn "Appium install failed"
  else
    warn "no npm: Appium not installed"
  fi
fi
# One adb for everything: the SDK's, which Appium and the Android Gradle plugin use, also on PATH for connect.sh (two
# adb versions would restart each other's server).
if [ -x "$ANDROID_SDK/platform-tools/adb" ]; then
  $sudo ln -sf "$ANDROID_SDK/platform-tools/adb" /usr/local/bin/adb
else
  warn "no adb: the Android SDK's platform-tools didn't install"
fi

# The bundle builds with the reseam CLI of its plugin version, from settings.gradle.kts.
reseam_bin=""
settings="$CLAUDE_PROJECT_DIR/settings.gradle.kts"
version=$(sed -n 's/.*id("app.reseam.workspace") version "\(.*\)".*/\1/p' "$settings" 2>/dev/null)
if [ -n "$version" ]; then
  bin="$TOOLS/reseam-$version"
  if [ ! -x "$bin" ]; then
    $sudo curl -fsSL -o "$bin" "https://cdn.reseam.app/engine/v$version/reseam-linux-x64" &&
      $sudo chmod +x "$bin" || warn "reseam CLI $version install failed"
  fi
  [ -x "$bin" ] && reseam_bin="$bin" && $sudo ln -sf "$bin" /usr/local/bin/reseam
fi
# Building the bundle signs it (RESEAM_BUNDLE_KEY or ~/.reseam/bundle-signing.key, never committed). A session's builds
# are for testing: a key made here is enough (whoever patches with them trusts its public key).
if [ -n "$reseam_bin" ] && [ -z "${RESEAM_BUNDLE_KEY:-}" ] && [ ! -f "$HOME/.reseam/bundle-signing.key" ]; then
  mkdir -p "$HOME/.reseam" &&
    "$reseam_bin" bundle keygen --out "$HOME/.reseam/bundle-signing.key" >/dev/null 2>&1 ||
    warn "reseam bundle signing key generation failed"
fi

# Maven Central answers 429 to cloud sessions often, and a Gradle build then fails. Every Gradle build uses Google's
# mirror of it instead: Maven Central's URLs are rewritten wherever a build declares them, before settings are read,
# and the mirror goes first for plugins (plugins.gradle.org redirects to Maven Central). Repositories are only
# rewritten, never added, so a build that declares its own (git.reseam.app) keeps them.
mkdir -p "$HOME/.gradle/init.d"
cat > "$HOME/.gradle/init.d/maven-central-mirror.gradle" <<'GRADLE'
def mirror = 'https://maven-central.storage-download.googleapis.com/maven2/'
def rewrite = { RepositoryHandler repos ->
    repos.withType(MavenArtifactRepository).configureEach { r ->
        def u = r.url.toString()
        if (u.contains('repo.maven.apache.org') || u.contains('repo1.maven.org')) r.url = mirror
    }
}
beforeSettings { s ->
    def plugins = s.pluginManagement.repositories
    rewrite(plugins)
    plugins.whenObjectAdded {
        if (plugins.findByName('centralMirror') == null) {
            def m = plugins.maven { name = 'centralMirror'; url = mirror }
            plugins.remove(m); plugins.add(0, m)
        }
    }
    rewrite(s.dependencyResolutionManagement.repositories)
    s.gradle.allprojects { p -> rewrite(p.buildscript.repositories); rewrite(p.repositories) }
}
GRADLE

if [ -n "${CLAUDE_ENV_FILE:-}" ] && ! grep -qs 'DOTNET_NOLOGO' "$CLAUDE_ENV_FILE"; then
  {
    echo 'export DOTNET_NOLOGO=1'
    echo 'export DOTNET_CLI_TELEMETRY_OPTOUT=1'
    echo 'export DOTNET_SKIP_FIRST_TIME_EXPERIENCE=1'
  } >> "$CLAUDE_ENV_FILE"
fi
if [ -n "${CLAUDE_ENV_FILE:-}" ] && ! grep -qs "$JDK17" "$CLAUDE_ENV_FILE"; then
  {
    echo "export JAVA_HOME=$JDK17"
    echo "export ANDROID_HOME=$ANDROID_SDK"
    echo "export PATH=$JDK17/bin:\$PATH"
    if [ -n "$reseam_bin" ]; then echo "export RESEAM_BIN=$reseam_bin"; fi
  } >> "$CLAUDE_ENV_FILE"
fi

for variable in TS_AUTHKEY_CLOUD E2E_EMULATOR_HOST E2E_APK_CHECKSUM; do
  [ -n "${!variable:-}" ] || warn "$variable is not set: the phone won't be reachable (tools/e2e/connect.sh)"
done
# Quiet by design: say it ran, so a run with nothing left to install doesn't look like no run.
echo "session-start hook done: tools in /opt/patch-tools, Android SDK in /opt/android-sdk"
exit 0
