# How to write a Gherkin

A Gherkin is a Given/When/Then test of a patched app, written so that anyone can play it tap by tap on a phone, with
nothing to work out on their own. Every time the owner asks a session to test a patch, the session writes its Gherkins
first, each in a ```` ```gherkin ```` code block, and agrees them with the owner before writing any C#. These rules apply
to every Gherkin a session writes. A Gherkin is two things at once:

- **an acceptance criterion** of a pull request: every criterion about how the patched app behaves is one (section 1);
- **one end-to-end test**: each Gherkin is exactly one C# scenario in
  [tests/BaconReader.E2E](../tests/BaconReader.E2E/README.md), which holds the Gherkin word for word in its
  `[TestDescription]` and does what it says, line by line. How a Gherkin becomes C# is that project's
  `E2E-TEST-CODE-RULES.md`, rule h.

The PR's e2e comment shows each scenario's title, closed until it is opened, and inside it its Gherkin, the patches its
app was patched with, and a GIF of it played on the phone ([hosting.md](hosting.md), "E2E videos on pull requests").

This document holds what every Gherkin shares. What a patch scenario adds (its scope, the order of its rounds and its
worked example) is in [patch scenarios](given-when-then-patch-scenarios.md).

Adapted from destiny-loopsmith's
[given-when-then-test-rules.md](https://github.com/JoanComasFdz/destiny-loopsmith/blob/main/docs/given-when-then-test-rules.md):
there a Gherkin plays a web app in a browser, on a desktop and a phone; here it plays a patched Android app on an
emulator, a phone.

## Example

The first worked example is
[SpoofClientTests.cs](../tests/BaconReader.E2E/Scenarios/Account/SpoofClient/SpoofClientTests.cs).

## 1. What is a Gherkin

1. **Every acceptance criterion about how the patched app behaves is a Gherkin:** what someone does and sees in the
   app. A criterion checked in a file (a doc, a patch's code, a test, a workflow) stays a plain line.
2. **Layout is a Gherkin too**, checked from where views are on the screen: two views don't overlap, a button sits at a
   row's end, a text isn't cut off. "Looks right", colours and spacing are never a Gherkin: nothing checks them but an
   eye, the reviewer's, on the scenarios' GIFs in the PR's e2e post.
3. **Not yet:** what the emulator can't reach (a second device, a real camera). Such a criterion stays a plain line,
   checked by hand on a phone, and says why.

## 2. Scope

A Gherkin tests the patch the PR changes and every case that can change its result
([patch scenarios](given-when-then-patch-scenarios.md), "Scope").

## 3. The shape

```gherkin
@phone
Scenario: <what it proves, as a statement>
  Given <the app, its patches, and the state it's in>
  When <one action>
  And <one action>
  Then <one check>
  And <one check>
  When …
  Then …
```

1. **Tags:** the screen it is played on, `@phone`, on the line before `Scenario:`. The emulator is the only screen today.
2. **Title:** the `Scenario:` line, one line, a statement ("Show tables draws a Reddit table as a grid").
   It is also the test's method name (each run of letters and digits a word, an apostrophe joining its word,
   capitalized: `ShowTablesDrawsARedditTableAsAGrid`), so it starts with a letter and reads well run together.
3. **Given:** the app, **every patch it is patched with, each in quotes** (the fewest the scenario needs: the patch under
   test, and what it needs to get there, e.g. "Spoof client" to log in), and the state it starts in:
   - `Given BaconReader is patched with "Spoof client" and installed`: the app is installed, not opened yet;
   - `Given BaconReader patched with "Spoof client" and "Show tables" is open, logged in`: opened on its main
     screen, its first-start dialogs answered (section 4.4), logged in to the test account.
4. **One Gherkin per Given.** Checks that start from the same patches and the same state are one Gherkin: each becomes
   a round of it. Split only where the Given differs.
5. **Rounds of When and Then.** A Gherkin is a sequence of rounds. Each round:
   - **When** is the actions that lead to one thing to look at: the first line `When`, the next ones `And`;
   - **Then** is what to look at, right after those actions: the first line `Then`, the next ones `And`.

   Act, then check, then act again. A round never checks something its actions haven't produced yet, and never saves
   all the checks for the end.

## 4. When

1. **One action per line,** in the first person, with the view's exact on-screen name in quotes:
   - `I open BaconReader`
   - `I tap "GOT IT :(" on "FINAL RELEASE ANNOUNCEMENT!"`
   - `I tap the post "Weekly discussion thread"`
   - `I tap "Search" in the toolbar`
   - `I type "bacon" in "Search comments"`
2. **Select by the option's exact text,** never by position ("the second post") and never by default. Write
   `I select "Best"` even when it is already selected.
3. **Keep checking and doing apart.** "The menu offers…" is a Then. Tapping is a When. They are never one line.
4. **The first-start dialogs.** A fresh install shows dialogs before the main screen: BaconReader's "FINAL RELEASE
   ANNOUNCEMENT!" (unless "Disable final release announcement" is patched in) and, with "Prompt to open Reddit links",
   "Open Reddit links with BaconReader?". A Gherkin about opening the app taps them in its own When lines; any other
   folds them into its Given ("is open"), which answers "Not now" to the links prompt, unless it says "with Reddit links
   set up".

## 5. Then

1. **One check per line.**
2. **Quote the exact on-screen text,** copied from the screen while playing it (a screenshot, the UI tree): a title
   ("Front Page"), a button ("GOT IT :("), a line in a list, a toast.
3. **Check only what the patch changes.** The rest of the screen is left out.
4. **Say what must not be there** when it matters: `no post shows an award`, `"View Sidebar" is greyed out`. The test
   checks it too.
5. **Explain anything that looks surprising** in a `#` line right above the check it explains: "# Logged out, Reddit
   refuses the front page: only its title loads." Something that is expected but looks wrong gets a comment, never
   silence.
6. **Layout checks** name the views and what holds between them: `the table's columns don't overlap`, `"Search" sits
   at the sort bar's right end`, `the table fits inside the screen`.

## 6. Order

The order of the rounds: [patch scenarios](given-when-then-patch-scenarios.md), "Order".

## 7. Fixtures

A scenario needs no fixture file: its app is the clean APK patched with the patches its Given names. What it needs
from Reddit (a post with a table, a comment with an image) is a real, stable post, named in the Gherkin by its title
and found by search or link, never by position in a feed. A logged-in scenario uses the test account (its app's
README, "Logging in"); a Gherkin never names its credentials.

## 8. In the pull request

The full Gherkins are agreed with the owner in the session. After that they live in their tests' `[TestDescription]`,
and the PR's e2e comment shows each one with its GIF, so the PR description doesn't repeat them:
under its **Acceptance criteria**, each Gherkin is one checkbox line with its title and its test class.

```markdown
- [ ] BaconReader patched with Spoof client opens on the front page (`SpoofClientTests`)
```

A patch's scenarios go in the same PR as the patch, and the GIFs are posted on it ([hosting.md](hosting.md)).

## 9. Before handing it over

1. **Copy every expected text from the screen** (a screenshot, the UI tree), never from memory or from
   the patch's code.
2. **Its test passes on the emulator:** while you work, `tools/e2e/record.sh --no-video <its class>` plays it fast,
   unrecorded; right before the push (never between pushes), `tools/e2e/record.sh --changed-since origin/main` records
   it, and each GIF shows what the Gherkin says; `tools/e2e/push-gifs.sh` posts them on the PR right after the push. A scenario or patch change after the recording and before the push means recording again.
3. **List anything you saw but didn't expect, and anything the owner has to decide,** in the message that hands the
   work over. Never fold either into a Gherkin as if it were expected.
