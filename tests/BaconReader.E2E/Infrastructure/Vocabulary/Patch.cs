// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

namespace BaconReader.E2E.Infrastructure.Vocabulary;

/// <summary>
/// A patch of baconreader-reseam-patches, as its bundle names it. An enum, not a union like the other kinds (rule e): a
/// scenario names its patches in <see cref="PatchedWithAttribute"/>, and an attribute takes only constants. A case is
/// added when a test needs it.
/// </summary>
public enum Patch
{
    SpoofClient,
    DisableFinalReleaseAnnouncement,
    PromptToOpenRedditLinks,
}

/// <summary>What the bundle names each <see cref="Patch"/>: the only place those strings live.</summary>
public static class PatchShowing
{
    /// <summary>The patch's name in the bundle, as <c>reseam patch --enable</c> takes it and the Gherkin quotes it.</summary>
    public static string ToShownText(this Patch patch) =>
        patch switch
        {
            Patch.SpoofClient => "Spoof client",
            Patch.DisableFinalReleaseAnnouncement => "Disable final release announcement",
            Patch.PromptToOpenRedditLinks => "Prompt to open Reddit links",
            _ => throw new ArgumentOutOfRangeException(nameof(patch), patch, "A patch with no name in the bundle."),
        };
}

/// <summary>
/// The patches the scenario's app is patched with, the fewest it needs: BaseE2E patches the clean APK with exactly these
/// (and what they depend on) and installs it before the scenario starts. A scenario that logs in needs
/// <see cref="Patch.SpoofClient"/>.
/// </summary>
[AttributeUsage(AttributeTargets.Method, AllowMultiple = false)]
public sealed class PatchedWithAttribute(params Patch[] patches) : Attribute
{
    public IReadOnlyList<Patch> Patches { get; } = patches;
}
