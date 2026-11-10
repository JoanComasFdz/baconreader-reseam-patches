// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone.ReseamManager.Screens;

/// <summary>Reseam Manager's home screen: the apps its patch sources patch, and "Pick an APK" for one's own APK.</summary>
public sealed class Home(AndroidDriver driver)
{
    private static readonly OpenQA.Selenium.By PickAnApk = Locate.Description("Pick an APK");

    /// <summary>Waits until the home screen shows "Pick an APK".</summary>
    public Task WaitUntilShownAsync() =>
        Waiting.UntilShownAsync(driver, PickAnApk, TimeSpan.FromSeconds(10), "Reseam Manager's home screen");

    /// <summary>Taps "Pick an APK": Android's file picker opens.</summary>
    public Task ClickPickAnApkAsync() => OnScreen.PressAsync(driver, PickAnApk);
}
