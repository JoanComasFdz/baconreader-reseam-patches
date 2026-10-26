// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using JoanComasFdz.AssertingThat;

namespace BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;

/// <summary>What a test checks on the main screen's list of posts.</summary>
public static class PostListSnapshotAssertions
{
    /// <summary>The list shows the posts titled <paramref name="titles"/>, in any order.</summary>
    public static AssertingThat<PostListSnapshot> ShowsThePosts(this AssertingThat<PostListSnapshot> that, params string[] titles)
    {
        var shown = that.InstanceToAssert.Titles;
        var missing = titles.Where(title => !shown.Contains(title)).ToArray();
        Assert.True(
            missing.Length == 0,
            $"The feed shows {Listed(shown)}, but {Listed(missing)} was expected too.");
        return that;
    }

    private static string Listed(IEnumerable<string> titles) =>
        titles.Any() ? string.Join(", ", titles.Select(title => $"\"{title}\"")) : "no post";
}
