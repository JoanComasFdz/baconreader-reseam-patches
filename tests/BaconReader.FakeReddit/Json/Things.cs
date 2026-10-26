// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using BaconReader.FakeReddit.World;

namespace BaconReader.FakeReddit.Json;

/// <summary>The world's posts, comments, subreddits and accounts as the things Reddit sends, seen by <c>viewer</c> (null logged out).</summary>
internal sealed class Things(RedditWorld world)
{
    public Thing Link(Post post, Account? viewer)
    {
        var self = post.Url is null;
        var url = post.Url ?? "https://www.reddit.com" + post.Permalink;
        var vote = viewer is not null && post.Votes.TryGetValue(viewer.Name, out var direction) ? direction : 0;
        var created = Seconds(post.Created);
        return new Thing("t3", new LinkData
        {
            Subreddit = post.Subreddit.Name,
            Selftext = post.Text ?? "",
            AuthorFullname = AuthorFullName(post.Author),
            Saved = viewer is not null && post.SavedBy.Contains(viewer.Name),
            Title = post.Title,
            SubredditNamePrefixed = "r/" + post.Subreddit.Name,
            Hidden = viewer is not null && post.HiddenBy.Contains(viewer.Name),
            LinkFlairCssClass = post.Flair is null ? null : "",
            Name = post.FullName,
            Ups = post.Score + vote,
            ThumbnailWidth = post.Thumbnail is null ? null : 140,
            ThumbnailHeight = post.Thumbnail is null ? null : 105,
            Score = post.Score + vote,
            Thumbnail = post.Thumbnail ?? (self ? "self" : "default"),
            PostHint = post.PreviewImages.Count > 0 ? "image" : self ? null : "link",
            IsSelf = self,
            Created = created,
            Domain = self ? "self." + post.Subreddit.Name : Domain(url),
            SelftextHtml = RedditMarkdown.ToEscapedSelfTextHtml(post.Text, world.BaseUrl),
            Likes = vote switch { 1 => true, -1 => false, _ => null },
            UrlOverriddenByDest = self ? null : url,
            Over18 = post.IsNsfw,
            Preview = post.PreviewImages.Count == 0
                ? null
                : new Preview([.. post.PreviewImages.Select((image, index) => new PreviewImage(new PreviewSource(image, 320, 240), [], [], $"{post.Id}preview{index}"))]),
            Locked = post.IsLocked,
            SubredditId = post.Subreddit.FullName,
            LinkFlairText = post.Flair,
            Id = post.Id,
            Author = post.Author,
            NumComments = post.CommentCount,
            Permalink = post.Permalink,
            Stickied = post.IsStickied,
            Pinned = false,
            Url = url,
            SubredditSubscribers = post.Subreddit.Subscribers,
            CreatedUtc = created,
            IsRedditMediaDomain = url.Contains("/_media/", StringComparison.Ordinal),
        });
    }

    /// <summary>A comment and, recursively, its replies.</summary>
    public Thing Comment(Comment comment, Account? viewer)
    {
        var vote = viewer is not null && comment.Votes.TryGetValue(viewer.Name, out var direction) ? direction : 0;
        var created = Seconds(comment.Created);
        var replies = comment.Replies.Select(reply => Comment(reply, viewer)).ToList();
        return new Thing("t1", new CommentData
        {
            SubredditId = comment.Post.Subreddit.FullName,
            Subreddit = comment.Post.Subreddit.Name,
            Likes = vote switch { 1 => true, -1 => false, _ => null },
            Replies = replies.Count == 0 ? null : ListingData.Listing(replies),
            Saved = viewer is not null && comment.SavedBy.Contains(viewer.Name),
            Id = comment.Id,
            Author = comment.Author,
            CreatedUtc = created,
            ParentId = comment.Parent?.FullName ?? comment.Post.FullName,
            Score = comment.Score + vote,
            AuthorFullname = AuthorFullName(comment.Author),
            Body = comment.Body,
            Name = comment.FullName,
            IsSubmitter = string.Equals(comment.Author, comment.Post.Author, StringComparison.OrdinalIgnoreCase),
            BodyHtml = RedditMarkdown.ToEscapedHtml(comment.Body, world.BaseUrl, comment.Media),
            Permalink = $"{comment.Post.Permalink}{comment.Id}/",
            Created = created,
            LinkId = comment.Post.FullName,
            SubredditNamePrefixed = "r/" + comment.Post.Subreddit.Name,
            Depth = comment.Depth,
            Ups = comment.Score + vote,
            MediaMetadata = comment.Media.Count == 0 ? null : comment.Media.Values.ToDictionary(media => media.Id, Metadata),
        });
    }

    public Thing Subreddit(Subreddit subreddit, Account? viewer)
    {
        var created = Seconds(subreddit.Created);
        var description = subreddit.Description.Length == 0 ? null : RedditMarkdown.ToEscapedHtml(subreddit.Description, world.BaseUrl);
        return new Thing("t5", new SubredditData
        {
            UserIsBanned = viewer is null ? null : false,
            DisplayName = subreddit.Name,
            Title = subreddit.Name,
            DisplayNamePrefixed = "r/" + subreddit.Name,
            Subscribers = subreddit.Subscribers,
            Name = subreddit.FullName,
            PublicDescription = subreddit.Description,
            UserIsSubscriber = viewer is null ? null : viewer.Subscriptions.Contains(subreddit.Name, StringComparer.OrdinalIgnoreCase),
            DescriptionHtml = description,
            PublicDescriptionHtml = description,
            Description = subreddit.Description,
            Created = created,
            CreatedUtc = created,
            Id = subreddit.Id,
            Url = $"/r/{subreddit.Name}/",
        });
    }

    public static UserData User(Account account)
    {
        var created = Seconds(account.Created);
        return new UserData { Id = account.Id, Name = account.Name, Created = created, CreatedUtc = created };
    }

    private MediaMetadata Metadata(CommentMedia media)
    {
        var url = world.MediaUrl(media.Path);
        var full = media.IsAnimated
            ? new MediaSize(media.Height, media.Width) { Gif = url, Mp4 = url }
            : new MediaSize(media.Height, media.Width) { U = url };
        return new MediaMetadata("valid", media.IsAnimated ? "AnimatedImage" : "Image", media.ContentType, [], full, media.Id);
    }

    // The fake has no account behind most authors; Reddit's fullname is "t2_" and an id, and a stable one will do.
    private string AuthorFullName(string author) =>
        world.FindAccount(author)?.FullName ?? "t2_" + author.ToLowerInvariant();

    private static string Domain(string url) => Uri.TryCreate(url, UriKind.Absolute, out var uri) ? uri.Host : "";

    private static double Seconds(DateTimeOffset at) => at.ToUnixTimeSeconds();
}
