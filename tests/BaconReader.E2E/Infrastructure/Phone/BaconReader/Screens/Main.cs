// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;

/// <summary>BaconReader's main screen (MainActivity): a feed of posts under its toolbar.</summary>
public sealed class Main(AndroidDriver driver, Action noteShown)
{
    private static readonly OpenQA.Selenium.By Posts = Locate.AppId("rv_links");

    /// <summary>The bar at the top: the feed's name, Refresh, Search and More options.</summary>
    public Toolbar Toolbar { get; } = new(driver);

    /// <summary>Waits until the main screen is shown: its toolbar and the list its posts load into.</summary>
    public async Task WaitUntilShownAsync()
    {
        await Toolbar.WaitUntilShownAsync();
        await Waiting.UntilShownAsync(driver, Posts, TimeSpan.FromSeconds(30), "The main screen's list of posts");
        noteShown();
    }
}
