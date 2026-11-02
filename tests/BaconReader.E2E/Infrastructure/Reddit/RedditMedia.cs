// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using BaconReader.FakeReddit.World;

namespace BaconReader.E2E.Infrastructure.Reddit;

/// <summary>The fake Reddit's media posts, thumbnails and avatars, in the Gherkins' words.</summary>
public sealed partial class Reddit
{
    /// <summary>"Reddit has an image post "&lt;title&gt;" in r/&lt;subreddit&gt;": a JPEG uploaded to i.redd.it.</summary>
    public void HasAnImagePost(string title, string subreddit) => world.AddPost(subreddit, title, post => post.WithImage());

    /// <summary>"Reddit has a GIF post "&lt;title&gt;" in r/&lt;subreddit&gt;": a GIF uploaded to i.redd.it.</summary>
    public void HasAGifPost(string title, string subreddit) => world.AddPost(subreddit, title, post => post.WithGif());

    /// <summary>
    /// "Reddit has an image post "&lt;title&gt;" in r/&lt;subreddit&gt; whose thumbnail is a signed preview": its
    /// thumbnail's URL signed, its "&amp;" HTML-escaped, as Reddit sends it without raw_json.
    /// </summary>
    public void HasAnImagePostWithASignedThumbnail(string title, string subreddit) =>
        world.AddPost(subreddit, title, post =>
        {
            post.WithImage();
            post.WithASignedThumbnail();
        });

    /// <summary>"Reddit has a video post "&lt;title&gt;" in r/&lt;subreddit&gt;": a video uploaded to v.redd.it, with sound.</summary>
    public void HasAVideoPost(string title, string subreddit) => world.AddPost(subreddit, title, post => post.AsVideo());

    /// <summary>
    /// "Reddit has a link post "&lt;title&gt;" in r/&lt;subreddit&gt; with a preview and no thumbnail": a link to a web
    /// page, with Reddit's preview of it.
    /// </summary>
    public void HasALinkPostWithAPreviewAndNoThumbnail(string title, string subreddit) =>
        world.AddPost(subreddit, title, post => post.LinkingToAPageWithAPreview());

    /// <summary>"Reddit has an NSFW image post "&lt;title&gt;" in r/&lt;subreddit&gt;".</summary>
    public void HasAnNsfwImagePost(string title, string subreddit) =>
        world.AddPost(subreddit, title, post => post.WithImage().Nsfw());

    /// <summary>"Reddit has a spoiler image post "&lt;title&gt;" in r/&lt;subreddit&gt;".</summary>
    public void HasASpoilerImagePost(string title, string subreddit) =>
        world.AddPost(subreddit, title, post => post.WithImage().Spoiler());

    /// <summary>"Reddit has a gallery post "&lt;title&gt;" in r/&lt;subreddit&gt; of &lt;images&gt; images".</summary>
    public void HasAGalleryPostOf(string title, string subreddit, int images) =>
        world.AddPost(subreddit, title, post => post.AsGallery(images));

    /// <summary>"Reddit has a crosspost "&lt;title&gt;" in r/&lt;subreddit&gt; of the post "&lt;shared&gt;"".</summary>
    public void HasACrosspostOf(string title, string subreddit, string shared)
    {
        var sharedPost = PostTitled(shared);
        world.AddPost(subreddit, title, post => post.CrosspostOf(sharedPost));
    }

    /// <summary>"the user "&lt;user&gt;" has an avatar".</summary>
    public void TheUserHasAnAvatar(string user) => world.GiveAvatar(user);
}
