// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using System.Collections.Immutable;
using BaconReader.E2E.Infrastructure.Vocabulary;
using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;

/// <summary>
/// The patches' settings, which "Patches" in BaconReader's settings opens: Reseam's settings screen, "BaconReader
/// patches", with a page for each area ("Images and videos", "Links"…). A page lists its settings, each a title, a
/// summary and a switch; a tap on the title switches it. Reseam's views have no ids: they're found by their texts.
/// </summary>
public sealed class PatchesSettings(AndroidDriver driver)
{
    private static readonly OpenQA.Selenium.By Screen = Locate.Text("BaconReader patches");

    private static OpenQA.Selenium.By Titled(string text) => Locate.Text(text);

    /// <summary>Waits until the screen is shown, its pages listed.</summary>
    public Task WaitUntilShownAsync() =>
        Waiting.UntilShownAsync(driver, Screen, TimeSpan.FromSeconds(10), "The patches' settings, \"BaconReader patches\"");

    /// <summary>Taps <paramref name="page"/>, which opens it.</summary>
    public Task ClickAsync(SettingsPage page) => OnScreen.PressAsync(driver, Titled(page.ToShownText()));

    /// <summary>Waits until <paramref name="page"/> is open: its settings show, the screen's list of pages doesn't.</summary>
    public Task WaitUntilPageShownAsync(SettingsPage page) =>
        Waiting.UntilAsync(
            () => driver.FindElements(Screen).Count == 0 && driver.FindElements(Titled(page.ToShownText())).Any(title => title.Displayed),
            TimeSpan.FromSeconds(10),
            $"The patches' \"{page.ToShownText()}\" settings");

    /// <summary>Taps <paramref name="setting"/>'s title, which switches it.</summary>
    public Task TickAsync(PatchSetting setting) => OnScreen.PressAsync(driver, Titled(setting.ToShownText()));

    /// <summary>What the page shows: the settings it lists, top to bottom.</summary>
    public Task<PatchesSettingsSnapshot> ReadAsync() =>
        OnScreen.ReadAsync(driver, () => new PatchesSettingsSnapshot(
        [
            .. PatchSettingShowing.Listed
                .Select(setting => (setting, title: driver.FindElements(Titled(setting.ToShownText())).FirstOrDefault(title => title.Displayed)))
                .Where(found => found.title is not null)
                .OrderBy(found => found.title!.Location.Y)
                .Select(found => found.setting),
        ]));
}

/// <summary>What a page of the patches' settings shows: the settings it lists, top to bottom.</summary>
public sealed record PatchesSettingsSnapshot(ImmutableArray<PatchSetting> Settings);
