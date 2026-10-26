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

    /// <summary>"the post "&lt;title&gt;" …": a post this scenario's Given made.</summary>
    public RedditPost ThePost(string title) =>
        new(world, world.Posts.SingleOrDefault(post => post.Title == title)
            ?? throw new InvalidOperationException($"The scenario's Reddit has no post \"{title}\": a Given line makes it first."));

    /// <summary>"the test account is subscribed to r/&lt;subreddit&gt;".</summary>
    public void TheTestAccountIsSubscribedTo(string subreddit) =>
        world.Subscribe(world.Accounts.Single(account => account.Name == TestAccount.UserName), subreddit);

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
    /// <summary>"… has a comment with a GIF": a comment with a GIF uploaded in it, and nothing else.</summary>
    public void HasACommentWithAGif() => world.AddComment(post, "", comment => comment.WithGif());
}

/// <summary>What Reddit's login saw of the app, step by step (the login page, logging in, Accept, the token).</summary>
public sealed record RedditLoginSnapshot(ImmutableArray<LoginSeen> Steps);

/// <summary>The requests Reddit got for posts while logged in, oldest first.</summary>
public sealed record RedditPostsRequestsSnapshot(ImmutableArray<RecordedRequest> Requests);
