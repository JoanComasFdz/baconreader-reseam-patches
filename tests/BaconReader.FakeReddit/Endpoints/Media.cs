// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using BaconReader.FakeReddit.World;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Http;

namespace BaconReader.FakeReddit.Endpoints;

/// <summary>
/// Media files, where "E2E fake Reddit" moves Reddit's media hosts: <c>/_media/i.redd.it/x.gif</c> for
/// <c>https://i.redd.it/x.gif</c>. A file the store doesn't hold is unhandled: a test sent a URL it didn't serve. A
/// preview.redd.it URL whose query still has its "&amp;" HTML-escaped is refused, 403, as Reddit refuses one.
/// </summary>
internal static class Media
{
    // BaconReader turns any link containing "v.redd.it" into one containing "www.reddit.com/video", by string replace.
    private const string RewrittenVideoHost = "www.reddit.com/video/";

    private const string SignedPreviewHost = "preview.redd.it/";

    public static void Map(WebApplication app, RedditWorld world) =>
        // HEAD too: "Improve post media loading" asks a GIF's size before it downloads it.
        app.MapMethods("/_media/{**path}", [HttpMethods.Get, HttpMethods.Head], (HttpContext context, string path) =>
        {
            if (path.StartsWith(SignedPreviewHost, StringComparison.Ordinal) && context.Request.QueryString.Value?.Contains("amp;", StringComparison.Ordinal) == true)
            {
                // Reddit signs preview URLs (s=…) over their query: one whose "&" are still HTML-escaped doesn't match
                // its signature, and Reddit refuses it.
                return Results.Text("Forbidden", statusCode: StatusCodes.Status403Forbidden);
            }

            var stored = path.StartsWith(RewrittenVideoHost, StringComparison.Ordinal) ? "v.redd.it/" + path[RewrittenVideoHost.Length..] : path;
            lock (world.Sync)
            {
                // A video's own address, v.redd.it/<id>, leads to its post, as v.redd.it redirects it.
                if (stored.StartsWith("v.redd.it/", StringComparison.Ordinal) && world.Posts.FirstOrDefault(post => post.VideoId is { } id && stored.TrimEnd('/') == "v.redd.it/" + id) is { } post)
                {
                    return Results.Redirect("https://www.reddit.com" + post.Permalink, permanent: true);
                }

                if (world.FindMedia(stored) is { } file)
                {
                    return Results.File(file.Bytes, file.ContentType, enableRangeProcessing: true);
                }
            }

            context.Items[FakeRedditServer.Unhandled] = true;
            return Results.NotFound();
        });
}
