// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using JoanComasFdz.AssertingThat;

namespace BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;

/// <summary>What a test checks of the post the detail screen shows.</summary>
public static class DetailPostSnapshotAssertions
{
    /// <summary>BaconReader shows the post "<paramref name="title"/>" on its detail screen.</summary>
    public static AssertingThat<DetailPostSnapshot> ShowsThePost(this AssertingThat<DetailPostSnapshot> that, string title)
    {
        Assert.True(
            that.InstanceToAssert.Title == title,
            $"BaconReader shows the post \"{that.InstanceToAssert.Title}\", but the post \"{title}\" was expected.");
        return that;
    }
}
