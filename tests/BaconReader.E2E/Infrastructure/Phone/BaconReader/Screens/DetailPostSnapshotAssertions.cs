// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using JoanComasFdz.AssertingThat;

namespace BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;

/// <summary>What a test checks on the post shown over its comments.</summary>
public static class DetailPostSnapshotAssertions
{
    /// <summary>The post shows no award icon.</summary>
    public static AssertingThat<DetailPostSnapshot> ShowsNoAward(this AssertingThat<DetailPostSnapshot> that)
    {
        var post = that.InstanceToAssert;
        Assert.True(post.Awards == 0, $"The post shows {post.Awards} award icons, but none was expected.");
        return that;
    }
}
