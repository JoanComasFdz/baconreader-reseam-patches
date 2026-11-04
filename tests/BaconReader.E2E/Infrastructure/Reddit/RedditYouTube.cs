// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using BaconReader.FakeReddit.World;

namespace BaconReader.E2E.Infrastructure.Reddit;

/// <summary>
/// YouTube, which the fake Reddit answers too ("E2E fake Reddit" moves YouTube's oEmbed endpoint and thumbnails to it),
/// in the Gherkins' words. Comments and posts link a video at YouTube's own address, which the patches know it by.
/// </summary>
public sealed partial class Reddit
{
    /// <summary>"YouTube has a video "&lt;title&gt;" on the channel "@&lt;channel&gt;"".</summary>
    public void YouTubeHasAVideo(string title, string channel) => world.YouTube.AddVideo(title, channel.TrimStart('@'));

    /// <summary>"YouTube has removed the video "&lt;title&gt;"": oEmbed answers it 404, as YouTube does.</summary>
    public void YouTubeHasRemovedAVideo(string title, string channel) =>
        world.YouTube.AddVideo(title, channel.TrimStart('@'), removed: true);

    /// <summary>"Reddit has a link post "&lt;title&gt;" in r/&lt;subreddit&gt; linking to the YouTube video "&lt;video&gt;"", with no thumbnail or preview of Reddit's.</summary>
    public void HasALinkPostLinkingToTheYouTubeVideo(string title, string subreddit, string video) =>
        world.AddPost(subreddit, title, post => post.LinkingTo(YouTubeVideoTitled(video).Link));

    internal YouTubeVideo YouTubeVideoTitled(string title) =>
        world.YouTube.FindVideoTitled(title)
            ?? throw new InvalidOperationException($"The scenario's YouTube has no video \"{title}\": a Given line makes it first.");
}
