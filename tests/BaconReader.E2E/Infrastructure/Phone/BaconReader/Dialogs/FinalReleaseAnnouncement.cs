// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone.BaconReader.Dialogs;

/// <summary>"FINAL RELEASE ANNOUNCEMENT!", the dialog BaconReader shows on its first start, with one button, "GOT IT :(".</summary>
public sealed class FinalReleaseAnnouncement(AndroidDriver driver)
{
    private static readonly OpenQA.Selenium.By Title = Locate.Text("FINAL RELEASE ANNOUNCEMENT!");
    private static readonly OpenQA.Selenium.By GotIt = Locate.Text("GOT IT :(");

    /// <summary>Waits until the dialog is shown (BaconReader shows it a moment after it opens).</summary>
    public Task WaitUntilShownAsync() =>
        Waiting.UntilShownAsync(driver, Title, TimeSpan.FromSeconds(30), "The \"FINAL RELEASE ANNOUNCEMENT!\" dialog");

    /// <summary>Taps "GOT IT :(", which closes the dialog.</summary>
    public Task ClickGotItAsync() => OnScreen.PressAsync(driver, GotIt);
}
