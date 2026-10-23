// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using BaconReader.E2E.Infrastructure.Vocabulary;
using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;

/// <summary>The main screen's toolbar: the name of the feed shown, then Refresh, Search and More options.</summary>
public sealed class Toolbar(AndroidDriver driver)
{
    private static readonly OpenQA.Selenium.By Title = Locate.AppId("toolbarTitle");

    /// <summary>Waits until the toolbar shows a feed's name.</summary>
    public Task WaitUntilShownAsync() =>
        Waiting.UntilShownAsync(driver, Title, TimeSpan.FromSeconds(60), "The main screen's toolbar");

    /// <summary>What the toolbar shows: the feed it names.</summary>
    public Task<ToolbarSnapshot> ReadAsync() =>
        OnScreen.ReadAsync(driver, () => new ToolbarSnapshot(FeedShowing.ReadFeed(driver.FindElement(Title).Text.Trim())));
}

/// <summary>What the toolbar shows: the feed it names.</summary>
public sealed record ToolbarSnapshot(Feed Feed);
