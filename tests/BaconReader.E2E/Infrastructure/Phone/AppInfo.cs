// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using BaconReader.E2E.Infrastructure.Device;
using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone;

/// <summary>
/// Android's App info for BaconReader: "Force stop", and "Clear storage" under "Storage &amp; cache". Done over adb, as
/// those buttons do it, not by tapping through Android's settings: a scenario stops or clears the app to open it again,
/// and what it checks is BaconReader.
/// </summary>
public sealed class AppInfo(AndroidDriver driver)
{
    /// <summary>Stops BaconReader, as "Force stop" does: its data stays, and opening it starts it afresh.</summary>
    public Task ForceStopBaconReaderAsync() =>
        OnScreen.DoAsync(driver, () => Commands.ShellAsync($"am force-stop {E2EDevice.Package}").GetAwaiter().GetResult());

    /// <summary>
    /// Clears BaconReader's storage, as "Clear storage" does: stopped, and as just installed, with its notifications denied
    /// as before each scenario (<see cref="PatchedApps.ClearDataAsync"/>). Android keeps the links selected on its "Open by
    /// default" screen.
    /// </summary>
    public Task ClearBaconReadersStorageAsync() =>
        OnScreen.DoAsync(driver, () => PatchedApps.ClearDataAsync().GetAwaiter().GetResult());
}
