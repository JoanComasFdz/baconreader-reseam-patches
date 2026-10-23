// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using Dunet;

namespace BaconReader.E2E.Infrastructure.Vocabulary;

/// <summary>A feed of posts, as the main screen's toolbar names it. A case is added when a test needs it.</summary>
[Union]
public partial record Feed
{
    public partial record FrontPage;

    /// <summary>What the app shows that no case names yet: a failing assertion names it as the app shows it.</summary>
    public partial record Unlisted(string Shown);
}

/// <summary>What the app shows for each <see cref="Feed"/>: the only place those strings live.</summary>
public static class FeedShowing
{
    private static readonly Feed[] Listed = [new Feed.FrontPage()];

    /// <summary>What the app shows for <paramref name="value"/>.</summary>
    public static string ToShownText(this Feed value) =>
        value.Match(
            frontPage => "Front Page",
            unlisted => unlisted.Shown);

    /// <summary>The case the app's <paramref name="shown"/> text names, or <see cref="Feed.Unlisted"/>.</summary>
    public static Feed ReadFeed(string shown) =>
        Listed.FirstOrDefault(value => value.ToShownText() == shown) ?? new Feed.Unlisted(shown);
}
