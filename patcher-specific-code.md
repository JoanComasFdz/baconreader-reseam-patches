<!-- SPDX-FileCopyrightText: 2026 JoanComasFdz -->
<!-- SPDX-License-Identifier: GPL-3.0-only -->

# Patcher-specific: code (Reseam)

How patch and extension code is written for Reseam. The shared rules are in [STYLE.md](STYLE.md). It follows Reseam's own bundle ([Reseam/patches](https://github.com/Reseam/patches)) and the engine's [STYLE.md](https://github.com/Reseam/reseam/blob/main/STYLE.md).

## Layout

```text
apps/baconreader/patch/src/main/kotlin/com/joancomasfdz/patches/baconreader/
  core/BaconReader.kt          packages, pinned versions, class names several patches use
  <area>/<Name>Patch.kt        one patch per file, grouped by area: account, media, ...
apps/baconreader/extensions/<name>/src/main/kotlin/com/joancomasfdz/baconreader/<name>/
                               Kotlin added to the app, one module per feature
apps/baconreader/extensions/<name>/src/stubs/java/
                               stand-ins for BaconReader classes the extension compiles against
apps/baconreader/extensions/common/
                               extension code several patches share
apps/baconreader/settings/     in-app settings and their strings
e2e-patches/apps/baconreader/patch/src/main/kotlin/com/joancomasfdz/patches/baconreader/e2e/
                               test-only patches for the e2e tests, a workspace and bundle of their own, never released
```

- Patch packages are `com.joancomasfdz.patches.baconreader.<area>`, extension packages `com.joancomasfdz.baconreader.<name>`.
- A patch's ID is its package plus property name, and users' saved options use it. Don't rename either after a release.

## Patches

```kotlin
// Why the patch exists: what broke, or what the app does that it shouldn't.
val fixSomething =
    patch("Fix something") {
        description("Makes something work again.")
        compatibleWith(BACONREADER, BACONREADER_PREMIUM)
        dependsOn(...)
        settings(baconReaderSettings, section(...))

        val option = stringOption(...)

        execute { ... }
    }

// Why this method is the one to change, when the name doesn't say.
private val target = method("target") { strings("...") }
```

- **Order inside the block:** `description`, `compatibleWith`, `dependsOn`, `settings`, options, then `execute`.
- **Compatibility:** `compatibleWith(BACONREADER, BACONREADER_PREMIUM)` from `core/BaconReader.kt`.
- Fixes have no `settings(...)`, like Reseam's own "Fix login".

## Targets

- Targets are `private val`s after the patch that uses them. A target several patches use goes in `core/`.
- `klass("com.onelouder.baconreader.X").method("name")`; `strings(...)`, return types or calls when the name alone is ambiguous. No `first()`.
- Debug names on `method(...)`, `point(...)` and `klass(...) { }`: `method("tokenRequest")`.
- High-level edits: `replaceAllStrings`, `alwaysReturn`, `redirectTo`, `before`/`after`.

## Options and settings

- **Option descriptions are not trimmed.** Write them as one-line strings, never `"""` blocks. Patch descriptions are trimmed.
- An empty option value is checked with `isNotBlank()`.
- Settings live in `apps/baconreader/settings`: one `SettingsGroup` object per feature, in its own file.
- Every setting's title is the string named after its property in snake case, and its summary the same name with `_summary`. They go in `src/main/strings/values/strings.xml`.
- Pin a setting's storage key with `key = "..."` before a release. Renaming the object or property otherwise resets it for every user.
- ReVanced's custom preference screens have no equivalent. Map them to `toggle`, `choice`, `text` (multiline for lists) or `folder`, and note anything that can't be mapped under "Differences from ReVanced" in `docs/patcher-specific-patches.md`.

## Extensions

- **Kotlin only.** ReVanced's Java is rewritten, like Reseam did for its ReVanced ports.
- `@file:JvmName("Name")` and `@Export` on every function a patch calls. A patch calls it through the generated binding, `com.joancomasfdz.baconreader.<name>.bindings.<Name>`.
- **Parameters that come from the app are nullable.** Reseam compiles extensions without Kotlin's null checks.
- **App classes:**
  - For a library BaconReader bundles (okhttp 4.10.0, Glide, ExoPlayer…), use `compileOnly("group:artifact:<the version BaconReader bundles>")`.
  - For BaconReader's own classes, write a Java stub in `src/stubs/java`.
  - Either way, check that every method you call exists in the app (see [AGENTS.md](AGENTS.md), "Check a change"). The app is minified, so unused library methods may be missing.
- Don't pass Kotlin function types or other `kotlin.*` types to app code or to other bundles.
- **Screens:** show a feature inside one of the app's screens where it fits, as Reddit chat does over MainActivity's post list (`inbox/RedditChatPatch.kt`): views added to the app's layout, found by the ids the patch passes, and the activity's lifecycle, back, menu and result methods hooked. A new `Activity` needs `manifest.addActivity(name) { ... }`, and mount builds leave out patches that edit the manifest.
- **Images in the app's resources:** `resources.addFile("drawable", name, "res/drawable/<name>.xml", xml)` compiles a vector drawable and returns its id, which the patch passes to the extension, for example from `appEntry { }`.

## Shared building blocks

What the patches share. Use these names; don't add a second of any.

### Settings screen

- **Host:** `baconReaderSettings` in `core/SettingsHost.kt` (`import com.joancomasfdz.patches.baconreader.core.baconReaderSettings`). An internal patch: the settings screen, the app runtime, the brand (`baconReaderBrand`, BaconReader's orange) and the strings extensions read. It also starts the error dialog.
- **Entry:** `settingsEntry`, "Patches entry in settings", in `core/SettingsEntryPatch.kt`. Adds "Patches" at the end of BaconReader's settings menu, which opens the screen. Patches don't depend on it.
- **Pages,** in `core/SettingsPages.kt` (`import com.joancomasfdz.patches.baconreader.core.SettingsPages`). A page shows once a running patch puts a setting on it.

  | Page | Title | Order | What goes there |
  |---|---|---|---|
  | `SettingsPages.Images` | Images and videos | 10 | Embed comment images: images, full width, long press, Wi-Fi only, videos |
  | `SettingsPages.Links` | Links | 20 | Embed comment images: YouTube titles and thumbnails, X and Bluesky posts. Improve post media loading: JavaScript and its site lists. Prompt to open Reddit links |
  | `SettingsPages.Comments` | Comments | 30 | Show user avatars, Load more comments |
  | `SettingsPages.Chat` | Chat | 40 | Reddit chat: the check for new chat messages |

  A setting that fits no page goes in a root section: `section(title, ...)`.
- **Strings:** `com.joancomasfdz.baconreader.settings.BaconReaderStrings`, generated from `apps/baconreader/settings/src/main/strings/values/strings.xml`. One table for the whole bundle, shipped by the host.

### Add a patch's settings

1. A group in `apps/baconreader/settings/src/main/kotlin/com/joancomasfdz/baconreader/settings/<Feature>Settings.kt`:

   ```kotlin
   object UserAvatarsSettings : SettingsGroup(BaconReaderStrings) {
       val userAvatars by toggle(default = false, key = "user_avatars.user_avatars")
       val userAvatarSize by choice(
           default = "0",
           choices = listOf(
               Choice("0", BaconReaderStrings.userAvatarSizeNormal),
               Choice("1", BaconReaderStrings.userAvatarSizeMedium),
           ),
           key = "user_avatars.user_avatar_size",
       )
   }
   ```

2. Its strings in `strings.xml`, grouped under a `<!-- Feature name -->` comment: `user_avatars`, `user_avatars_summary`, the choice titles, and a section title such as `section_user_avatars`. A string with an argument wraps it in `<xliff:g id="name">%1$s</xliff:g>`.
3. Registered in the patch, after `dependsOn`:

   ```kotlin
   settings(
       baconReaderSettings,
       section(
           SettingsPages.Comments,
           BaconReaderStrings.sectionUserAvatars,
           UserAvatarsSettings.userAvatars,
           UserAvatarsSettings.userAvatarSize,
       ),
   )
   ```

4. Read in the extension: `UserAvatarsSettings.userAvatars.value`, `UserAvatarsSettings.userAvatarSize.value.toInt()`. In the patch, `gate(...)` or `whenEnabled(...)` instead of an extension call when that is all it does.

`settings(...)` makes the host a dependency. A patch without settings that uses `ErrorDialog`, `BaconReaderStrings` or `BaconReaderPatches.bundle()` adds `dependsOn(baconReaderSettings)`: without the host, the app has no runtime assets and those fail.

### Mapping ReVanced's preferences

| ReVanced | Reseam |
|---|---|
| `SwitchPreference`, `BooleanSetting` | `toggle`. One `_summary` for both states: describe what "on" does. |
| `parent(...)`, `parentNot(...)` | Nothing in code. In ReVanced they only grey a setting out on the settings screen, and the child's value still applies while the parent is off. Read each setting's own value where ReVanced does; don't add parent checks. Reseam can't grey settings out, so say in the summary when a setting needs its parent. `covers` is not it: it forces the children on. |
| `ListPreference` with `IntegerSetting` | `choice` with values `"0"`, `"1"`…, read with `toInt()`. |
| `TextPreference` (multiline) | `text(default, multiline = true)`, one entry per line. |
| `IntegerSetting` with a custom dialog (`CommentLimitPreference`) | `text(default = "500")`. The extension clamps it and falls back to the default when it isn't a number. No slider. |
| `NonInteractivePreference` with only text (`images_gestures`) | No info row. Fold the text into a related setting's summary. |
| A row that runs code (`OpenLinksPreference`) | No action row. Can't be mapped: leave it out and say so in `docs/patcher-specific-patches.md`. |
| A hidden setting (`OPEN_LINKS_PROMPT_SHOWN`) | Not a setting. Use the app's `SharedPreferences` file `baconreader_patches`. |
| The About row | The screen's own about card. |

### Common extension

`apps/baconreader/extensions/common`, package `com.joancomasfdz.baconreader.common`. Code two or more patches use. A feature extension depends on it in its `build.gradle.kts`:

```kotlin
dependencies { compileOnly(project(":apps:baconreader:extensions:common")) }
```

The engine adds an extension's DEX to the app when patched code refers to one of its classes, and with it every extension DEX that DEX refers to. So `common` comes along with the feature extension; patches don't refer to it themselves.

- **`ErrorDialog.report(tag: String, message: String, error: Throwable? = null)`.** Logs the error with `Log.e(tag, ...)` and shows it in a dialog over the screen on top, in the bundle's brand: what failed, the causes, the app, patches and Android versions, and the stack trace, with Copy, Save to file and Close. Use it wherever ReVanced calls `ErrorDialog.printException` or `Logger.printException`, with the extension's `TAG`. Callable from any thread. `ErrorDialog.init(application)` is exported and called by the host; don't call it again. `init` also catches every error that escapes the patches' code (any stack frame in `com.joancomasfdz.`), on any thread, and shows it instead of letting the app close; other crashes still close the app and show the next time it opens. That's a safety net: still catch and report errors where the code can recover.
- **`BaconReaderPatches.NAME`** (`"baconreader-patches"`) and **`BaconReaderPatches.bundle(): ReseamBundle`**, for the branded `Ui`: `BaconReaderPatches.bundle().ui(activity).dialog()...`.
- **`GlideUtils`**, the Glide 4.14 that BaconReader bundles, called directly (`compileOnly("com.github.bumptech.glide:glide:4.14.2")` in a module that uses Glide types itself):
  - `MAX_ATTEMPTS = 3`, `RETRY_DELAY_MS = 1000L` (grows with each attempt)
  - `interface LoadCallback { fun onReady(); fun onFailed() }`
  - `with(view: View): RequestManager`, the request manager of the view's activity
  - `load(imageView: ImageView, url: String, thumbnailUrl: String?, callback: LoadCallback?)`
  - `load(imageView: ImageView, file: File, thumbnailUrl: String?, callback: LoadCallback?)`
  - `intoWithRetry(requestBuilder: RequestBuilder<*>?, imageView: ImageView?, onFailed: Runnable? = null)`, for a request the app built
  - `fetch(view: View, url: String, width: Int, height: Int): Drawable`, blocking
  - `isRetryable(error: Throwable?): Boolean`, false only for HTTP 4xx other than 408 and 429
  - `clear(view: View)`
- **`DownloadProgress(context: Context)`**, a progress bar with "1.2 / 3.4 MB": `root: LinearLayout` (add it to the layout, starts hidden), `start()`, `update(received: Long, total: Long)`, `stop()`, `fail()`. Companion: `FAILED_COLOR`, `showFailed(bar: ProgressBar)`, `showLoading(bar: ProgressBar, accent: Int)`, `progressLabel(received: Long, total: Long): String`.
- **`ImageDownloader`**, downloads to a 100 MB cache with progress: `interface Listener { fun onProgress(received: Long, total: Long); fun onDownloaded(file: File); fun onFailed() }`, `cached(context: Context, url: String): File?`, `download(context: Context, url: String, listener: Listener)` (main thread in, main thread out).

Nothing in `common` is `@Export`ed except `ErrorDialog.init`. Patches call the feature extension, which calls `common`. Export from `common` only what a patch must call directly.

### Stubs

- BaconReader's own classes go in each extension's `src/stubs/java`, with the app's package. Stubs aren't shared between modules: a module that needs `Tasks` stubs it too.
- Declare only the members the extension uses, with the app's exact modifiers and types. Abstract methods or empty bodies, no logic. SPDX headers like any file.
- Check each one against the smali: a wrong descriptor compiles and fails in the app. `verify.sh`'s last step lists calls that don't resolve.
- Library classes BaconReader bundles (okhttp 4.10.0, Glide 4.14.2) come from `compileOnly`, not stubs.
- **Only public classes and members.** Extension code lives outside BaconReader's packages, so Android throws `IllegalAccessError` when it uses a class or member that isn't public (or protected and inherited). A stub can't show that: it compiles whatever it declares. Either make the class or member public at patch time (`setAccessFlags`, like `core/ThreadAccess.kt`, as a dependency of every patch that needs it), or use reflection as ReVanced does. `verify.sh` lists every one it finds.

## Formatting

- ktfmt's kotlinlang style: `java -jar ktfmt.jar --kotlinlang-style <files>`.
- Run it until it changes nothing: a second run can still change a file it just formatted.
- Format only this bundle's files, not the template's (`settings.gradle.kts`). Re-read comments ktfmt rewraps: it can break a line mid-sentence.

## Licensing

- The Reseam template's files stay AGPL-3.0-or-later, with the additional terms in `NOTICE`; the Gradle wrapper is Apache-2.0. `REUSE.toml` lists them.
