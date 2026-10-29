// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using BaconReader.E2E.Infrastructure.Phone.BaconReader.Dialogs;
using BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;
using BaconReader.E2E.Infrastructure.Vocabulary;
using OpenQA.Selenium.Appium.Android;
using OpenQA.Selenium.Appium.Android.Enums;

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

    /// <summary>The full-screen viewer a double tap on an image opens ("Embed comment images").</summary>
    public ImageViewer ImageViewer { get; } = new(driver);

    /// <summary>BaconReader's settings (SettingsActivity): the menu of its sections.</summary>
    public Settings Settings { get; } = new(driver);

    /// <summary>The patches' settings, which "Patches" in BaconReader's settings opens.</summary>
    public PatchesSettings PatchesSettings { get; } = new(driver);

    /// <summary>
    /// Opens BaconReader on its main screen, as a scenario's "Given BaconReader … is open" says: opens it from the
    /// launcher, taps "Don’t allow" on Android's notification prompt if it shows, "GOT IT :(" on the final release announcement
    /// and "Not now" on the prompt to open Reddit links when its patches show them, and waits until the main screen is
    /// shown.
    /// </summary>
    public async Task OpenAsync()
    {
        await launcher.OpenBaconReaderAsync();
        await notificationPermission.ClickDontAllowIfShownAsync();
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

    /// <summary>
    /// Logs the test account in from the main screen, as a scenario's "Given the test account is logged in" says: "Add
    /// account" in the left menu, Reddit's "Log In" with the test account, "Accept", then closes the left menu BaconReader
    /// comes back with, and waits until the feed shows its posts. The login screen at times closes by itself while its
    /// fields are filled in (seen on the emulator, more often while recording), back to the left menu: then it starts
    /// again from "Add account", three times at most.
    /// </summary>
    public async Task LogInWithTheTestAccountAsync()
    {
        await Main.LeftMenu.OpenAsync();
        await Main.LeftMenu.WaitUntilShownAsync();
        for (var attempt = 1; ; attempt++)
        {
            try
            {
                await Main.LeftMenu.ClickAsync(new MenuEntry.AddAccount());
                await LogIn.WaitUntilShownAsync();
                await LogIn.TypeTheTestAccountsUserNameAsync();
                await LogIn.TypeTheTestAccountsPasswordAsync();
                await LogIn.ClickLogInAsync();
                await LogIn.WaitUntilConnectShownAsync();
                break;
            }
            catch (Exception exception) when (attempt < 3
                && exception is TimeoutException or OpenQA.Selenium.NoSuchElementException or OpenQA.Selenium.StaleElementReferenceException)
            {
                // The login screen closed when BaconReader is back on the left menu, logged out; otherwise it's another failure.
                if (!await Main.LeftMenu.ShowsAsync(new MenuEntry.AddAccount()))
                {
                    throw;
                }
            }
        }

        await LogIn.ClickAcceptAsync();
        await Main.WaitUntilShownAsync();
        await Main.LeftMenu.WaitUntilShownAsync();
        await Main.LeftMenu.CloseAsync();
        await Main.Posts.WaitUntilPostsShownAsync();
    }

    /// <summary>
    /// Goes back to the main screen's posts with Android's Back, as many times as it takes (from a post, from the
    /// settings and the left menu they were opened from), and waits until they show.
    /// </summary>
    public async Task GoBackToThePostsAsync()
    {
        for (var backs = 0; !await Main.ShowsThePostsAsync(); backs++)
        {
            if (backs == 8)
            {
                throw new InvalidOperationException("The main screen's posts didn't show after going back 8 times.");
            }

            await OnScreen.DoAsync(driver, () => driver.PressKeyCode(AndroidKeyCode.Back));
        }

        await Main.Posts.WaitUntilPostsShownAsync();
    }
}
