// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using BaconReader.E2E.Infrastructure.Vocabulary;
using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;

/// <summary>
/// The main screen's toolbar: the name of the feed shown, which opens the list of feeds, then Refresh, Search and More
/// options.
/// </summary>
public sealed class Toolbar(AndroidDriver driver)
{
    private static readonly OpenQA.Selenium.By Title = Locate.AppId("toolbarTitle");

    // Each feed in the list the feed's name opens.
    private static OpenQA.Selenium.By FeedInList(Feed feed) => Locate.AppIdWithText("text", feed.ToShownText());

    /// <summary>Waits until the toolbar shows a feed's name.</summary>
    public Task WaitUntilShownAsync() =>
        Waiting.UntilShownAsync(driver, Title, TimeSpan.FromSeconds(10), "The main screen's toolbar");

    /// <summary>Selects <paramref name="feed"/>: taps the feed's name, then <paramref name="feed"/> in the list it opens.</summary>
    public async Task SelectFeedAsync(Feed feed)
    {
        await OnScreen.PressAsync(driver, Title);
        await Waiting.UntilShownAsync(driver, FeedInList(feed), TimeSpan.FromSeconds(10), $"\"{feed.ToShownText()}\" in the toolbar's list of feeds");
        await OnScreen.PressAsync(driver, FeedInList(feed));
    }

    /// <summary>What the toolbar shows: the feed it names.</summary>
    public Task<ToolbarSnapshot> ReadAsync() =>
        OnScreen.ReadAsync(driver, () => new ToolbarSnapshot(FeedShowing.ReadFeed(driver.FindElement(Title).Text.Trim())));
}

/// <summary>What the toolbar shows: the feed it names.</summary>
public sealed record ToolbarSnapshot(Feed Feed);
