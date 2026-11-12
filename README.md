<div align="center">

# 🥓 BaconReader Patches

**BaconReader, alive again.** Patches for [Reseam](https://reseam.app) that make BaconReader work with today's Reddit, and add what it never had.

[![Reseam](https://img.shields.io/badge/Reseam-0.41.0-ff4500?style=flat-square)](https://reseam.app)
[![BaconReader](https://img.shields.io/badge/BaconReader-6.1.4-f57c00?style=flat-square)](#-install)
[![User guide](https://img.shields.io/badge/user_guide-📖-2ea44f?style=flat-square)](https://joancomasfdz.github.io/baconreader-reseam-patches/)
[![License](https://img.shields.io/badge/license-GPL--3.0-blue?style=flat-square)](LICENSE)

[**➕ Add to Reseam Manager**](https://reseam.app/add/?url=https%3A%2F%2Fgithub.com%2FJoanComasFdz%2Fbaconreader-reseam-patches)

</div>

## ⚡ Install

1. Install [Reseam Manager](https://reseam.app/download/).
2. Add this repository as a patch source, one of two ways:
   - On your phone, open [**Add to Reseam Manager**](https://reseam.app/add/?url=https%3A%2F%2Fgithub.com%2FJoanComasFdz%2Fbaconreader-reseam-patches): the link opens Reseam Manager.
   - Or, in Reseam Manager, open **Add patch source** and paste this address:
     ```text
     https://github.com/JoanComasFdz/baconreader-reseam-patches
     ```
   Before adding it, Reseam Manager shows the bundle's apps, BaconReader's two packages `com.onelouder.baconreader` and `com.onelouder.baconreader.premium`, and its signing key, which must be:
   ```text
   8fcc605f22082b88c826675e5614938e1fedcd769c3c568b79f3cd9564159437
   ```
3. Patch your **BaconReader 6.1.4** APK, free or Premium.
4. Uninstall the original BaconReader, then install the patched one.

Settings live in BaconReader → **Settings → Patches**.

> [!WARNING]
> Use an APK you trust. Repackaged "Premium" APKs often carry malware.

### Update

In Reseam Manager, open **Patched apps → BaconReader Premium** (or **BaconReader**), tap **Patch again**, then
**Install → Update**. Reseam Manager patches its own copy of the APK you picked the first time and signs the result with
the same key, so Android updates the app and keeps your login and settings: you don't need to keep the APK.

Reseam Manager gets new patches when it starts, but doesn't patch BaconReader again by itself: patch again to get them.
The bottom of BaconReader's left menu shows the version of the patches installed, such as "Patches 0.1.20 (Reseam
0.40.0)". Watch it: [How to update](https://joancomasfdz.github.io/baconreader-reseam-patches/#update), in the user
guide.

Don't patch the installed BaconReader: it's patched already, and the patches look for the original code, so several
fail. If Reseam Manager says the patches need a newer version, update Reseam Manager first.

### Troubleshooting

- **Reseam Manager shows another signing key, or not both BaconReader packages, when adding the source:** it's another
  source, or the address was pasted wrongly. Don't add it; add the repository as step 2 says, with the link or the
  address `https://github.com/JoanComasFdz/baconreader-reseam-patches`. If you added it already, remove it first.
- **Reseam Manager can't load the bundle and says so:** the bundle was built with an older Reseam than your Reseam
  Manager. It's rebuilt within a day of each new Reseam; update the source then.

## 🧩 What's inside

Fixes for what broke since 2023, features BaconReader never had, and cleanups of what no longer works.

<div align="center">

[**📖 Explore the user guide**](https://joancomasfdz.github.io/baconreader-reseam-patches/)

</div>

## 🛠️ Development

What each patch does: [docs/patches.md](docs/patches.md), grouped as the user guide shows it; its settings and differences from ReVanced: [docs/patcher-specific-patches.md](docs/patcher-specific-patches.md). Build and release: [patcher-specific-build.md](patcher-specific-build.md), [patcher-specific-release.md](patcher-specific-release.md). Code style and checks: [STYLE.md](STYLE.md), [patcher-specific-code.md](patcher-specific-code.md) and [AGENTS.md](AGENTS.md).

End-to-end tests: [tests/BaconReader.E2E](tests/BaconReader.E2E/README.md), each a PR shows as GIFs. A VS Code extension plays their recordings next to their code; the repository's dev container (a Codespace) installs it: [tools/vscode-extension](tools/vscode-extension/README.md).

## 📜 License

[GPL-3.0](LICENSE). Most patches are ported from [ReVanced Patches](https://gitlab.com/ReVanced/revanced-patches). Not affiliated with Reddit, BaconReader or Reseam.

## 🤖 AI disclosure

I developed these patches with AI assistance (Claude Code). The AI wrote most of the code following my requirements, and I review the results and test the patches on my device. With it I also developed end-to-end tests that run the patches on a real phone: the user guide shows each test's steps, its Gherkin, beside the video of its run. The commits say so too.
