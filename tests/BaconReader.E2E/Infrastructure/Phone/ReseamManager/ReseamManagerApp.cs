// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using BaconReader.E2E.Infrastructure.Phone.ReseamManager.Dialogs;
using BaconReader.E2E.Infrastructure.Phone.ReseamManager.Screens;
using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone.ReseamManager;

/// <summary>
/// Reseam Manager, the patcher's app (<see cref="Device.TheManager"/> installs it): its screens and dialogs, as a user
/// adds the BaconReader patches and patches BaconReader with them.
/// </summary>
public sealed class ReseamManagerApp(AndroidDriver driver)
{
    /// <summary>"Add baconreader-patches?", when a link hands it a patch source.</summary>
    public AddSource AddSource { get; } = new(driver);

    /// <summary>The patch source's page, "baconreader-patches": its version, its signing key, its apps and patches.</summary>
    public PatchSource PatchSource { get; } = new(driver);

    /// <summary>Its home screen: the apps its patch sources patch, and "Pick an APK".</summary>
    public Home Home { get; } = new(driver);

    /// <summary>An app's page: its patches, the APK it patches and "Patch".</summary>
    public AppPage AppPage { get; } = new(driver);

    /// <summary>"BaconReader Premium is ready", once it patched the app: "Install", then "Open".</summary>
    public Ready Ready { get; } = new(driver);
}
