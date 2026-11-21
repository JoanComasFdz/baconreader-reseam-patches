// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone.ReseamManager.Screens;

/// <summary>
/// Reseam Manager's page of a patch source it hasn't added, which a link opens: its name ("baconreader-patches"), "It's
/// signed by a key you haven't seen before. Only add patches from people you trust.", its "Version", where its updates
/// come from, its signing key, its apps and patches, and "Add", which adds it and goes back to the app that opened the
/// link (Reseam Manager 1.0; 0.41 asked "Add baconreader-patches?" in a sheet, then showed the source's page).
/// </summary>
public sealed class AddSource(AndroidDriver driver)
{
    // The warning: the page shows it as soon as it read the source.
    private static readonly OpenQA.Selenium.By Warning = Locate.TextContaining("Only add patches from people you trust.");
    private static readonly OpenQA.Selenium.By Add = Locate.Text("Add");

    /// <summary>Waits until the page shows the source.</summary>
    public Task WaitUntilShownAsync() =>
        Waiting.UntilShownAsync(driver, Warning, TimeSpan.FromSeconds(30), "Reseam Manager's page to add \"baconreader-patches\"");

    /// <summary>What the page shows: the source's name, its first line, and its version, after "Version".</summary>
    public Task<PatchSourceSnapshot> ReadAsync() =>
        OnScreen.ReadAsync(driver, () =>
        {
            var lines = driver.FindElements(Locate.AnyText()).Select(line => line.Text.Trim()).ToList();
            var version = lines.IndexOf("Version") is var at and >= 0 && at + 1 < lines.Count ? lines[at + 1] : null;
            return new PatchSourceSnapshot(lines.FirstOrDefault(), version);
        });

    /// <summary>Taps "Add", and waits until Reseam Manager added the source: the page is gone.</summary>
    public async Task ClickAddAsync()
    {
        await OnScreen.PressAsync(driver, Add);
        await Waiting.UntilAsync(() => driver.FindElements(Warning).Count == 0, TimeSpan.FromSeconds(30), "Reseam Manager adding \"baconreader-patches\"");
    }
}

/// <summary>What Reseam Manager's page of a patch source shows: its name and its version ("0.1.18").</summary>
public sealed record PatchSourceSnapshot(string? Name, string? Version);
