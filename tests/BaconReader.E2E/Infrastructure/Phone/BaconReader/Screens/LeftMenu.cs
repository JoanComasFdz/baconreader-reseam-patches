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
    // "Navigate up", and while it shows the orange envelope of unread messages, "Navigate up, orange envelope" (from
    // the test-only "E2E inbox envelope label").
    private static readonly OpenQA.Selenium.By MenuButton = Locate.DescriptionStartingWith("Navigate up");
    private const string EnvelopeLabel = ", orange envelope";
    private static readonly OpenQA.Selenium.By Menu = Locate.AppId("drawerView");
    private static readonly OpenQA.Selenium.By Entry = Locate.AppId("text");
    // An entry's icon: before an entry of the menu, after one of the accounts (its ✕, "Add account"'s +).
    private static readonly OpenQA.Selenium.By Icon = Locate.AppId("icon");
    // The arrow at the end of a row that opens what it holds: the user name's opens the accounts.
    private static readonly OpenQA.Selenium.By Arrow = Locate.AppId("expand");
    // "Show user avatars" draws an account's avatar over an object replacement character before its name.
    private const string Avatar = "\uFFFC ";
    // "Patches entry in settings"'s line at the bottom of the drawer, under the menu: "Patches <version> (<patcher>)".
    private static readonly OpenQA.Selenium.By PatchesVersion = Locate.TextStartingWith("Patches ");

    /// <summary>
    /// Opens the menu: taps the toolbar's menu button, after bringing the toolbar back when the posts were scrolled down
    /// (<see cref="Toolbar"/>).
    /// </summary>
    public async Task OpenAsync()
    {
        await Toolbar.BringBackAsync(driver, MenuButton, "The menu button, \"Navigate up\",");
        await OnScreen.PressAsync(driver, MenuButton);
    }

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
    /// Taps <paramref name="entry"/>, which opens what it names over the menu. On the emulator BaconReader at times ignored
    /// the tap, and a tap again 20 s later opened it; on the Pixel 6 Pro no tap was ignored in 4 runs of every scenario
    /// (2026-10-31), so it taps once.
    /// </summary>
    public Task ClickAsync(MenuEntry entry) => OnScreen.PressAsync(driver, Locate.AppIdWithText("text", entry.ToShownText()));

    /// <summary>
    /// Taps the test account's user name at the top of the menu (<see cref="TestAccount"/>), which opens the accounts under
    /// it, or closes them.
    /// </summary>
    public Task ClickTheTestAccountsUserNameAsync() =>
        OnScreen.PressAsync(driver, () => driver.FindElement(Menu).FindElements(Entry).First(entry => Named(entry.Text.Trim(), TestAccount.UserName)));

    /// <summary>
    /// Taps the arrow next to the account <paramref name="user"/> at the top of the menu (the arrow has no label), which
    /// opens the accounts under it, or closes them. It is the arrow on the same row as the account's name; the
    /// account's row among the accounts has none.
    /// </summary>
    public Task ClickArrowNextToAsync(string user) =>
        OnScreen.PressAsync(driver, () =>
        {
            var name = driver.FindElement(Menu).FindElements(Entry).First(entry => Named(entry.Text.Trim(), user) && ArrowOnRowOf(entry) is not null);
            return ArrowOnRowOf(name)!;
        });

    /// <summary>Waits until the accounts show under the user name: their last row, "Add account", is there.</summary>
    public Task WaitUntilAccountsShownAsync() =>
        Waiting.UntilAsync(
            () => Accounts().Any(entry => entry.Text.Trim() == new MenuEntry.AddAccount().ToShownText()),
            TimeSpan.FromSeconds(10),
            "The left menu's accounts under the user name");

    /// <summary>Waits until the accounts under the user name are closed: "Add account" is no longer there.</summary>
    public Task WaitUntilAccountsClosedAsync() =>
        Waiting.UntilAsync(
            () => !Accounts().Any(entry => entry.Text.Trim() == new MenuEntry.AddAccount().ToShownText()),
            TimeSpan.FromSeconds(10),
            "The left menu's accounts under the user name closing");

    /// <summary>
    /// What the menu shows of the accounts: the user name at its top, and the accounts under it, top to bottom (none
    /// while they are closed).
    /// </summary>
    public Task<LeftMenuAccountsSnapshot> ReadAccountsAsync() =>
        OnScreen.ReadAsync(driver, () => new LeftMenuAccountsSnapshot(
            driver.FindElement(Menu).FindElements(Entry).First().Text.Trim(),
            [.. Accounts().Select(entry => MenuEntryShowing.ReadMenuEntry(entry.Text.Trim()))]));

    // Whether a row's text names user, with or without the avatar "Show user avatars" draws before it.
    private static bool Named(string text, string user) => text == user || text == Avatar + user;

    // The arrow whose row is the entry's: the one that spans the entry's middle, up and down.
    private OpenQA.Selenium.IWebElement? ArrowOnRowOf(OpenQA.Selenium.IWebElement entry)
    {
        var middle = entry.Location.Y + entry.Size.Height / 2;
        return driver.FindElement(Menu).FindElements(Arrow)
            .FirstOrDefault(arrow => arrow.Displayed && arrow.Location.Y <= middle && middle <= arrow.Location.Y + arrow.Size.Height);
    }

    // The accounts' rows: each shows its icon after its text, where the menu's entries show it before.
    private IEnumerable<OpenQA.Selenium.IWebElement> Accounts()
    {
        var menu = driver.FindElements(Menu).FirstOrDefault(shown => shown.Displayed);
        if (menu is null) return [];
        var icons = menu.FindElements(Icon).Select(icon => new System.Drawing.Rectangle(icon.Location, icon.Size)).ToList();
        return menu.FindElements(Entry).Where(entry =>
        {
            var text = new System.Drawing.Rectangle(entry.Location, entry.Size);
            return icons.Any(icon => icon.Left >= text.Right && icon.Top < text.Bottom && icon.Bottom > text.Top);
        }).ToList();
    }

    /// <summary>Closes the menu, as Android's Back does.</summary>
    public Task CloseAsync() => OnScreen.DoAsync(driver, () => driver.PressKeyCode(AndroidKeyCode.Back));

    /// <summary>
    /// What the button that opens the menu shows: the orange envelope while there are unread messages, otherwise the menu
    /// icon. UiAutomator can't tell the two images apart; the test-only "E2E inbox envelope label" names the envelope in
    /// the button's description.
    /// </summary>
    public Task<LeftMenuButtonSnapshot> ReadButtonAsync() =>
        OnScreen.ReadAsync(driver, () => new LeftMenuButtonSnapshot(
            driver.FindElement(MenuButton).GetAttribute("content-desc")?.EndsWith(EnvelopeLabel, StringComparison.Ordinal) == true));

    /// <summary>
    /// What the drawer shows at its bottom, under the menu: the patches' version, "Patches &lt;version&gt;
    /// (&lt;patcher&gt;)", and whether it is below every entry of the menu.
    /// </summary>
    public Task<LeftMenuFooterSnapshot> ReadFooterAsync() =>
        OnScreen.ReadAsync(driver, () =>
        {
            var version = driver.FindElements(PatchesVersion).FirstOrDefault(line => line.Displayed);
            var lowestEntry = driver.FindElements(Menu).SelectMany(menu => menu.FindElements(Entry)).Where(entry => entry.Displayed)
                .Select(entry => entry.Location.Y + entry.Size.Height).DefaultIfEmpty(0).Max();
            return new LeftMenuFooterSnapshot(version?.Text, version is not null && version.Location.Y >= lowestEntry);
        });

    /// <summary>What the menu shows: its entries, top to bottom.</summary>
    public Task<LeftMenuSnapshot> ReadAsync() =>
        OnScreen.ReadAsync(driver, () => new LeftMenuSnapshot(
            [.. driver.FindElement(Menu).FindElements(Entry).Select(entry => MenuEntryShowing.ReadMenuEntry(entry.Text.Trim()))]));
}

/// <summary>What the toolbar's button that opens the left menu shows: the orange envelope of unread messages, or the menu icon.</summary>
public sealed record LeftMenuButtonSnapshot(bool ShowsTheOrangeEnvelope);

/// <summary>What the left menu shows: its entries, top to bottom.</summary>
public sealed record LeftMenuSnapshot(ImmutableArray<MenuEntry> Entries);

/// <summary>
/// What the left menu shows of the accounts: the user name at its top ("￼ e2e_user", with the avatar "Show user avatars"
/// draws before it), and the rows of the accounts under it, top to bottom (the accounts, "Add account"), empty while
/// they are closed.
/// </summary>
public sealed record LeftMenuAccountsSnapshot(string UserName, ImmutableArray<MenuEntry> Rows);

/// <summary>
/// What the drawer shows at its bottom: the patches' version ("Patches unknown (Reseam 0.35.1)"; null when none shows),
/// and whether it is below every entry of the menu.
/// </summary>
public sealed record LeftMenuFooterSnapshot(string? PatchesVersion, bool BelowTheMenu);
