// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using Dunet;

namespace BaconReader.E2E.Infrastructure.Vocabulary;

/// <summary>A page of the patches' settings, as it names it. A case is added when a test needs it.</summary>
[Union]
public partial record SettingsPage
{
    public partial record ImagesAndVideos;

    /// <summary>What the app shows that no case names yet: a failing assertion names it as the app shows it.</summary>
    public partial record Unlisted(string Shown);
}

/// <summary>What the app shows for each <see cref="SettingsPage"/>: the only place those strings live.</summary>
public static class SettingsPageShowing
{
    private static readonly SettingsPage[] Listed = [new SettingsPage.ImagesAndVideos()];

    /// <summary>What the app shows for <paramref name="value"/>.</summary>
    public static string ToShownText(this SettingsPage value) =>
        value.Match(
            imagesAndVideos => "Images and videos",
            unlisted => unlisted.Shown);

    /// <summary>The case the app's <paramref name="shown"/> text names, or <see cref="SettingsPage.Unlisted"/>.</summary>
    public static SettingsPage ReadSettingsPage(string shown) =>
        Listed.FirstOrDefault(value => value.ToShownText() == shown) ?? new SettingsPage.Unlisted(shown);
}
