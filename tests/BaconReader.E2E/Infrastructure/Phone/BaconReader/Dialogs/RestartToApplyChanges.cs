// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone.BaconReader.Dialogs;

/// <summary>
/// "Restart to apply changes?", the dialog the patches' settings show over BaconReader's settings when they close after
/// a setting changed: "Restart" restarts the app, "Later" closes the dialog, and so does Back.
/// </summary>
public sealed class RestartToApplyChanges(AndroidDriver driver)
{
    private static readonly OpenQA.Selenium.By Title = Locate.Text("Restart to apply changes?");
    private static readonly OpenQA.Selenium.By Later = Locate.Text("Later");

    /// <summary>Waits until the dialog is shown.</summary>
    public Task WaitUntilShownAsync() =>
        Waiting.UntilShownAsync(driver, Title, TimeSpan.FromSeconds(10), "The \"Restart to apply changes?\" dialog");

    /// <summary>Taps "Later", which closes the dialog without restarting the app.</summary>
    public Task ClickLaterAsync() => OnScreen.PressAsync(driver, Later);
}
