// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone.ReseamManager.Screens;

/// <summary>Reseam Manager's page of an app: its patches ("36 of 36 patches"), the APK it patches ("Saved APK") and "Patch".</summary>
public sealed class AppPage(AndroidDriver driver)
{
    private static readonly OpenQA.Selenium.By Patch = Locate.Text("Patch");

    /// <summary>Waits until the app's page shows "Patch".</summary>
    public Task WaitUntilShownAsync() =>
        Waiting.UntilShownAsync(driver, Patch, TimeSpan.FromSeconds(30), "Reseam Manager's page of the app");

    /// <summary>Taps "Patch".</summary>
    public Task ClickPatchAsync() => OnScreen.PressAsync(driver, Patch);
}
