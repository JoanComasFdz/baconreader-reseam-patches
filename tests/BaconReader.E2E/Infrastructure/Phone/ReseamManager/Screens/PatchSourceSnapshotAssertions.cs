// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using System.Text.RegularExpressions;
using JoanComasFdz.AssertingThat;

namespace BaconReader.E2E.Infrastructure.Phone.ReseamManager.Screens;

/// <summary>What a test checks on Reseam Manager's page of a patch source.</summary>
public static partial class PatchSourceSnapshotAssertions
{
    /// <summary>It shows the patch source <paramref name="name"/> and a version of it ("0.1.18").</summary>
    public static AssertingThat<PatchSourceSnapshot> ShowsThePatchSource(this AssertingThat<PatchSourceSnapshot> that, string name)
    {
        var shown = that.InstanceToAssert;
        Assert.True(
            shown.Name == name && VersionNumber().IsMatch(shown.Version ?? ""),
            $"Reseam Manager shows the patch source \"{shown.Name}\" at version \"{shown.Version}\", but \"{name}\" and its version were expected.");
        return that;
    }

    [GeneratedRegex(@"^\d+(\.\d+)+(-dev\.\d+)?$")]
    private static partial Regex VersionNumber();
}
