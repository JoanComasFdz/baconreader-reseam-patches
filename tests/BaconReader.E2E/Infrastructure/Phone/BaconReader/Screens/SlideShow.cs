// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;

/// <summary>
/// The post's screen in its "Slide Show View", which the toolbar's "Slide Show View" switches to and "Detail View"
/// back from: the post's media over the whole screen. A link post shows "Tap to open link", then its page, in a web view
/// whose JavaScript "Improve post media loading" decides ("JavaScript in link posts"); BaconReader's own browser, which a
/// thumbnail opens, always runs JavaScript.
/// </summary>
public sealed class SlideShow(AndroidDriver driver)
{
    private static readonly OpenQA.Selenium.By SlideShowViewButton = Locate.Description("Slide Show View");
    private static readonly OpenQA.Selenium.By DetailViewButton = Locate.Description("Detail View");
    private static readonly OpenQA.Selenium.By TapToOpenLink = Locate.Text("Tap to open link");
    private static readonly OpenQA.Selenium.By Page = Locate.AnyWebPage();

    /// <summary>Taps "Slide Show View" in the post's toolbar, which shows the post's media over the whole screen.</summary>
    public Task ClickSlideShowViewAsync() => OnScreen.PressAsync(driver, SlideShowViewButton);

    /// <summary>Waits until a link post shows "Tap to open link".</summary>
    public Task WaitUntilTapToOpenLinkShownAsync() =>
        Waiting.UntilShownAsync(driver, TapToOpenLink, TimeSpan.FromSeconds(10), "\"Tap to open link\"");

    /// <summary>Taps "Tap to open link", which loads the link post's page.</summary>
    public Task ClickTapToOpenLinkAsync() => OnScreen.PressAsync(driver, TapToOpenLink);

    /// <summary>Waits until the link post's page shows.</summary>
    public Task WaitUntilPageShownAsync() =>
        Waiting.UntilShownAsync(driver, Page, TimeSpan.FromSeconds(10), "The link post's page");

    /// <summary>Taps "Detail View" in the toolbar, which shows the post over its comments again.</summary>
    public Task ClickDetailViewAsync() => OnScreen.PressAsync(driver, DetailViewButton);

    /// <summary>Waits until the toolbar offers "Slide Show View" again: the post's screen is back in its detail view.</summary>
    public Task WaitUntilDetailViewShownAsync() =>
        Waiting.UntilShownAsync(driver, SlideShowViewButton, TimeSpan.FromSeconds(10), "The post's detail view");
}
