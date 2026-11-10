// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using System.Reflection;
using BaconReader.FakeReddit.World;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Http;

namespace BaconReader.FakeReddit.Endpoints;

/// <summary>
/// What the fake has for the scenario, as a page for the phone's browser: <c>/_fake/setup</c>, which reads
/// <c>/_fake/setup.json</c> a few times a second and shows each new thing as it comes. A recording opens it before the
/// app, so the video shows the scenario's Given lines happen (the e2e tests' README, "Recordings").
/// </summary>
internal static class Setup
{
    public static void Map(WebApplication app, RedditWorld world)
    {
        var page = ReadPage();
        app.MapGet("/_fake/setup", () => Results.Content(page, "text/html; charset=utf-8"));
        app.MapGet("/_fake/setup.json", (HttpContext context) =>
        {
            context.Response.Headers.CacheControl = "no-store";
            lock (world.Sync)
            {
                var loggedIn = world.LoggedInUnlocked.Select(account => account.Name).ToHashSet(StringComparer.OrdinalIgnoreCase);
                return Results.Json(new
                {
                    accounts = world.AccountsUnlocked.Select(account => new
                    {
                        name = account.Name,
                        loggedIn = loggedIn.Contains(account.Name),
                        unread = account.Inbox.Count(message => message.IsNew),
                    }),
                    posts = world.PostsUnlocked.Select(post => new
                    {
                        id = post.Id,
                        subreddit = post.Subreddit.Name,
                        title = post.Title,
                        kind = KindOf(post),
                        comments = Flatten(post.Comments, 0).Take(MostComments).ToList(),
                        commentCount = post.CommentCount,
                    }),
                });
            }
        });
    }

    // A post with hundreds of comments shows its first ones and their count.
    private const int MostComments = 12;

    private static IEnumerable<object> Flatten(IEnumerable<Comment> comments, int depth) =>
        comments.SelectMany(comment => new object[] { new { id = comment.Id, author = comment.Author, body = Excerpt(comment.Body), depth } }
            .Concat(Flatten(comment.Replies, depth + 1)));

    private static string Excerpt(string body)
    {
        var line = body.ReplaceLineEndings(" ").Trim();
        return line.Length <= 80 ? line : line[..79] + "…";
    }

    private static string KindOf(Post post) => post switch
    {
        { CrosspostOf: not null } => "Crosspost",
        { IsGallery: true } => "Gallery",
        { VideoId: not null } => "Video",
        { PreviewGif: not null } => "GIF",
        { Url: { } url } when url.Contains("redgifs", StringComparison.OrdinalIgnoreCase) => "RedGIFs video",
        { Url: { } url } when url.Contains("i.redd.it", StringComparison.OrdinalIgnoreCase) => "Image",
        { Url: not null } => "Link",
        _ => "Text",
    };

    private static string ReadPage()
    {
        using var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream("Setup/setup.html")
            ?? throw new InvalidOperationException("The setup page isn't in the fake's resources.");
        using var reader = new StreamReader(stream);
        return reader.ReadToEnd();
    }
}
