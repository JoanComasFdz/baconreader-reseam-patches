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
- **No code without a user.** Before porting a branch from ReVanced's shared code, check that BaconReader reaches it: grep the app for the endpoint, string or call. Leave out what it never uses, and say so in the README.

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
