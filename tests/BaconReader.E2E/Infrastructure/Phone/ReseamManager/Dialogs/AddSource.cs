// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone.ReseamManager.Dialogs;

/// <summary>
/// Reseam Manager's sheet "Add baconreader-patches?": the source's apps, patches, where its updates come from and its
/// signing key, and "Add".
/// </summary>
public sealed class AddSource(AndroidDriver driver)
{
    private static readonly OpenQA.Selenium.By Question = Locate.Text("Add baconreader-patches?");
    private static readonly OpenQA.Selenium.By Add = Locate.Text("Add");

    /// <summary>Waits until Reseam Manager asks to add the patch source.</summary>
    public Task WaitUntilShownAsync() =>
        Waiting.UntilShownAsync(driver, Question, TimeSpan.FromSeconds(30), "Reseam Manager's \"Add baconreader-patches?\"");

    /// <summary>Taps "Add": the source's page opens.</summary>
    public Task ClickAddAsync() => OnScreen.PressAsync(driver, Add);
}
