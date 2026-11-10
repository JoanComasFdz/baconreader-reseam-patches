// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone;

/// <summary>
/// Android's "Allow &lt;app&gt; to send you notifications?", with "Allow" and "Don’t allow", which a newly installed app
/// asks: BaconReader when it opens (a scenario's app installed by BaseE2E has its notifications denied already; one a
/// manager installed asks), Reseam Manager when it first patches.
/// </summary>
public sealed class NotificationPermission(AndroidDriver driver)
{
    private static readonly OpenQA.Selenium.By DontAllow = Locate.Text("Don’t allow");

    /// <summary>Waits until Android asks for <paramref name="app"/>, as its name shows ("BaconReader Premium").</summary>
    public Task WaitUntilShownAsync(string app) =>
        Waiting.UntilShownAsync(driver, Locate.Text($"Allow {app} to send you notifications?"), TimeSpan.FromSeconds(30), $"Android's \"Allow {app} to send you notifications?\"");

    /// <summary>Taps "Don’t allow".</summary>
    public Task ClickDontAllowAsync() => OnScreen.PressAsync(driver, DontAllow);
}
