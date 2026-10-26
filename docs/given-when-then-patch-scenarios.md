# Patch scenarios

A **patch scenario** tests what one patch does to the app: the subject a pull request changes (a fix that makes
something work again, a feature it adds, a cleanup it makes). It lives in its app's e2e project under `Scenarios/`, by
the patch's area, as this bundle groups them (`account`, `comments`, `media`, `posts`…): its
[README](../tests/BaconReader.E2E/README.md), "Where tests go".

This document is a child of [How to write a Gherkin](given-when-then-test-rules.md), which holds what every Gherkin
shares (its shape, When, Then, the PR, the checks before handing it over). It adds what a patch scenario does: its
scope and the order of its rounds. Adapted from destiny-loopsmith's
[loop scenarios](https://github.com/JoanComasFdz/destiny-loopsmith/blob/main/docs/given-when-then-loop-scenarios.md).

## Example

[SpoofClientTests.cs](../tests/BaconReader.E2E/Scenarios/Account/SpoofClient/SpoofClientTests.cs): BaconReader
patched with only "Spoof client", installed and opened.

```gherkin
@phone
Scenario: BaconReader patched with Spoof client opens on the front page
  Given BaconReader is patched with "Spoof client"
  And BaconReader is installed
  When I open BaconReader
  And I tap "GOT IT :(" on "FINAL RELEASE ANNOUNCEMENT!"
  Then the toolbar shows the "Front Page" feed
```

## Scope

1. **Test only what the PR's patch changes.** One Gherkin covers one subject (e.g. "Show tables draws a table").
   Behaviour the patch doesn't touch, even if it shows on the same screen, is left out.
2. **Patch with the fewest patches.** The patch under test, and only what it needs to get to its subject: "Spoof
   client" when it logs in, "Prompt to open Reddit links" when it is about opening a Reddit link. Loading posts and
   comments needs none: the fake Reddit answers a logged-out app (its README, "The fake Reddit"). Each one is quoted in
   the Given, one a line.
3. **Cover every case that can change the result.** For example:
   - each value its setting offers (on and off, each choice);
   - the content it acts on and content it must leave alone (a post with a table and one without);
   - the boundary that matters (the 500th comment and the 501st);
   - the app without the patch, when the difference is the point (a fix): a second Gherkin whose Given leaves the patch
     out, showing the broken behaviour.

## Order

1. **Go up, one value at a time:** a setting's values in the order the settings screen lists them, off before on.
2. **Isolate every round.** After a round's Then, go back to where the round started (Back, or close what it opened),
   unless the next round needs what it left. Each round then starts from the same screen as the first, and what it shows
   comes from its own actions only. Keep a state only when the next round is about it, and say so in a `#` line in that
   round ("# keeping the search open: the next round clears it").
3. **Put the offering round first** (what a menu or a setting offers), then the rounds that use each offer.
