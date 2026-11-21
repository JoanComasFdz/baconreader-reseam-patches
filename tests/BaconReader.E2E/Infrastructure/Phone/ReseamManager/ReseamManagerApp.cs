// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using BaconReader.E2E.Infrastructure.Phone.ReseamManager.Screens;
using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone.ReseamManager;

/// <summary>
/// Reseam Manager, the patcher's app (<see cref="Device.TheManager"/> installs it): its screens and dialogs, as a user
/// adds the BaconReader patches and patches BaconReader with them.
/// </summary>
public sealed class ReseamManagerApp(AndroidDriver driver)
{
    /// <summary>The page to add "baconreader-patches", when a link hands it a patch source.</summary>
    public AddSource AddSource { get; } = new(driver);

    /// <summary>Its home screen: the apps its patch sources patch, and "Pick an APK".</summary>
    public Home Home { get; } = new(driver);

    /// <summary>An app's page: its patches, the APK it patches and "Patch".</summary>
    public AppPage AppPage { get; } = new(driver);

    /// <summary>"BaconReader Premium is ready", once it patched the app: "Install", then "Open".</summary>
    public Ready Ready { get; } = new(driver);

    // The source's page, which "Add" sometimes leaves open.
    private static readonly OpenQA.Selenium.By SourcePage = Locate.Text("Use this patch source");

    /// <summary>
    /// Opens Reseam Manager, as tapping its icon does, on its home screen: the back arrow closes the source's page when
    /// "Add" left it open, as a user does to get to "Pick an APK".
    /// </summary>
    public async Task OpenAsync()
    {
        await OnScreen.DoAsync(driver, () => driver.ActivateApp(Device.TheManager.Package));
        if (await Waiting.FindWithinAsync(driver, SourcePage, TimeSpan.FromSeconds(2), "Reseam Manager's page of the source") is not null)
        {
            await OnScreen.PressBackAsync(driver);
        }
    }
}
