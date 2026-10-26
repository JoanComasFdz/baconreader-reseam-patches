// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;

/// <summary>BaconReader's main screen (MainActivity): a feed of posts under its toolbar, and the left menu.</summary>
public sealed class Main(AndroidDriver driver, Action noteShown)
{
    private static readonly OpenQA.Selenium.By PostsList = Locate.AppId("rv_links");

    /// <summary>The bar at the top: the feed's name, Refresh, Search and More options.</summary>
    public Toolbar Toolbar { get; } = new(driver);

    /// <summary>The menu the toolbar's menu button opens: the account, then the app's screens.</summary>
    public LeftMenu LeftMenu { get; } = new(driver);

    /// <summary>The feed's posts.</summary>
    public PostList Posts { get; } = new(driver);

    /// <summary>Waits until the main screen is shown: its toolbar and the list its posts load into.</summary>
    public async Task WaitUntilShownAsync()
    {
        await Toolbar.WaitUntilShownAsync();
        await Waiting.UntilShownAsync(driver, PostsList, TimeSpan.FromSeconds(30), "The main screen's list of posts");
        noteShown();
    }
}
