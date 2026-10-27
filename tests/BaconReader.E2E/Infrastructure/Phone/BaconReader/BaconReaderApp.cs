// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using BaconReader.E2E.Infrastructure.Phone.BaconReader.Dialogs;
using BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;
using BaconReader.E2E.Infrastructure.Vocabulary;
using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone.BaconReader;

/// <summary>
/// BaconReader on the phone, as the user sees it: one object per screen (an activity: Screens/) and per dialog it shows
/// (Dialogs/), named and nested as the app names and nests them. It knows the scenario's patches
/// (<see cref="PatchedWithAttribute"/>): they decide which dialogs opening it shows. <paramref name="noteAppShown"/> tells
/// BaseE2E when the main screen first shows (the run's startup time).
/// </summary>
public sealed class BaconReaderApp(
    AndroidDriver driver,
    Launcher launcher,
    NotificationPermission notificationPermission,
    IReadOnlyList<Patch> patches,
    Action noteAppShown)
{
    /// <summary>The dialog a fresh BaconReader shows first, unless "Disable final release announcement" is patched in.</summary>
    public FinalReleaseAnnouncement FinalReleaseAnnouncement { get; } = new(driver);

    /// <summary>The dialog "Prompt to open Reddit links" shows once: "Open Reddit links with BaconReader?".</summary>
    public OpenRedditLinksPrompt OpenRedditLinksPrompt { get; } = new(driver);

    /// <summary>The main screen (MainActivity): a feed of posts under the toolbar.</summary>
    public Main Main { get; } = new(driver, noteAppShown);

    /// <summary>The screen "Add account" opens: Reddit's login in a web view.</summary>
    public LogIn LogIn { get; } = new(driver);

    /// <summary>The screen a post opens on (DetailActivity): the post and its comments.</summary>
    public Detail Detail { get; } = new(driver);

    /// <summary>
    /// Opens BaconReader on its main screen, as a scenario's "Given BaconReader … is open" says: opens it from the
    /// launcher, taps "Don’t allow" on Android's notification prompt, "GOT IT :(" on the final release announcement
    /// and "Not now" on the prompt to open Reddit links when its patches show them, and waits until the main screen is
    /// shown.
    /// </summary>
    public async Task OpenAsync()
    {
        await launcher.OpenBaconReaderAsync();
        await notificationPermission.WaitUntilShownAsync();
        await notificationPermission.ClickDontAllowAsync();
        if (!patches.Contains(Patch.DisableFinalReleaseAnnouncement))
        {
            await FinalReleaseAnnouncement.WaitUntilShownAsync();
            await FinalReleaseAnnouncement.ClickGotItAsync();
        }

        if (patches.Contains(Patch.PromptToOpenRedditLinks))
        {
            await OpenRedditLinksPrompt.WaitUntilShownAsync();
            await OpenRedditLinksPrompt.ClickNotNowAsync();
        }

        await Main.WaitUntilShownAsync();
    }
}
