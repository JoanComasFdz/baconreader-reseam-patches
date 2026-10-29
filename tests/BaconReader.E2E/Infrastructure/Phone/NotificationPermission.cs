// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone;

/// <summary>
/// Android's dialog "Allow BaconReader Premium to send you notifications?": "Allow" and "Don’t allow". The emulator
/// (Android 14) shows it when a fresh install of BaconReader opens, as every scenario's does. The Pixel 6 Pro (Android
/// 16) doesn't: Android asks its permission controller to show it, for BaconReader targets Android 12, but the request
/// is dropped before the dialog is created (seen in its log on 2026-10-29: a START of GrantPermissionsActivity, never a
/// wm_create_activity), so the Gherkins' step is "(optional)".
/// </summary>
public sealed class NotificationPermission(AndroidDriver driver)
{
    private static readonly OpenQA.Selenium.By Message = Locate.Text("Allow BaconReader Premium to send you notifications?");
    private static readonly OpenQA.Selenium.By DontAllow = Locate.Text("Don’t allow");

    // How long BaconReader is in front without the dialog before it won't come: on the emulator, its "FINAL RELEASE
    // ANNOUNCEMENT!" can show a moment before Android's dialog opens over it.
    private static readonly TimeSpan Settle = TimeSpan.FromSeconds(3);

    /// <summary>
    /// Taps "Don’t allow" if Android asks: waits until the dialog shows, and taps it, or until BaconReader has been in
    /// front for <see cref="Settle"/> without it.
    /// </summary>
    public async Task ClickDontAllowIfShownAsync()
    {
        if (await Waiting.UntilShownOrSettledAsync(
                driver, Message, () => driver.CurrentPackage == E2EDevice.Package, Settle, TimeSpan.FromSeconds(30),
                "Android's \"Allow BaconReader Premium to send you notifications?\" dialog, or BaconReader"))
        {
            await OnScreen.PressAsync(driver, DontAllow);
        }
    }
}
