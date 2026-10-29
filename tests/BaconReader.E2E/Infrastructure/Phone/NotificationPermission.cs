// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone;

/// <summary>
/// Android's dialog "Allow BaconReader Premium to send you notifications?", which Android 14 shows when a fresh install
/// of BaconReader opens, as every scenario's does: "Allow" and "Don’t allow".
/// </summary>
public sealed class NotificationPermission(AndroidDriver driver)
{
    private static readonly OpenQA.Selenium.By Message = Locate.Text("Allow BaconReader Premium to send you notifications?");
    private static readonly OpenQA.Selenium.By DontAllow = Locate.Text("Don’t allow");

    /// <summary>Waits until the dialog is shown.</summary>
    public Task WaitUntilShownAsync() =>
        Waiting.UntilShownAsync(driver, Message, TimeSpan.FromSeconds(30), "Android's \"Allow BaconReader Premium to send you notifications?\" dialog");

    /// <summary>Taps "Don’t allow", which closes the dialog.</summary>
    public Task ClickDontAllowAsync() => OnScreen.PressAsync(driver, DontAllow);
}
