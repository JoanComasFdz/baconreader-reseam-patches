// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using System.Collections.Immutable;
using System.Text.RegularExpressions;
using BaconReader.FakeReddit.World;

namespace BaconReader.E2E.Infrastructure.Reddit;

/// <summary>
/// The fake Reddit a scenario's app talks to, in the Gherkin's words: a Given line that says what "Reddit has" is a call
/// here, which builds exactly that data (<c>Reddit.HasAPost("Weekly thread", "e2e")</c>), and a Then line about what
/// Reddit saw reads it here. BaseE2E empties it before each scenario and creates the test account.
/// </summary>
public sealed partial class Reddit(RedditWorld world)
{
    /// <summary>"Reddit has a post "&lt;title&gt;" in r/&lt;subreddit&gt;": a text post, made now.</summary>
    public void HasAPost(string title, string subreddit) => world.AddPost(subreddit, title);

    /// <summary>
    /// "Reddit has a post "&lt;title&gt;" in r/&lt;subreddit&gt; with the text "&lt;text&gt;" and an image": a text post
    /// whose text is <paramref name="text"/> and then an image uploaded in it.
    /// </summary>
    public void HasAPostWithTheTextAndAnImage(string title, string subreddit, string text) =>
        world.AddPost(subreddit, title, post => post.WithTextAndImage(text));

    /// <summary>"the post "&lt;title&gt;" …": a post this scenario's Given made.</summary>
    public RedditPost ThePost(string title) =>
        new(world, world.Posts.SingleOrDefault(post => post.Title == title)
            ?? throw new InvalidOperationException($"The scenario's Reddit has no post \"{title}\": a Given line makes it first."));

    /// <summary>"the test account is subscribed to r/&lt;subreddit&gt;".</summary>
    public void TheTestAccountIsSubscribedTo(string subreddit) =>
        world.Subscribe(world.Accounts.Single(account => account.Name == TestAccount.UserName), subreddit);

    /// <summary>
    /// The image uploaded in the comment "<paramref name="text"/>" (its text, before the image), as Reddit serves it: the file name its
    /// URL ends with, and its size.
    /// </summary>
    public RedditImageSnapshot ReadTheImageOfTheComment(string text)
    {
        var comments = world.Posts.SelectMany(post => post.Comments).Where(comment => comment.Body == text || comment.Body.StartsWith(text + "\n\n", StringComparison.Ordinal)).ToList();
        var comment = comments.Count == 1
            ? comments[0]
            : throw new InvalidOperationException($"The scenario's Reddit has {comments.Count} comments \"{text}\", but one was expected.");
        var image = comment.Media.Values.Single(media => !media.IsVideo);
        var file = world.Media(image.Path) ?? throw new InvalidOperationException($"The fake doesn't serve the image of the comment \"{text}\".");
        return new RedditImageSnapshot(image.Path[(image.Path.LastIndexOf('/') + 1)..], file.Bytes.Length);
    }

    /// <summary>What Reddit's login saw of the app: each step's client ID, redirect URI and User-Agent.</summary>
    public RedditLoginSnapshot ReadLogin() => new([.. world.Logins]);

    /// <summary>
    /// The requests Reddit got for posts while logged in, oldest first: every feed's (the Front Page, r/&lt;subreddit&gt;,
    /// in any sort), with the User-Agent each carried.
    /// </summary>
    public RedditPostsRequestsSnapshot ReadRequestsForPosts() =>
        new([.. world.Requests.Where(request => request is { Method: "GET", Account: not null } && IsFeed(request.Path))]);

    private static bool IsFeed(string path) => FeedPath().IsMatch(path);

    // "/", "/.json", "/hot.json", "/r/e2e/new/": a feed, as the app asks for it.
    [GeneratedRegex("^/(r/[^/]+/?)?(best|hot|new|top|rising|controversial)?(\\.json)?/?$")]
    private static partial Regex FeedPath();
}

/// <summary>A post of the scenario's Reddit, which a Given line adds to.</summary>
public sealed class RedditPost(RedditWorld world, Post post)
{
    /// <summary>"… has a comment "&lt;text&gt;"": a comment that says only <paramref name="text"/>.</summary>
    public void HasAComment(string text) => world.AddComment(post, text);

    /// <summary>"… has a comment "&lt;text&gt;" with an image": <paramref name="text"/>, then an image uploaded in it.</summary>
    public void HasACommentWithAnImage(string text) => world.AddComment(post, text, comment => comment.WithImage());

    /// <summary>"… has a comment "&lt;text&gt;" with a GIF": <paramref name="text"/>, then a GIF uploaded in it.</summary>
    public void HasACommentWithAGif(string text) => world.AddComment(post, text, comment => comment.WithGif());

    /// <summary>"… has a comment "&lt;text&gt;" with a link to an image": <paramref name="text"/>, then a bare link to a JPEG.</summary>
    public void HasACommentWithALinkToAnImage(string text) => world.AddComment(post, text, comment => comment.LinkingToAnImage());

    /// <summary>"… has a comment "&lt;text&gt;" with a video": <paramref name="text"/>, then a video uploaded in it, ten seconds long.</summary>
    public void HasACommentWithAVideo(string text) => world.AddComment(post, text, comment => comment.WithVideo());

    /// <summary>
    /// "… has a comment "&lt;text&gt;" with a link to a video": <paramref name="text"/>, then a bare link to an MP4, ten
    /// seconds long.
    /// </summary>
    public void HasACommentWithALinkToAVideo(string text) => world.AddComment(post, text, comment => comment.LinkingToAVideo());
}

/// <summary>An image uploaded to Reddit, as Reddit serves it: the file name its URL ends with, and its size in bytes.</summary>
public sealed record RedditImageSnapshot(string FileName, long Size);

/// <summary>What Reddit's login saw of the app, step by step (the login page, logging in, Accept, the token).</summary>
public sealed record RedditLoginSnapshot(ImmutableArray<LoginSeen> Steps);

/// <summary>The requests Reddit got for posts while logged in, oldest first.</summary>
public sealed record RedditPostsRequestsSnapshot(ImmutableArray<RecordedRequest> Requests);
