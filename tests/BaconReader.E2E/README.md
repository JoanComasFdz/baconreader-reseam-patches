# BaconReader.E2E: end-to-end tests of the patched BaconReader

Scenarios that patch BaconReader Premium 6.1.4 with this bundle, install it on an Android emulator and drive it with
Appium, each one the Gherkin of an acceptance criterion
([docs/given-when-then-test-rules.md](../../docs/given-when-then-test-rules.md)), recorded as a GIF and an MP4 so a PR
shows its patch working. **How to write one: [E2E-TEST-CODE-RULES.md](E2E-TEST-CODE-RULES.md)** (binding for this
project).

It is destiny-loopsmith's
[Loopsmith.Web.E2E](https://github.com/JoanComasFdz/destiny-loopsmith/tree/main/tests/Loopsmith.Web.E2E) for Android:
Appium (UiAutomator2) instead of Playwright, and an emulator instead of a locally
served site.

## The emulator

The scenarios need an Android 14 emulator reachable over adb, an Appium 3.8 server with the UiAutomator2 driver 8.7
that drives it over adb, and BaconReader Premium 6.1.4's clean APK. In an AndroidDev cloud session, the session-start
hook (`.claude/hooks/session-start.sh`) installs Appium, and sourcing `tools/e2e/connect.sh` in the shell that then runs
the scenarios joins the emulator, starts Appium in the session and exports what they read
([patcher-specific-build.md](../../patcher-specific-build.md), "Cloud sessions"):

| Variable | Default | |
|---|---|---|
| `E2E_APPIUM_URL` | unset | the Appium server; without it, every scenario skips |
| `E2E_ADB_SERIAL` | `127.0.0.1:15555` | the emulator for adb (installs, data, recording) |
| `E2E_APPIUM_UDID` | `E2E_ADB_SERIAL` | the emulator as the Appium server sees it (its adb serial, when Appium runs next to the tests) |
| `E2E_ARTIFACTS_DIR` | unset | the folder holding `com.onelouder.baconreader.premium-6.1.4-clean-base-*.apk` |
| `E2E_BASE_APK` | that APK | the APK to patch, if it's elsewhere |
| `E2E_BUNDLE` | `build/reseam/baconreader-patches.reseam` | the bundle to patch with (`./gradlew bundle`) |
| `E2E_RESEAM` | `reseam` | the reseam CLI |
| `E2E_VIDEO_DIR` | unset | record each run there (`record.sh` sets it) |
| `E2E_ACTION_GAP` | `1` | least seconds between two recorded steps |
| `E2E_APPIUM_INSTALL_HELPERS` | unset | `1`: let Appium install its helper apps (an emulator just recreated) |

Appium's helper apps (`io.appium.uiautomator2.server`, `.server.test`, `io.appium.settings`) stay installed on the
emulator, so a session starts without reinstalling them; a new emulator needs one run with
`E2E_APPIUM_INSTALL_HELPERS=1` (the connection script sets it when they're missing).

## What a scenario does

For each scenario, `BaseE2E`:

1. patches the clean APK with the bundle, enabling only the scenario's `[PatchedWith]` patches
   (`reseam patch --preset none --enable …`), once per set of patches and bundle (`build/e2e/patched/`);
2. installs it, unless it is the APK already installed, with its permissions granted; every APK is signed with one test
   key (`build/e2e/signing/`, made on the first patch), and an app signed with another key is uninstalled first;
3. clears the app's data, so the scenario starts from a fresh install;
4. starts recording the screen, when the run records.

The scenario then opens the app itself: `Phone.Launcher.OpenBaconReaderAsync()` and the first-start dialogs
(`FINAL RELEASE ANNOUNCEMENT!` → "GOT IT :(", and with "Prompt to open Reddit links", "Open Reddit links with
BaconReader?" → "Not now" or "Set up"), or `Phone.BaconReader.OpenAsync()`, which does all of it. Then it taps and checks what
its patch changes.

A scenario replaces the installed BaconReader with its own test-signed build and clears its data: whatever was
installed and logged in on the emulator doesn't survive a run.

Measured on 2026-10-23 from a cloud session (~3–4 Mbit/s to the emulator): patching ~1 s; sending a new APK ~40 s; a
scenario whose APK is already installed ~35 s, recorded.

## Run and record

In the shell that sourced the emulator's connection script:

```bash
tools/e2e/record.sh --no-video SpoofClientTests            # while developing: unrecorded, unpaced
tools/e2e/record.sh --no-video --no-bundle Account         # a domain folder, reusing the last bundle
tools/e2e/record.sh --changed-since origin/main            # right before the push: this branch's scenarios, recorded
tools/e2e/push-gifs.sh                                     # right after the push: posted on the branch's PR
```

`record.sh` builds the bundle (`--no-bundle` skips it), runs the chosen scenarios and, recording, writes
`out/<domain folder>/<gif name>.gif` and `.mp4` and `out/scenarios.json`
([tools/e2e/README.md](../../tools/e2e/README.md)); `push-gifs.sh` hands them to the "E2E videos" workflow, which posts
them on the PR ([docs/hosting.md](../../docs/hosting.md)). Without the emulator, `dotnet test` skips every scenario and
runs only the shape and naming tests (the "E2E CI" workflow does that).

By hand: `dotnet test tests/BaconReader.E2E` (add `E2E_VIDEO_DIR=<dir>` to keep the raw videos and their records).

## Where tests go

`Infrastructure/` holds everything that isn't a scenario: `BaseE2E`, `Device/` (adb, patching and installing,
recording), the `Phone` page objects (Android's screens on `Phone`, the app's on `Phone.BaconReader`), the `Vocabulary`, the assertions, and what a scenario is (its Gherkin,
`[TestDescription]`, `[PlayOnPhone]`, its names, its recording's record). `Scenarios/` is organized by the patches'
areas (`account`, `comments`, `editor`, `inbox`, `links`, `media`, `misc`, `posts`): folder = area, class = one patch
(`<Patch>Tests.cs`) in a folder of its own, method = one scenario (one Gherkin).

```
Scenarios/Account/SpoofClient/SpoofClientTests.cs
```

## Logging in

Not built yet. Reddit refuses a logged-out BaconReader even with "Spoof client" (`www.reddit.com/.json` answers
`HTTP 403 Blocked`), so every scenario that loads posts or comments needs a logged-in account. What it needs:

1. a Reddit account for the tests only, with two-factor authentication off;
2. its user name and password in the environment, `E2E_REDDIT_USERNAME` and `E2E_REDDIT_PASSWORD`, never in the
   repository;
3. `Phone.BaconReader.LogInAsync()`, built with the first scenario that needs it: the drawer's account entry, Reddit's login page
   in the app's web view (user name, password, "Log in"), "Allow" on Reddit's authorization page for the spoofed app,
   then back on the main screen, logged in. Its Given: `… is open, logged in`.

## Isolation

One device, so scenarios run one after another (`xunit.runner.json`). Each starts from the app's data cleared, so
nothing one does (a login, a setting) reaches another.
