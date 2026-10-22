# BaconReader patches for Reseam

BaconReader patches for [Reseam](https://reseam.app), ported from ReVanced. Built from the [Reseam patch bundle template](https://git.reseam.app/reseam/patches-template) for Reseam 0.28.0.

| Patch | What it does |
|---|---|
| Spoof client | Logs in to Reddit with another app's client ID, since Reddit no longer accepts BaconReader's own. Defaults to RedReader's client ID, redirect URI and user agent, like ReVanced. Also rewrites `ssl.reddit.com` URLs to `www.reddit.com`. |
| Fix Redgifs API | Replaces BaconReader's Redgifs interceptor with one that fetches the temporary tokens the Redgifs API now requires. |
| Patches entry in settings | Adds "Patches" at the end of BaconReader's settings menu, which opens the patches' settings. |

The patches are pinned to version 6.1.4, BaconReader's last release, of `com.onelouder.baconreader` and `com.onelouder.baconreader.premium`. Only Premium has been tested. To try another version anyway, pass `--ignore-versions`.

## Settings

Patches with settings add them to one settings screen, made by Reseam's app runtime. Open it from BaconReader's settings → Patches. Its pages are Images and videos, Links and Comments; a page shows once a selected patch has settings on it. The screen also has an about card and exports and imports the settings.

Errors of the patches show in a dialog over the current screen, with the details to copy or save to Downloads.

## Spoof client options

| Key | Default | Empty value |
|---|---|---|
| `clientId` | RedReader's, `yH0aTnJEt6qUgGn835B4vg` | Not allowed |
| `redirectUri` | `redreader://rr_oauth_redir` | Keeps `http://baconreader.com/auth` |
| `userAgent` | `org.quantumbadger.redreader/1.25.1` | Keeps BaconReader's |

To use your own Reddit app, create an "Installed app" at https://www.reddit.com/prefs/apps, then set `clientId` and the `redirectUri` you gave it.

## Differences from ReVanced

- **Redgifs hook.** ReVanced deletes the five instructions that add BaconReader's interceptor and replaces the first `build()` call. This port redirects the `addInterceptor` call instead, so the new interceptor also applies on the method's second `build()` path, the one taken when setting up SSL fails.
- **Redgifs `/info`.** ReVanced's shared Redgifs code fakes the removed `/info` endpoint for Sync. BaconReader never calls it, so the port leaves it out.
- **User agent.** ReVanced returns early from the two `getUserAgent` methods. This port replaces their bodies with the same constant return.
- **Empty options.** ReVanced skips the redirect URI and user agent when unset. Reseam always applies an option's default, so an empty value skips them here.
- **Extension.** ReVanced's Java extension and shared Redgifs code are rewritten in Kotlin. The shared extension init hook isn't needed.
- **Settings screen.** ReVanced adds a "ReVanced" entry to BaconReader's settings and shows its settings in `GoogleApiActivity`, in BaconReader's theme. This port adds a "Patches" entry that opens Reseam's settings screen, which follows the system's light or dark theme. Settings whose summary changed with the switch have one summary here, and settings ReVanced greys out under another stay editable.
- **Error dialog.** ReVanced's dialog has a close icon, and Copy and Save keep it open. This port uses Reseam's branded dialog, where Copy, Save to file and Close each close it. The "Patches" line shows the bundle's version and the Reseam version.
- **Versions.** ReVanced's patches run on any version. These are pinned, like every patch in Reseam's own bundle.

## Build and test

Needs JDK 17, the Android SDK with `ANDROID_HOME` set, and the [`reseam` CLI 0.28.0](https://reseam.app/download/). Create a signing key once with `reseam bundle keygen --out ~/.reseam/bundle-signing.key`, and keep it out of the repository.

```shell
./gradlew bundle
reseam patch BaconReader.apk \
  --bundle build/reseam/baconreader-patches.reseam \
  --trust <your public key> \
  --output patched.apk
```

Set an option with `--option com.joancomasfdz.patches.baconreader.account.spoofClient.clientId=<id>`.

## Release

`.github/workflows/release.yml`, from the template, runs on `v*` tags. It builds and signs the bundle, then publishes it with its `patches.json` on a GitHub release.

1. Add the signing key as the repository secret `BUNDLE_SIGNING_KEY_B64`: `base64 -w0 ~/.reseam/bundle-signing.key`.
2. Push a tag: `git tag v0.1.0 && git push origin v0.1.0`.

Users add the bundle to Reseam Manager with this link, then check the signing key it shows against the one published here:

```text
https://reseam.app/add/?url=https%3A%2F%2Fgithub.com%2FJoanComasFdz%2Fbaconreader-reseam-patches
```

## Layout and style

[STYLE.md](STYLE.md) has the full guidelines, and [AGENTS.md](AGENTS.md) the checks every change passes. In short, the layout follows Reseam's own bundle: `core/BaconReader.kt` holds the packages and pinned versions, patches sit in one package per area (`account`, `media`), and targets are private values after the patch that uses them. Kotlin is formatted with ktfmt's kotlinlang style, which Reseam uses:

```shell
java -jar ktfmt.jar --kotlinlang-style $(git ls-files 'apps/*.kt' 'apps/*.kts')
```

## License

GPL-3.0-only, like [ReVanced Patches](https://gitlab.com/ReVanced/revanced-patches), which the patches and extension are ported from. The files from the Reseam template stay AGPL-3.0-or-later, with the additional terms in [NOTICE](NOTICE), and the Gradle wrapper is Apache-2.0. [REUSE.toml](REUSE.toml) and each file's SPDX header say which applies, and [LICENSES/](LICENSES) holds the texts.
