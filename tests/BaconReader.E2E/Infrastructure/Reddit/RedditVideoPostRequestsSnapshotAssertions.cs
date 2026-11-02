// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using JoanComasFdz.AssertingThat;

namespace BaconReader.E2E.Infrastructure.Reddit;

/// <summary>What a test checks of the requests Reddit got for the posts of videos.</summary>
public static class RedditVideoPostRequestsSnapshotAssertions
{
    /// <summary>Reddit was asked for the post of the video of "<paramref name="title"/>", and answered with it.</summary>
    public static AssertingThat<RedditVideoPostRequestsSnapshot> AnsweredThePostOfTheVideoOf(
        this AssertingThat<RedditVideoPostRequestsSnapshot> that, string title)
    {
        var requests = that.InstanceToAssert.Requests;
        Assert.True(
            requests.Any(request => request.Post == title && request.Status == 200),
            $"Reddit wasn't asked for the post of the video of \"{title}\"; it got: {(requests.IsEmpty ? "no request for a video's post" : string.Join(", ", requests.Select(request => $"{request.Post ?? "an unknown video"} ({request.Status})")))}.");
        return that;
    }
}
