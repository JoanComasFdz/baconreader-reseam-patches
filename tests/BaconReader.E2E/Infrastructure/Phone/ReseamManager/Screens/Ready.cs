// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone.ReseamManager.Screens;

/// <summary>
/// Reseam Manager's "BaconReader Premium is ready" ("36 patches applied in 1 s"), once it patched the app: "Install",
/// and once installed, "Open".
/// </summary>
public sealed class Ready(AndroidDriver driver)
{
    private static readonly OpenQA.Selenium.By Install = Locate.Text("Install");
    private static readonly OpenQA.Selenium.By Open = Locate.Text("Open");

    /// <summary>Waits until it patched the app and shows "Install": its patching takes seconds on the phone.</summary>
    public Task WaitUntilShownAsync() =>
        Waiting.UntilShownAsync(driver, Install, TimeSpan.FromSeconds(180), "Reseam Manager's \"BaconReader Premium is ready\"");

    /// <summary>Taps "Install".</summary>
    public Task ClickInstallAsync() => OnScreen.PressAsync(driver, Install);

    /// <summary>Waits until Android installed the app: "Open" shows.</summary>
    public Task WaitUntilInstalledAsync() =>
        Waiting.UntilShownAsync(driver, Open, TimeSpan.FromSeconds(90), "Reseam Manager's \"Open\", once the app is installed");

    /// <summary>Taps "Open": BaconReader opens.</summary>
    public Task ClickOpenAsync() => OnScreen.PressAsync(driver, Open);
}
