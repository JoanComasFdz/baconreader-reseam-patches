// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone.BaconReader.Dialogs;

/// <summary>
/// "Open Reddit links with BaconReader?", the dialog the "Prompt to open Reddit links" patch shows once: "Set up" opens
/// Android's "Open by default" settings, "Not now" closes it.
/// </summary>
public sealed class OpenRedditLinksPrompt(AndroidDriver driver)
{
    private static readonly OpenQA.Selenium.By Title = Locate.Text("Open Reddit links with BaconReader?");
    private static readonly OpenQA.Selenium.By SetUp = Locate.Text("Set up");
    private static readonly OpenQA.Selenium.By NotNow = Locate.Text("Not now");

    /// <summary>Waits until the dialog is shown.</summary>
    public Task WaitUntilShownAsync() =>
        Waiting.UntilShownAsync(driver, Title, TimeSpan.FromSeconds(10), "The \"Open Reddit links with BaconReader?\" dialog");

    /// <summary>Taps "Set up": Android's "Open by default" settings for BaconReader open.</summary>
    public Task ClickSetUpAsync() => OnScreen.PressAsync(driver, SetUp);

    /// <summary>Taps "Not now", which closes the dialog.</summary>
    public Task ClickNotNowAsync() => OnScreen.PressAsync(driver, NotNow);
}
