# BaconReader patches for Reseam

BaconReader patches for [Reseam](https://reseam.app), ported from ReVanced. Built from the [Reseam patch bundle template](https://git.reseam.app/reseam/patches-template) for Reseam 0.28.0.

The patches are pinned to version 6.1.4, BaconReader's last release, of `com.onelouder.baconreader` and `com.onelouder.baconreader.premium`. Only Premium has been tested. To try another version anyway, pass `--ignore-versions`.

## Build and test

Needs JDK 17, the Android SDK with `ANDROID_HOME` set, and the [`reseam` CLI 0.28.0](https://reseam.app/download/). Create a signing key once with `reseam bundle keygen --out ~/.reseam/bundle-signing.key`, and keep it out of the repository.

```shell
./gradlew bundle
reseam patch BaconReader.apk \
  --bundle build/reseam/baconreader-patches.reseam \
  --trust <your public key> \
  --output patched.apk
```

## Release

`.github/workflows/release.yml`, from the template, runs on `v*` tags. It builds and signs the bundle, then publishes it with its `patches.json` on a GitHub release.

1. Add the signing key as the repository secret `BUNDLE_SIGNING_KEY_B64`: `base64 -w0 ~/.reseam/bundle-signing.key`.
2. Push a tag: `git tag v0.1.0 && git push origin v0.1.0`.

Users add the bundle to Reseam Manager with this link, then check the signing key it shows against the one published here:

```text
https://reseam.app/add/?url=https%3A%2F%2Fgithub.com%2FJoanComasFdz%2Fbaconreader-reseam-patches
```

## Layout and style

The layout follows Reseam's own bundle: `core/BaconReader.kt` holds the packages and pinned versions, patches sit in one package per area (`account`, `media`), and targets are private values after the patch that uses them. Kotlin is formatted with ktfmt's kotlinlang style, which Reseam uses:

```shell
java -jar ktfmt.jar --kotlinlang-style $(git ls-files 'apps/*.kt' 'apps/*.kts')
```

## License

GPL-3.0-only, like [ReVanced Patches](https://gitlab.com/ReVanced/revanced-patches), which the patches and extension are ported from. The files from the Reseam template stay AGPL-3.0-or-later, with the additional terms in [NOTICE](NOTICE), and the Gradle wrapper is Apache-2.0. [REUSE.toml](REUSE.toml) and each file's SPDX header say which applies, and [LICENSES/](LICENSES) holds the texts.
