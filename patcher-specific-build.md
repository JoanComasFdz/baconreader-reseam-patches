<!-- SPDX-FileCopyrightText: 2026 JoanComasFdz -->
<!-- SPDX-License-Identifier: GPL-3.0-only -->

# Patcher-specific: build (Reseam)

The tools and commands behind [AGENTS.md](AGENTS.md)'s "Build" and "Check a change", for Reseam.

## Tools

- JDK 17 and the Android SDK, with `ANDROID_HOME` set.
- The `reseam` CLI of the plugin version in `settings.gradle.kts` (`id("app.reseam.workspace") version "…"`), as `RESEAM_BIN` or on `PATH`: [reseam.app/download](https://reseam.app/download/).
- A bundle signing key in `RESEAM_BUNDLE_KEY` or `~/.reseam/bundle-signing.key`: `reseam bundle keygen --out ~/.reseam/bundle-signing.key`. Never commit it.

## Build

```shell
./gradlew bundle --no-daemon      # → build/reseam/baconreader-patches.reseam
```

A test APK's build: `./gradlew bundle -PreleaseTag=build-<n>`. The drawer footer then shows "Patches build-<n>"; without a tag, "unknown".

## List

```shell
reseam bundle list build/reseam/baconreader-patches.reseam
```

It prints each patch with its name, description, packages and options, and the key that signed the bundle (`signer: <hex>`).

## Patch

```shell
reseam patch <BaconReader 6.1.4 APK> \
  --bundle build/reseam/baconreader-patches.reseam \
  --trust <signer's public key> \
  --output patched.apk
```

- The log ends with `failed=0`, and the patch is under `applied`.
- Only some patches: `--preset none --enable "<patch name>"` (repeatable).
- An option: `--option com.joancomasfdz.patches.baconreader.account.spoofClient.clientId=<id>`.
- Signing the APK: without `--key`/`--cert`, reseam makes a key and leaves it next to the output (`<name>.pk8`, `<name>.der`). Pass them back with `--key`/`--cert` so each build installs over the last.
- Another BaconReader version: `--ignore-versions`.

## Cloud sessions

In a Claude Code cloud session of the `AndroidDev` environment, `.claude/hooks/session-start.sh` installs what the sections above and the e2e tests need: the .NET 10 SDK, ffmpeg, socat, Tailscale, adb, Appium 3.8 with the UiAutomator2 driver 8.7, JDK 17, the Android SDK, apktool, ktfmt, the `reseam` CLI of the plugin version in `settings.gradle.kts`, and a bundle signing key for the session's builds. It also points Gradle at Google's mirror of Maven Central, which often answers 429 to cloud sessions. It does not touch the emulator, and warns (`session-start:`) about anything that failed or any missing variable. A session on this repository runs it on start; no other repository is needed.

The environment needs network access "Full" and these variables, set in the environment's settings and never in the repository:

| Variable | What |
|---|---|
| `TS_AUTHKEY_CLOUD` | A reusable, ephemeral Tailscale auth key, tagged for cloud sessions |
| `E2E_EMULATOR_HOST` | The emulator's name on the tailnet |
| `E2E_APK_CHECKSUM` | The sha256 of the clean BaconReader Premium 6.1.4 APK the emulator host serves |

### The emulator

```bash
source tools/e2e/connect.sh        # join the tailnet, fetch the base APK, wake Android, forward adb, start Appium, take the lease
tools/e2e/screenshot.sh            # a small JPEG of the screen (.e2e/screen.jpg)
tools/e2e/connect.sh --down        # at the end: release the lease, disconnect, log out
```

`connect.sh` exports `E2E_ADB_SERIAL` (`127.0.0.1:15555`), `E2E_APPIUM_URL` (`http://127.0.0.1:14723`), `E2E_APPIUM_UDID` and `E2E_ARTIFACTS_DIR` (`.e2e/`), which [the e2e tests](tests/BaconReader.E2E/README.md) read.

- **One shell per call.** Each Bash tool call is a new shell, so the exports exist only in the call that sourced the script. In later calls use the fixed values (`adb -s 127.0.0.1:15555`, `http://127.0.0.1:14723`) or source it again: that takes ~3 s, keeps the tunnel, the forward and Appium, and renews the lease. Run it with a plain `source`: piped (`… | tee`) it runs in a subshell and the exports are lost.
- **Base APK.** It is downloaded to `.e2e/com.onelouder.baconreader.premium-6.1.4-clean-base-<first 8 characters of E2E_APK_CHECKSUM>.apk` and refused unless its sha256 is `E2E_APK_CHECKSUM`. `.e2e/` and `*.apk` are ignored by git.
- **Emulator.** Android 14 (API 34), a Nexus 5 profile at 1080x1920, with `adb root` and `su`. `connect.sh` wakes it (15 to 60 s, ~100 s if it was just recreated), and the emulator host stops it after 3 hours with no connection and no lease. Installed apps and logins survive a stop.
- **Lease.** One device is shared: `/data/local/tmp/e2e-lease` holds "<session id> <expiry>" (30 minutes, renewed by sourcing the script again). If `connect.sh` says "device leased by another session", wait; don't break the lease. `--down` removes only this session's lease and says what it did.
- **The link is slow** (~3 to 4 Mbit/s, ~130 ms per round trip, through a relay; UDP is blocked). Keep transfers small: install only when the APK changed, prefer the UI tree over screenshots, and record screens at 540x960 and ~1 Mbit/s.
- **Appium's helper apps** stay installed on the device. After the emulator is recreated, `connect.sh` prints a note and exports `E2E_APPIUM_INSTALL_HELPERS=1`: the first Appium session then runs without the two skip capabilities and with `appium:uiautomator2ServerInstallTimeout` and `appium:adbExecTimeout` well above 20 s.
- **First launch of BaconReader** shows Android's notification permission and the app's "FINAL RELEASE ANNOUNCEMENT!" dialogs. Install with `adb install -r -g`, or `adb shell pm grant com.onelouder.baconreader.premium android.permission.POST_NOTIFICATIONS`, then tap "GOT IT :(". `pm clear` revokes the permission: grant it again.
- **After a wake** the device clock is set and error dialogs are suppressed; if one still shows, `adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS`.
- **Local ports.** adb on 5555 would show a phantom `emulator-5554`, so the forward uses 15555.
- **Waiting.** Claude's Bash tool blocks a foreground `sleep`: poll for the expected state, or wait in the background.
- **Proxy.** The cloud session's proxy refuses plain HTTPS to github.com (403, release downloads included), while git and GitHub's API for attached repositories work. A tool published only on a GitHub release can't be downloaded there.
