// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using System.Collections.Immutable;
using BaconReader.E2E.Infrastructure.Vocabulary;
using OpenQA.Selenium.Appium.Android;
using OpenQA.Selenium.Appium.Android.Enums;

namespace BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;

/// <summary>
/// The main screen's left menu, which the toolbar's menu button opens: "Add account" or the user name logged in, then the
/// account's entries (Profile, Inbox…) and the app's (Subreddit, Settings…).
/// </summary>
public sealed class LeftMenu(AndroidDriver driver)
{
    private static readonly OpenQA.Selenium.By MenuButton = Locate.Description("Navigate up");
    private static readonly OpenQA.Selenium.By Menu = Locate.AppId("drawerView");
    private static readonly OpenQA.Selenium.By Entry = Locate.AppId("text");

    /// <summary>Opens the menu: taps the toolbar's menu button.</summary>
    public Task OpenAsync() => OnScreen.PressAsync(driver, MenuButton);

    /// <summary>
    /// Waits until the menu is shown, slid all the way in (where it is no longer changes from one look to the next): a
    /// tap while it still slides lands where its entry is no more.
    /// </summary>
    public Task WaitUntilShownAsync()
    {
        System.Drawing.Rectangle? last = null;
        return Waiting.UntilAsync(
            () =>
            {
                var shown = driver.FindElements(Menu).FirstOrDefault(menu => menu.Displayed);
                var now = shown is null ? (System.Drawing.Rectangle?)null : new System.Drawing.Rectangle(shown.Location, shown.Size);
                var settled = now is not null && now == last;
                last = now;
                return settled;
            },
            TimeSpan.FromSeconds(10),
            "The main screen's left menu sliding in");
    }

    /// <summary>
    /// Taps <paramref name="entry"/>, which opens what it names over the menu. BaconReader at times ignores the tap (seen
    /// by hand too, another tap works; on the emulator, once two taps in a row): while the menu still shows the entry
    /// 10 s later (the login screen takes up to 8 s to show), it taps again, up to three taps.
    /// </summary>
    public async Task ClickAsync(MenuEntry entry)
    {
        var target = Locate.AppIdWithText("text", entry.ToShownText());
        for (var taps = 1; ; taps++)
        {
            await OnScreen.PressAsync(driver, target);
            if (taps == 3)
            {
                // What it opens is the caller's to wait for.
                return;
            }

            try
            {
                await Waiting.UntilAsync(
                    () => !driver.FindElements(target).Any(view => view.Displayed),
                    TimeSpan.FromSeconds(10),
                    $"\"{entry.ToShownText()}\" opening");
                return;
            }
            catch (TimeoutException)
            {
                // Ignored: tap again.
            }
        }
    }

    /// <summary>Closes the menu, as Android's Back does.</summary>
    public Task CloseAsync() => OnScreen.DoAsync(driver, () => driver.PressKeyCode(AndroidKeyCode.Back));

    /// <summary>What the menu shows: its entries, top to bottom.</summary>
    public Task<LeftMenuSnapshot> ReadAsync() =>
        OnScreen.ReadAsync(driver, () => new LeftMenuSnapshot(
            [.. driver.FindElement(Menu).FindElements(Entry).Select(entry => MenuEntryShowing.ReadMenuEntry(entry.Text.Trim()))]));
}

/// <summary>What the left menu shows: its entries, top to bottom.</summary>
public sealed record LeftMenuSnapshot(ImmutableArray<MenuEntry> Entries);
