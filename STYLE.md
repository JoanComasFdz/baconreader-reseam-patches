# Style

How patches in this bundle are written. It follows Reseam's own bundle ([Reseam/patches](https://github.com/Reseam/patches)) and the engine's [STYLE.md](https://github.com/Reseam/reseam/blob/main/STYLE.md), plus what porting from ReVanced taught us. When this file and Reseam's code disagree, Reseam's code wins; update this file.

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

- **Name:** what users see, in sentence case: "Fix Redgifs API", "Show tables".
- **Description:** one sentence about what the user gets, not how: "Makes Redgifs videos play again."
- **Comment above the patch:** why it exists. This is the place for how BaconReader breaks.
- **Order inside the block:** `description`, `compatibleWith`, `dependsOn`, `settings`, options, then `execute`.
- **Compatibility:** always `compatibleWith(BACONREADER, BACONREADER_PREMIUM)` from `core/BaconReader.kt`. Both are pinned to 6.1.4, BaconReader's last release.
- **Fixes have no setting.** A patch that restores broken behavior runs always, like Reseam's "Fix login". A patch that changes how the app looks or behaves gets a toggle.

## Targets

- Targets are `private val`s after the patch that uses them. A target several patches use goes in `core/`.
- BaconReader isn't obfuscated, so `klass("com.onelouder.baconreader.X").method("name")` is fine. Use strings, return types or calls when the name alone is ambiguous.
- Give `method(...)`, `point(...)` and `klass(...) { }` a debug name that says what the target is: `method("tokenRequest")`, not `method("target1")`.
- A target must match exactly one method. Add a constraint rather than `first()`.
- Prefer `replaceAllStrings`, `alwaysReturn`, `redirectTo` and `before`/`after` over raw bytecode edits. When ReVanced removes or rewrites instructions, look for the call to redirect instead.

## Options and settings

- Options are for values chosen once, before patching, such as a client ID. Settings are for anything users may change later.
- **Option descriptions are not trimmed.** Write them as one-line strings, never `"""` blocks. Patch descriptions are trimmed.
- An option with a default can't be unset. If an empty value should skip a change, say so in its description and check `isNotBlank()`.
- Settings live in `apps/baconreader/settings`: one `SettingsGroup` object per feature, in its own file.
- Every setting's title is the string named after its property in snake case, and its summary the same name with `_summary`. They go in `src/main/strings/values/strings.xml`.
- Pin a setting's storage key with `key = "..."` before a release. Renaming the object or property otherwise resets it for every user.
- ReVanced's custom preference screens have no equivalent. Map them to `toggle`, `choice`, `text` (multiline for lists) or `folder`, and note anything that can't be mapped under "Differences from ReVanced" in the README.

## Extensions

- **Kotlin only.** ReVanced's Java is rewritten, like Reseam did for its ReVanced ports.
- `@file:JvmName("Name")` and `@Export` on every function a patch calls. A patch calls it through the generated binding, `com.joancomasfdz.baconreader.<name>.bindings.<Name>`.
- **Parameters that come from the app are nullable.** Reseam compiles extensions without Kotlin's null checks.
- **App classes:**
  - For a library BaconReader bundles (okhttp 4.10.0, Glide, ExoPlayer…), use `compileOnly("group:artifact:<the version BaconReader bundles>")`.
  - For BaconReader's own classes, write a Java stub in `src/stubs/java`.
  - Either way, check that every method you call exists in the app (see [AGENTS.md](AGENTS.md)). The app is minified, so unused library methods may be missing.
- Don't pass Kotlin function types or other `kotlin.*` types to app code or to other bundles.
- **HTTP:** disconnect every `HttpURLConnection` in a `finally` block. Only set a fixed-length body when sending one.
- **Logging:** `android.util.Log` with a `TAG` constant inside the object.
- **Screens:** a new screen is an `@Export class ... : Activity()` in the extension, declared by the patch with `manifest.addActivity(name) { ... }`: a platform theme, and `configChanges` when a rotation must not rebuild it (`inbox/RedditChatPatch.kt`). It opens with an explicit `Intent(context, X::class.java)`. Mount builds leave out patches that edit the manifest.
- **Images in the app's resources:** `resources.addFile("drawable", name, "res/drawable/<name>.xml", xml)` compiles a vector drawable and returns its id, which the patch passes to the extension, for example from `appEntry { }`.
- **No code without a user.** Before porting a branch from ReVanced's shared code, check that BaconReader reaches it: grep the app for the endpoint, string or call. Leave out what it never uses, and say so in the README.

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
| A row that runs code (`OpenLinksPreference`) | No action row. Can't be mapped: leave it out and say so in the README. |
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

## Porting from ReVanced

- Port from the newest ReVanced source that works, and name the branch and commit in the README and `NOTICE`.
- Keep ReVanced's "why" comments, reworded to stand on their own. Drop comments that narrate.
- **Credit:** files ported from ReVanced start with:

  ```kotlin
  // SPDX-FileCopyrightText: 2026 ReVanced contributors
  // SPDX-FileCopyrightText: 2026 JoanComasFdz
  // SPDX-License-Identifier: GPL-3.0-only
  ```

  New files leave out the ReVanced line. No "Ported from" comments.
- **Behavior differences:** any change in behavior goes under "Differences from ReVanced" in the README: what ReVanced does, what the port does, and why.

## Comments

- Code says what it does. A comment says why, only when the code can't show it: an app quirk, a server rule, an invariant.
- No section headers, no narration, no restating the code.
- State facts you checked. "Reddit stopped accepting BaconReader's client" is checked. "Reddit revoked it when free API access ended" is a guess.

## Formatting

- ktfmt's kotlinlang style: `java -jar ktfmt.jar --kotlinlang-style <files>`.
- Run it until it changes nothing: a second run can still change a file it just formatted.
- Format only this bundle's files, not the template's (`settings.gradle.kts`). Re-read comments ktfmt rewraps: it can break a line mid-sentence.

## Licensing

- This bundle's files are GPL-3.0-only. The template's files stay AGPL-3.0-or-later, and the Gradle wrapper is Apache-2.0. `REUSE.toml` says which file is which.
- Every source file has SPDX headers.

## Commits

- One line, conventional, lowercase, like Reseam's: `feat(baconreader): add Show tables, which draws Reddit tables as a grid`, `fix(baconreader): …`, `chore: …`, `docs: …`.
- One commit per patch. Shared setup goes in its own commit before the patches that use it.
- Every commit builds on its own and its patches apply (see [AGENTS.md](AGENTS.md)).

## Pull requests

Follow the layout of the BaconReader MRs on ReVanced:

1. An intro: what the PR adds, and that each patch is its own commit.
2. **Bug fixes** and **New features**: a `###` per patch with short bullets about what the user gets.
3. **Porting from ReVanced**: the source commit and the differences.
4. **Compatibility**, **License** and **Testing**, written in the first person. Testing says what was checked on a phone and what only statically.
5. **AI disclosure**.
