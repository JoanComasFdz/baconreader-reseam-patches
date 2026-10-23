// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using System.Text;
using Humanizer;

namespace BaconReader.E2E.Infrastructure;

/// <summary>A scenario's names, from its Gherkin's title, its method's name and its class's namespace.</summary>
public static class ScenarioNaming
{
    private const string ScenariosNamespace = ".Scenarios.";

    /// <summary>
    /// "Reaper's Tithe gives 3 stacks for a kill of 3" → "ReapersTitheGives3StacksForAKillOf3": the method's name for a
    /// Gherkin's title. Each run of letters and digits is a word (an apostrophe joins its word), its first letter upper case.
    /// </summary>
    public static string ToMethodName(string title)
    {
        var name = new StringBuilder();
        var startsWord = true;
        foreach (var character in title.Replace("'", "").Replace("’", ""))
        {
            if (!char.IsAsciiLetterOrDigit(character))
            {
                startsWord = true;
                continue;
            }

            name.Append(startsWord ? char.ToUpperInvariant(character) : character);
            startsWord = false;
        }

        return name.ToString();
    }

    /// <summary>"baconreader-patched-with-spoof-client-opens-phone": a run's GIF name.</summary>
    public static string ToGifName(string methodName, Screen screen) =>
        $"{methodName.Kebaberize()}-{screen.ToString().ToLowerInvariant()}";

    /// <summary>
    /// "Account", "Comments/ShowTables": a scenario class's domain folder under its project's Scenarios/, its namespace
    /// after ".Scenarios."; "Unsorted" outside it. The recordings' folder (tools/e2e/record.sh) is named by it.
    /// </summary>
    public static string ToDomainFolder(Type testClass) => ToDomainFolder(testClass.Namespace);

    /// <inheritdoc cref="ToDomainFolder(Type)"/>
    public static string ToDomainFolder(string? @namespace) =>
        (@namespace ?? "") is var name && name.IndexOf(ScenariosNamespace, StringComparison.Ordinal) is var at && at >= 0
            ? name[(at + ScenariosNamespace.Length)..].Replace('.', '/')
            : "Unsorted";
}
