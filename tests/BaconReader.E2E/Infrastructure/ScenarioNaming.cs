// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using System.Text;
using System.Text.RegularExpressions;
using Humanizer;

namespace BaconReader.E2E.Infrastructure;

/// <summary>A scenario's names, from its Gherkin's title, its method's name and its class's namespace.</summary>
public static partial class ScenarioNaming
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

    /// <summary>
    /// "baconreader-patched-with-spoof-client-opens-phone": a run's GIF name. A run of capitals with a digit inside ("E2E")
    /// stays one word: Humanizer alone ends a word at the digit's next capital ("e2-e").
    /// </summary>
    public static string ToGifName(string methodName, Screen screen)
    {
        var words = CapitalsWithDigit().Replace(methodName, run => run.Value[0] + run.Value[1..].ToLowerInvariant());
        return $"{words.Kebaberize()}-{screen.ToString().ToLowerInvariant()}";
    }

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

    // Ends where the next word starts (a capital and a lower-case letter), at a digit or at the end.
    [GeneratedRegex("[A-Z][A-Z0-9]*[0-9][A-Z0-9]*[A-Z](?=[A-Z][a-z]|[0-9]|$)")]
    private static partial Regex CapitalsWithDigit();
}
