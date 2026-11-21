// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone.ReseamManager.Dialogs;

/// <summary>
/// Reseam Manager's page of a patch source it hasn't added, "baconreader-patches": "It's signed by a key you haven't seen
/// before. Only add patches from people you trust.", its version, where its updates come from, its signing key, its apps
/// and patches, and "Add" (Reseam Manager 1.0; 0.41 asked "Add baconreader-patches?" in a sheet).
/// </summary>
public sealed class AddSource(AndroidDriver driver)
{
    // The warning, which only this page shows: the source's page, once added, has the same title.
    private static readonly OpenQA.Selenium.By Warning = Locate.TextContaining("Only add patches from people you trust.");
    private static readonly OpenQA.Selenium.By Add = Locate.Text("Add");

    /// <summary>Waits until Reseam Manager asks to add the patch source.</summary>
    public Task WaitUntilShownAsync() =>
        Waiting.UntilShownAsync(driver, Warning, TimeSpan.FromSeconds(30), "Reseam Manager's page to add \"baconreader-patches\"");

    /// <summary>Taps "Add": the source's page opens.</summary>
    public Task ClickAddAsync() => OnScreen.PressAsync(driver, Add);
}
