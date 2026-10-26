// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

namespace BaconReader.FakeReddit.World;

/// <summary>
/// What a post holds, in a test's words: <c>world.AddPost("e2e", "Weekly thread", post => post.WithImage())</c>. Each
/// member fills in everything Reddit sends for it (the URL, the thumbnail, the preview), with media the fake serves.
/// </summary>
public sealed class PostBuilder
{
    private readonly RedditWorld world;

    internal PostBuilder(RedditWorld world, Post post)
    {
        this.world = world;
        Post = post;
    }

    /// <summary>The post being built.</summary>
    public Post Post { get; }

    /// <summary>Posted by <paramref name="author"/>.</summary>
    public PostBuilder By(string author)
    {
        Post.Author = author;
        return this;
    }

    /// <summary>A text post whose text is <paramref name="markdown"/>.</summary>
    public PostBuilder WithText(string markdown)
    {
        Post.Text = markdown;
        Post.Url = null;
        return this;
    }

    /// <summary>A link post to <paramref name="url"/>.</summary>
    public PostBuilder LinkingTo(string url)
    {
        Post.Text = null;
        Post.Url = url;
        return this;
    }

    /// <summary>An image post, as Reddit sends one uploaded to i.redd.it: a JPEG unless <paramref name="asset"/> says otherwise.</summary>
    public PostBuilder WithImage(MediaAsset? asset = null)
    {
        var image = asset ?? MediaAsset.Jpeg;
        var url = world.AddMedia($"i.redd.it/{world.NewId()}.{image.Extension}", image.ContentType, image.Bytes);
        LinkingTo(url);
        Post.Thumbnail = url;
        Post.PreviewImages.Add(url);
        return this;
    }

    /// <summary>A GIF post, uploaded to i.redd.it.</summary>
    public PostBuilder WithGif() => WithImage(MediaAsset.Gif);

    /// <summary>Its score: upvotes less downvotes.</summary>
    public PostBuilder WithScore(int score)
    {
        Post.Score = score;
        return this;
    }

    /// <summary>Posted <paramref name="ago"/> before now.</summary>
    public PostBuilder PostedAgo(TimeSpan ago)
    {
        Post.Created = DateTimeOffset.UtcNow - ago;
        return this;
    }

    /// <summary>Marked NSFW.</summary>
    public PostBuilder Nsfw()
    {
        Post.IsNsfw = true;
        return this;
    }

    /// <summary>Stickied at the top of its subreddit.</summary>
    public PostBuilder Stickied()
    {
        Post.IsStickied = true;
        return this;
    }

    /// <summary>With the flair <paramref name="flair"/>.</summary>
    public PostBuilder WithFlair(string flair)
    {
        Post.Flair = flair;
        return this;
    }
}

/// <summary>
/// What a comment holds, in a test's words: <c>world.AddComment(post, "Look", comment => comment.WithGif())</c>.
/// </summary>
public sealed class CommentBuilder
{
    private readonly RedditWorld world;

    internal CommentBuilder(RedditWorld world, Comment comment)
    {
        this.world = world;
        Comment = comment;
    }

    /// <summary>The comment being built.</summary>
    public Comment Comment { get; }

    /// <summary>Written by <paramref name="author"/>.</summary>
    public CommentBuilder By(string author)
    {
        Comment.Author = author;
        return this;
    }

    /// <summary>
    /// A GIF uploaded in the comment, as Reddit sends it: <c>![gif](&lt;id&gt;)</c> in its body, an entry in its
    /// <c>media_metadata</c>, and the GIF at <c>i.redd.it/&lt;id&gt;.gif</c> in the media store.
    /// </summary>
    public CommentBuilder WithGif(MediaAsset? gif = null) => WithMedia("gif", gif ?? MediaAsset.Gif);

    /// <summary>An image uploaded in the comment: <c>![img](&lt;id&gt;)</c>, a PNG unless <paramref name="image"/> says otherwise.</summary>
    public CommentBuilder WithImage(MediaAsset? image = null) => WithMedia("img", image ?? MediaAsset.Png);

    /// <summary>Its score.</summary>
    public CommentBuilder WithScore(int score)
    {
        Comment.Score = score;
        return this;
    }

    private CommentBuilder WithMedia(string kind, MediaAsset asset)
    {
        var id = world.NewId() + "e2emedia";
        var path = $"i.redd.it/{id}.{asset.Extension}";
        world.AddMedia(path, asset.ContentType, asset.Bytes);
        Comment.Media[id] = new CommentMedia(id, asset.ContentType, path, asset.Width, asset.Height);
        var separator = Comment.Body.Length == 0 ? "" : "\n\n";
        Comment.Body += $"{separator}![{kind}]({id})";
        return this;
    }
}
