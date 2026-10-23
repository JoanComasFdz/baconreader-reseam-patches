// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using System.Reflection;
using Xunit;
using Xunit.Sdk;
using Xunit.v3;

namespace BaconReader.E2E.Infrastructure;

/// <summary>
/// The screen a scenario is played on (E2E-TEST-CODE-RULES.md, rule a): a scenario's <c>[PlayOnPhone]</c> plays it
/// once, and the project's <c>BaseE2E</c> opens the app on that screen. One screen today, the emulator; the enum keeps
/// the reference project's shape, so a tablet can join without changing the scenarios.
/// </summary>
public enum Screen
{
    /// <summary>The emulator: a Nexus 5, 1080×1920, Android 14.</summary>
    Phone,
}

/// <summary>What the PR shows for each <see cref="Screen"/>, and its Gherkin tag.</summary>
public static class ScreenShowing
{
    /// <summary>"📱 Phone": the run's label in the test's name and the PR's subtitle.</summary>
    public static string ToLabel(this Screen screen) =>
        screen switch
        {
            _ => "📱 Phone",
        };

    /// <summary>"@phone": the Gherkin tag that asks for the screen.</summary>
    public static string ToTag(this Screen screen) => "@" + screen.ToString().ToLowerInvariant();
}

/// <summary>Plays the scenario on the phone (<see cref="Screen.Phone"/>). At most once per scenario.</summary>
[AttributeUsage(AttributeTargets.Method, AllowMultiple = false)]
public sealed class PlayOnPhoneAttribute() : PlayOnAttribute(Screen.Phone);

/// <summary>
/// One play of a scenario on <see cref="Screen"/>: a theory row whose one argument is the screen, labelled "📱 Phone".
/// </summary>
public abstract class PlayOnAttribute(Screen screen) : DataAttribute
{
    /// <summary>The screen this attribute plays the scenario on.</summary>
    public Screen Screen { get; } = screen;

    public override ValueTask<IReadOnlyCollection<ITheoryDataRow>> GetData(MethodInfo testMethod, DisposalTracker disposalTracker) =>
        new([new TheoryDataRow<Screen>(Screen) { Label = Screen.ToLabel() }]);

    public override bool SupportsDiscoveryEnumeration() => true;
}
