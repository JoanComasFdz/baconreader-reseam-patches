// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using JoanComasFdz.AssertingThat;

namespace BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;

/// <summary>What a test checks on where a post's detail view is shown.</summary>
public static class DetailMediaLayoutSnapshotAssertions
{
    /// <summary>The screen is upright: the window is higher than wide.</summary>
    public static AssertingThat<DetailMediaLayoutSnapshot> IsUpright(this AssertingThat<DetailMediaLayoutSnapshot> that)
    {
        var layout = that.InstanceToAssert;
        Assert.True(
            layout.Window.Height > layout.Window.Width,
            $"The post is shown in {layout.Window.Width}×{layout.Window.Height} px, but an upright screen, higher than wide, was expected.");
        return that;
    }
}
