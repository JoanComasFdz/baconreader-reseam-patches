// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using BaconReader.E2E.Infrastructure.Phone.BaconReader;
using BaconReader.E2E.Infrastructure.Vocabulary;
using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone;

/// <summary>
/// The phone as the user holds it: Android's own screens (<see cref="Launcher"/>, <see cref="Downloads"/>;
/// later its settings), and the apps on it (<see cref="BaconReader"/>), each named and nested as the user sees them. Locators stay
/// private to their class (<see cref="Locate"/>).
/// </summary>
public sealed class Phone
{
    public Phone(AndroidDriver driver, PatchSet patches, Action noteAppShown)
    {
        Launcher = new Launcher(driver);
        Downloads = new Downloads(driver);
        BaconReader = new BaconReaderApp(driver, Launcher, patches, noteAppShown);
    }

    /// <summary>Android's launcher: opens an app, as tapping its icon does.</summary>
    public Launcher Launcher { get; }

    /// <summary>Android's Downloads folder: what BaconReader saved in Downloads/BaconReader.</summary>
    public Downloads Downloads { get; }

    /// <summary>BaconReader, patched with the scenario's patches (<see cref="PatchSet"/>).</summary>
    public BaconReaderApp BaconReader { get; }
}
