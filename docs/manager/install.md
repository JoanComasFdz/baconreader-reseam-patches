<!-- SPDX-FileCopyrightText: 2026 JoanComasFdz -->
<!-- SPDX-License-Identifier: GPL-3.0-only -->
<!-- scenarios: PatchWithReseamManagerTests -->

# How to install

Add the BaconReader patches to Reseam Manager, then let it patch your BaconReader 6.1.4 APK and install it.

- Install [Reseam Manager](https://reseam.app/download/), and have your BaconReader Premium (or BaconReader) 6.1.4 APK on the phone. Use an APK you trust: repackaged "Premium" APKs often carry malware.
- On the phone, open this repository on GitHub and tap **➕ Add to Reseam Manager**: Reseam Manager opens and asks "Add baconreader-patches?". Its signing key must start with `8fcc 605f 2208 2b88` (the README has all of it). Tap **Add**.
- Go back to Reseam Manager's home screen, tap **Pick an APK** and pick your APK, then tap **Patch**: Reseam Manager patches it with the newest patches.
- Tap **Install**. The first time, Android opens "Install unknown apps": turn on **Allow from this source**, go back, and tap **Install** again, then **Install** on "Install this app?". If the original BaconReader is installed, uninstall it first: Android won't install the patched one over it.
- Tap **Open**. The bottom of BaconReader's left menu shows which patches are installed, such as "Patches 0.1.18 (Reseam 0.39.0)". Settings live in BaconReader → **Settings → Patches**.
