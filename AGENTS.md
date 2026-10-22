# Agents

Follow [STYLE.md](STYLE.md). A change is done when the checks below pass for every commit.

## Build

- JDK 17, the Android SDK with `ANDROID_HOME` set, and the `reseam` CLI matching the plugin version in `settings.gradle.kts` (`RESEAM_BIN` or on `PATH`).
- A bundle signing key in `RESEAM_BUNDLE_KEY` or `~/.reseam/bundle-signing.key`. Never commit it.
- `./gradlew bundle --no-daemon` writes `build/reseam/baconreader-patches.reseam`.
- One Gradle build at a time per machine when several agents share it. Use `--no-daemon`.
- A stale ignored `build/` folder of a removed module still makes Gradle configure it ("nothing to dex"). Check commits in a clean worktree: `git worktree add --detach <dir> <commit>`.
- If Maven Central answers 429, point Gradle at `https://maven-central.storage-download.googleapis.com/maven2/` with an init script in `~/.gradle/init.d`. Don't commit it.

## Check a change

1. **Build:** `./gradlew bundle` passes.
2. **List:** `reseam bundle list build/reseam/baconreader-patches.reseam` shows the patch with the right name, description, packages and options.
3. **Patch:** `reseam patch <BaconReader 6.1.4 APK> --bundle build/reseam/baconreader-patches.reseam --trust <public key> --output patched.apk`. The log ends with `failed=0` and the patch under `applied`.
4. **Inspect the result:** disassemble `patched.apk` with apktool and read the changed methods.
   - When a ReVanced build of the same patch exists, compare the changed classes with it. Strip `.line`, `.local` and `.param` lines and comments, and treat `const-string/jumbo` as `const-string`.
5. **Extension calls:** every method an extension calls on app or library classes exists in the patched app's smali, with the same descriptor.
6. **Formatting:** ktfmt's kotlinlang style leaves the bundle's `.kt` and `.kts` files unchanged.

Only a phone shows whether a patch works. Say in the PR what was tested on one and what only passed these checks.

## Porting a ReVanced patch

1. Read the ReVanced patch, its fingerprints and its extension at the newest commit that works.
2. Find each fingerprint's method in BaconReader 6.1.4 (jadx or apktool) and write a Reseam target for it.
3. Rewrite the extension in Kotlin. Check that BaconReader reaches every branch you keep.
4. Map its settings to a `SettingsGroup` (see STYLE.md).
5. Run the checks, then add the patch to the README: its row, its settings or options, and its differences from ReVanced.
