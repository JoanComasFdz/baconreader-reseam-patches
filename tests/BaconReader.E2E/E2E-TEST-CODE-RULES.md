# End-to-end test code rules (binding)

These rules are binding for `tests/BaconReader.E2E`. They are destiny-loopsmith's
[E2E-TEST-CODE-RULES.md](https://github.com/JoanComasFdz/destiny-loopsmith/blob/main/tests/Loopsmith.Web.E2E/E2E-TEST-CODE-RULES.md),
translated from Playwright and a web app to Appium and a patched Android app on an emulator.

The aim: a scenario is one Gherkin, line for line. Every acceptance criterion about a patched app is a Gherkin, written
by [docs/given-when-then-test-rules.md](../../docs/given-when-then-test-rules.md), and every Gherkin is one scenario
here, played on the phone. A scenario reads like the Gherkin it implements, in the app's own words, and everything that
isn't the scenario lives in `Infrastructure/`. What every app's e2e project shares (a Gherkin and its
`[TestDescription]`, the screen and its attribute, `ScenarioNaming`, the record next to a video) lives in
`Infrastructure/` too, a global using of every scenario.

## A full example

```csharp
using BaconReader.E2E.Infrastructure;
using BaconReader.E2E.Infrastructure.Frontend.Screens;
using BaconReader.E2E.Infrastructure.Vocabulary;
using JoanComasFdz.AssertingThat;

namespace BaconReader.E2E.Scenarios.Account;

/// <summary>"Spoof client": BaconReader talks to Reddit as another app (RedReader by default), so logging in works again.</summary>
public sealed class SpoofClientTests : BaseE2E
{
    [Theory, PlayOnPhone]
    [PatchedWith(Patch.SpoofClient)]
    [TestDescription("""
        @phone
        Scenario: BaconReader patched with Spoof client opens on the front page
          Given BaconReader is patched with "Spoof client" and installed
          When I open BaconReader
          And I tap "GOT IT :(" on "FINAL RELEASE ANNOUNCEMENT!"
          Then the toolbar shows the "Front Page" feed
        """)]
    public async Task BaconReaderPatchedWithSpoofClientOpensOnTheFrontPage(Screen screen)
    {
        // Given

        // When
        await Frontend.Launcher.OpenBaconReaderAsync();
        await Frontend.FinalReleaseAnnouncement.WaitUntilShownAsync();
        await Frontend.FinalReleaseAnnouncement.ClickGotItAsync();
        await Frontend.Main.WaitUntilShownAsync();

        // Then
        var toolbar = await Frontend.Main.Toolbar.ReadAsync();
        Asserting.That(toolbar).ShowsFeed(new Feed.FrontPage());
    }
}
```

## a. Test shape

- A class is `public sealed class <Topic>Tests : BaseE2E`, in a folder of its own under its domain folder (rule g).
- **One Gherkin is one scenario:** one method, a `[Theory]` with:
  - `[PlayOnPhone]`: plays it once, on the emulator. It matches the Gherkin's `@phone` tag;
  - `[PatchedWith(...)]`: the patches its Given quotes, exactly those (rule b);
  - `[TestDescription("""…""")]`: the Gherkin it implements, word for word as agreed with the owner, tags and `#` lines
    included;
  - one parameter, `Screen screen`: the screen its attribute plays it on. BaseE2E reads it; the body never does (the
    project's `NoWarn` says so to xunit's analyzer).
- Its **name** is the Gherkin's `Scenario:` title, each run of letters and digits a word (an apostrophe joins its
  word), capitalized (`ScenarioNaming.ToMethodName`). Its **GIF name** is the method name kebaberized, then its screen:
  `bacon-reader-patched-with-spoof-client-opens-on-the-front-page-phone`.
- `ScenarioShapeTests` (`dotnet test`, no emulator) fails a scenario that isn't a `[Theory]` with `[PlayOnPhone]` and
  its `Screen` parameter, has no `[TestDescription]`, isn't named after its Gherkin's title, whose screen attribute isn't
  its Gherkin's tag, or that has no `[PatchedWith]` or a patch its Given doesn't quote.
- The body is `// Given` once, then a `// When` and a `// Then` for each round of the Gherkin, in its order (rule h).
  It calls only `Frontend` and `Asserting.That`: no Appium API, no locators, no adb, no `Assert.*`, no delays.
- Strings appear only for text the app composes around names and numbers (a post's title, a comment line, a toast:
  rule e).

## b. BaseE2E

`Infrastructure/BaseE2E.cs` owns everything that isn't the scenario:
- the skip: a scenario is skipped unless `E2E_APPIUM_URL` is set (the emulator's connection script exports it: README, "The emulator");
- **the patches:** it patches the clean APK with the scenario's `[PatchedWith]` patches, the fewest it needs, with
  `reseam patch --preset none --enable …` and the bundle `./gradlew bundle` built, and installs it with its runtime
  permissions granted (`Device/PatchedApps`). An APK is patched once per set of patches and bundle, signed with one test
  key, and sent to the emulator only when it isn't the one installed (sending one takes ~40 s over the slow link);
- **a fresh app per scenario:** stopped and its data cleared (no login, no setting, no cache from the scenario before),
  its notifications allowed again (clearing revokes them, and Android 14 would ask);
- **the screen:** one Appium session on the emulator, its UiAutomator2 helpers already installed
  (`skipServerInstallation`, `skipDeviceInitialization`; `E2E_APPIUM_INSTALL_HELPERS=1` installs them after the
  emulator was recreated), that never launches or resets the app itself: the scenario opens it;
- **the recording:** when the run records (`E2E_VIDEO_DIR`), Android's `screenrecord` records the screen from before
  the app opens to the end, 540×960 at 1 Mbit/s, with each touch drawn (`show_touches`), in parts of 3 minutes joined at
  the end (`Device/ScreenRecording`); it holds the end on screen a moment and saves the video, also when the scenario
  fails, with its record next to it (title, Gherkin, screen, patches, GIF name, domain folder, when the main screen
  first showed, whether it passed);
- the `Frontend` property.

A scenario never repeats any of that.

## c. Frontend

`Infrastructure/Frontend/` is an object model of the app as the user sees it:
- one class per screen (an activity: `MainActivity` → `Frontend.Main`) in `Screens/` and per dialog in `Dialogs/`,
  named as the app names them, nested as the app nests them: `Frontend.Main.Toolbar`. A part of a screen that the user
  sees as its own (the toolbar, the sort bar, a post's row) gets its own class, named as the app labels it. Android's own
  screens the scenario passes through (the launcher, a permission dialog, "Open by default") get one too;
- `Frontend.OpenAsync()` is a scenario's "Given BaconReader … is open": it opens the app, answers the first-start
  dialogs its patches show ("GOT IT :(", "Not now") and waits for the main screen;
- every member touches the device, so every member is async with an `Async` suffix:
  - **Act**, what a user does, by verb: `Click…Async` (a tap), `Type…Async` (a text field), `Select…Async` (a menu,
    a spinner), `Tick…Async` (a checkbox or a switch), `Swipe…Async`, `Open…Async` (an app, a link);
  - **Wait**: `WaitUntil<what the user expects>Async` (`Main.WaitUntilShownAsync()`), always for something the user
    would see;
  - **Read**: `ReadAsync()` returns an immutable record snapshot of what that part shows, never the raw UI tree:
    vocabulary values (rule e) where it shows one kind of thing, the shown text where it composes a line;
    `ReadLayoutAsync()` returns where its views are on the screen (their bounds, in pixels) for layout checks (rule d).
- **Pace the steps.** Every Act and Read goes through `OnScreen` (`PressAsync`, `ActAsync`, `DoAsync`, `ReadAsync`),
  never straight to Appium: while recording, a step starts no sooner than `--action-gap` seconds after the previous one
  ended, so the GIF doesn't tap faster than the eye follows. A view that is off screen is scrolled to first (through
  `OnScreen`, when a scenario first needs it). Android draws the touches; nothing else is drawn. Waits don't go through
  it (`Waiting`).
- Snapshots don't retry: a scenario waits for what it expects before it reads.
- Locators are private to their class and use only `Locate`: a resource id (`Locate.AppId("toolbarTitle")`, Android's
  own `Locate.AndroidId("button1")`), a content description, or the exact text. Never XPath or a position. A view a patch
  creates in code gets a content description in the patch, in the same PR.

## d. Assertions

- Each snapshot record has its own static `<Type>Assertions` class next to it (`ToolbarSnapshotAssertions` next to
  `Toolbar.cs`), with extension methods on `AssertingThat<T>` in the app's language: `ShowsFeed(new Feed.FrontPage())`,
  `ShowsTable(…)`, `HasNoAwards()`; never `TextIs(…)`.
- A Gherkin's "must not be there" line has its own assertion: `HasNoAwards()`, `IsGreyedOut(…)`.
- **Layout** assertions work on a `ReadLayoutAsync()` snapshot, from the bounds of its views, never from a
  screenshot: `HasNoOverlap()`, `FitsInsideTheScreen()`.
- They return the `AssertingThat<T>` for chaining, use xunit's `Assert` inside, and their message says what the user
  sees against what was expected: "The toolbar shows the "Popular" feed, but "Front Page" was expected."
- Read and assert on separate lines.

## e. Vocabulary

- `Infrastructure/Vocabulary/` holds one Dunet union per kind of thing a scenario names (a feed, a subreddit, a sort,
  a setting…), one file per kind, each with one function from a case to what the app shows (`ToShownText`): the only
  place those strings live. The reverse (`Read<Kind>`) is derived from it.
- **Patches are an enum,** `Patch`, not a union: `[PatchedWith]` is an attribute, and an attribute takes only
  constants. Its `ToShownText` is the patch's name in the bundle, which `reseam patch --enable` takes and the Gherkin
  quotes.
- A Gherkin's quoted **name** of a thing a scenario acts on or checks ("Front Page", "Best", "Spoof client") is a
  vocabulary case in the scenario. A quoted **text the app composes** (a post's title, a comment, a dialog's message)
  is the quoted string itself, exactly as the Gherkin quotes it.
- Every union has an `Unlisted(string Shown)` case: what the app shows that no case names yet, so a failing assertion
  names it as the app shows it.
- A case is added when a scenario needs it; no up-front lists.

## f. Isolation

A scenario never depends on another scenario or on what ran before it: everything it needs comes from BaseE2E (its
patched app, freshly cleared) and its own steps. Its rounds are isolated inside it the way its Gherkin says. There is
one device, so scenarios run one after another (`xunit.runner.json`), in any order.

## g. Adding a test

1. Write the **Gherkin** first (docs/given-when-then-test-rules.md) in a ```` ```gherkin ```` block and agree it with
   the owner: it is the PR's acceptance criterion.
2. Find its **domain folder** under `Scenarios/`: the patch's area, as this bundle groups its patches
   (`account`, `comments`, `media`, `posts`… → `Scenarios/Account/`, `Scenarios/Comments/`…).
3. Its **class** is the patch's, `<Patch>Tests.cs` (`SpoofClientTests.cs`), in a folder of its own named for the class
   without `Tests` (`Scenarios/Account/SpoofClient/SpoofClientTests.cs`). Its namespace stays the domain folder's
   (`BaconReader.E2E.Scenarios.Account`): the recordings' folder is named by it.
4. Write the scenario (rules a–f, and rule h for the translation).
5. Grow `Infrastructure/` only by what this scenario needs: the page-object members, vocabulary cases and assertions it
   calls. A missing content description goes into the patch, in the same PR.
6. Run it unrecorded while you work: `tools/e2e/record.sh --no-video <its class>` (`--no-bundle` when the patches
   didn't change).
7. Record it once, right before the push, never between pushes: `tools/e2e/record.sh --changed-since origin/main`. Each
   GIF shows what the Gherkin says. Right after the push, `tools/e2e/push-gifs.sh` posts the GIFs on the PR.

## h. From a Gherkin to a scenario

Line for line, in the Gherkin's order:

| In the Gherkin | In the scenario |
|---|---|
| `@phone` | `[Theory, PlayOnPhone]` |
| The whole Gherkin | `[TestDescription("""…""")]`, word for word |
| `Scenario: BaconReader patched with Spoof client opens on the front page` | `public async Task BaconReaderPatchedWithSpoofClientOpensOnTheFrontPage(Screen screen)` |
| `Given BaconReader is patched with "Spoof client" and installed` | `[PatchedWith(Patch.SpoofClient)]`; `// Given` stays empty: BaseE2E installed it |
| `Given BaconReader patched with "Spoof client" and "Show tables" is open` | `[PatchedWith(Patch.SpoofClient, Patch.ShowTables)]`; `// Given`: `Frontend.OpenAsync()` |
| A `When`, and the `And` lines after it | One `// When` block: one Act call per line, in order, ending with the `WaitUntil…` for what the Then reads |
| `I open BaconReader` | `Launcher.OpenBaconReaderAsync()` |
| `I tap "GOT IT :(" on "FINAL RELEASE ANNOUNCEMENT!"` | `FinalReleaseAnnouncement.WaitUntilShownAsync()`, `ClickGotItAsync()` |
| A `Then`, and the `And` lines after it | One `// Then` block: one read per part it checks, then one assertion per line, chained |
| `the toolbar shows the "Front Page" feed` | `Main.Toolbar.ReadAsync()`, `ShowsFeed(new Feed.FrontPage())` |
| A quoted name of a thing | A vocabulary case (rule e) |
| A `#` line (why it surprises) | Stays in the Gherkin; a `//` line above the read it explains, when it explains a check |

A Gherkin line with no page-object member or assertion yet gets one (rule g, step 5), named in the same words.
