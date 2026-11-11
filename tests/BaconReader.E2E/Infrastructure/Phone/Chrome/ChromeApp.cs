// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone.Chrome;

/// <summary>Chrome, the phone's browser: the pages a user opens in it.</summary>
public sealed class ChromeApp(AndroidDriver driver)
{
    private const string Package = "com.android.chrome";

    /// <summary>A repository's page on GitHub, its README under its files.</summary>
    public GitHubRepository GitHubRepository { get; } = new(driver);

    /// <summary>Opens <paramref name="address"/> in Chrome, as typing it in its address bar does.</summary>
    public Task OpenAsync(string address) =>
        OnScreen.DoAsync(driver, () => driver.ExecuteScript("mobile: deepLink", new Dictionary<string, object> { ["url"] = address, ["package"] = Package }));
}
