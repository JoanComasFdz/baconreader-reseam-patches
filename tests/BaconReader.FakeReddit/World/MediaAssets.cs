// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

namespace BaconReader.FakeReddit.World;

/// <summary>A small media file the fake ships, to put in posts and comments.</summary>
/// <param name="Extension">The file extension, without the dot.</param>
public sealed record MediaAsset(string ContentType, string Extension, int Width, int Height, byte[] Bytes)
{
    /// <summary>An animated GIF, 240×180, two seconds.</summary>
    public static MediaAsset Gif { get; } = Load("e2e.gif", "image/gif", 240, 180);

    /// <summary>A JPEG photo, 320×240.</summary>
    public static MediaAsset Jpeg { get; } = Load("e2e.jpg", "image/jpeg", 320, 240);

    /// <summary>A PNG image, 320×240.</summary>
    public static MediaAsset Png { get; } = Load("e2e.png", "image/png", 320, 240);

    /// <summary>An MP4 video with an audio track, 320×240, two seconds.</summary>
    public static MediaAsset Mp4 { get; } = Load("e2e.mp4", "video/mp4", 320, 240);

    /// <summary>An MP4 video with an audio track, 320×240, ten seconds: long enough to watch it play.</summary>
    public static MediaAsset LongMp4 { get; } = Load("e2e-video.mp4", "video/mp4", 320, 240);

    /// <summary>
    /// A video as Reddit serves one uploaded to v.redd.it, ten seconds, 320×240, its sound in a track of its own: this
    /// DASH playlist, whose video rendition is <see cref="DashVideo"/>, at <c>DASH_240.mp4</c> next to it, and whose audio
    /// rendition is <see cref="DashAudio"/>, at <c>DASH_AUDIO_128.mp4</c>.
    /// </summary>
    public static MediaAsset DashPlaylist { get; } = Load("e2e-dash.mpd", "application/dash+xml", 320, 240);

    /// <summary>The rendition <see cref="DashPlaylist"/> names, <c>DASH_240.mp4</c>: a fragmented MP4.</summary>
    public static MediaAsset DashVideo { get; } = Load("e2e-dash.mp4", "video/mp4", 320, 240);

    /// <summary>The audio rendition <see cref="DashPlaylist"/> names, <c>DASH_AUDIO_128.mp4</c>: a 440 Hz tone, AAC, in a fragmented MP4.</summary>
    public static MediaAsset DashAudio { get; } = Load("e2e-dash-audio.mp4", "audio/mp4", 0, 0);

    /// <summary>A web page whose heading says "JavaScript is off", and "JavaScript is on" once its script runs.</summary>
    public static MediaAsset WebPage { get; } = Load("e2e-page.html", "text/html", 0, 0);

    private static MediaAsset Load(string name, string contentType, int width, int height)
    {
        using var stream = typeof(MediaAsset).Assembly.GetManifestResourceStream("Media/" + name)
            ?? throw new InvalidOperationException($"The fake's asset {name} isn't embedded.");
        using var bytes = new MemoryStream();
        stream.CopyTo(bytes);
        return new MediaAsset(contentType, Path.GetExtension(name).TrimStart('.'), width, height, bytes.ToArray());
    }
}
