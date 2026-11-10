// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone.ReseamManager.Screens;

/// <summary>
/// Reseam Manager's page of a patch source ("baconreader-patches"): "Use this patch source", its "Version", where it
/// comes from, its signing key, its apps and its patches, and a back arrow.
/// </summary>
public sealed class PatchSource(AndroidDriver driver)
{
    private static readonly OpenQA.Selenium.By UseThisPatchSource = Locate.Text("Use this patch source");
    private static readonly OpenQA.Selenium.By Back = Locate.Description("Back");

    /// <summary>Waits until the source's page shows.</summary>
    public Task WaitUntilShownAsync() =>
        Waiting.UntilShownAsync(driver, UseThisPatchSource, TimeSpan.FromSeconds(60), "Reseam Manager's page of the patch source");

    /// <summary>What the page shows: the source's name, its first line, and its version, after "Version".</summary>
    public Task<PatchSourceSnapshot> ReadAsync() =>
        OnScreen.ReadAsync(driver, () =>
        {
            var lines = driver.FindElements(Locate.AnyText()).Select(line => line.Text.Trim()).ToList();
            var version = lines.IndexOf("Version") is var at and >= 0 && at + 1 < lines.Count ? lines[at + 1] : null;
            return new PatchSourceSnapshot(lines.FirstOrDefault(), version);
        });

    /// <summary>Taps the back arrow: its home screen.</summary>
    public Task ClickBackAsync() => OnScreen.PressAsync(driver, Back);
}

/// <summary>What Reseam Manager's page of a patch source shows: its name and its version ("0.1.18").</summary>
public sealed record PatchSourceSnapshot(string? Name, string? Version);
