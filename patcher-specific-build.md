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
