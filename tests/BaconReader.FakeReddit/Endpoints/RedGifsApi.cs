// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using BaconReader.FakeReddit.Json;
using BaconReader.FakeReddit.World;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Http;

namespace BaconReader.FakeReddit.Endpoints;

/// <summary>
/// RedGIFs' API, <c>https://api.redgifs.com/</c>, where "E2E fake Reddit" moves it: <c>/_redgifs/…</c>. A temporary
/// token is RedGIFs' answer of 2026-11-03 (<c>token</c>, <c>addr</c>, <c>agent</c>, <c>session</c>, <c>rtfm</c>), and it
/// works only with the User-Agent it was given to: another one is refused, <c>401 WrongSender</c>, as RedGIFs refused
/// one used from another address. A video RedGIFs never had is <c>404 GifNotFound</c>, as RedGIFs answers it.
/// </summary>
internal static class RedGifsApi
{
    public static void Map(WebApplication app, RedditWorld world)
    {
        app.MapGet("/_redgifs/v2/auth/temporary", (HttpContext context) =>
        {
            var userAgent = context.Request.Headers.UserAgent.ToString();
            lock (world.Sync)
            {
                var token = world.RedGifs.IssueToken(userAgent);
                return Answers.Json(new
                {
                    token,
                    addr = "127.0.0.1",
                    agent = userAgent,
                    session = "e2e",
                    rtfm = "https://github.com/Redgifs/api/wiki/Temporary-tokens",
                });
            }
        });

        app.MapGet("/_redgifs/v2/gifs/{id}", (HttpContext context, string id) =>
        {
            var userAgent = context.Request.Headers.UserAgent.ToString();
            var authorization = context.Request.Headers.Authorization.ToString();
            var token = authorization.StartsWith("Bearer ", StringComparison.Ordinal) ? authorization["Bearer ".Length..] : null;
            lock (world.Sync)
            {
                var (status, answer) = token is null || world.RedGifs.UserAgentOf(token) is not { } givenTo
                    // Unconfirmed: what RedGIFs answers a request without a token it gave (the fake couldn't ask
                    // without one from this cloud's changing address).
                    ? (401, Error("TokenRequired", "A temporary token is required.", 401))
                    : givenTo != userAgent
                        ? (401, Error("WrongSender", "This token belongs to a different device, please request a new one.", 401))
                        : world.RedGifs.FindGif(id) is not { } gif
                            ? (404, Error("GifNotFound", "Gif not found.", null))
                            : gif.State == RedGifState.Deleted
                                // Unconfirmed: "Improve post media loading" takes 410 for a deleted video.
                                ? (410, Error("GifDeleted", "Gif was deleted.", null))
                                : (200, Gif(gif));
                world.RedGifs.RecordLookup(id, token, userAgent, status);
                return Results.Text(RedditJson.Serialize(answer), "application/json; charset=utf-8", statusCode: status);
            }
        });
    }

    // RedGIFs' error: {"error": {"code": …, "description": …}}, with its status when it is 401.
    private static Dictionary<string, object?> Error(string code, string description, int? status)
    {
        var error = new Dictionary<string, object?> { ["code"] = code, [status is null ? "description" : "message"] = description };
        if (status is not null)
        {
            error["status"] = status;
        }

        return new() { ["error"] = error };
    }

    // A video's information. BaconReader reads only gif.urls (hd to play, thumbnail or poster for the list); the other
    // fields are those RedGIFs' API documents, unconfirmed against a live answer.
    private static Dictionary<string, object?> Gif(RedGif gif) => new()
    {
        ["gif"] = new Dictionary<string, object?>
        {
            ["id"] = gif.Id,
            ["createDate"] = 1762128000,
            ["hasAudio"] = true,
            ["width"] = MediaAsset.Mp4.Width,
            ["height"] = MediaAsset.Mp4.Height,
            ["likes"] = 0,
            ["tags"] = Array.Empty<string>(),
            ["verified"] = false,
            ["views"] = 0,
            ["duration"] = 2.0,
            ["published"] = true,
            ["type"] = 1,
            ["urls"] = new Dictionary<string, object?>
            {
                ["sd"] = gif.Hd,
                ["hd"] = gif.Hd,
                ["poster"] = gif.Poster,
                ["thumbnail"] = gif.Thumbnail,
                ["vthumbnail"] = gif.Thumbnail,
            },
            ["userName"] = "e2e_creator",
            ["avgColor"] = "#000000",
            ["gallery"] = null,
            ["hideHome"] = false,
            ["hideTrending"] = false,
            ["sexuality"] = Array.Empty<string>(),
            ["niches"] = Array.Empty<string>(),
        },
        ["user"] = null,
    };
}
