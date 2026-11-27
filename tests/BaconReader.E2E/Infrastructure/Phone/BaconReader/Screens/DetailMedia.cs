// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using System.Drawing;
using BaconReader.E2E.Infrastructure.Vocabulary;
using OpenQA.Selenium;
using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;

/// <summary>
/// A post's media in its detail view, under its title, which BaconReader shows only with "Show large image" on: an
/// image or a GIF in the media's view (<c>viewFlipper</c>); a video, playing, in its player (<c>videoView</c>, described
/// "Show player controls"); or, with "Video autoplay in detail view" off, the post's preview with a play button,
/// described "Play video". With "Full screen post videos", the player shows "Full screen" in its top right corner while
/// its controls show: when the video starts, for a few seconds, and while it's paused. A tap on the video pauses or
/// plays it (BaconReader's own listener), and the controls show while it's paused.
/// </summary>
public sealed class DetailMedia(AndroidDriver driver)
{
    private static readonly By Media = Locate.AppId("viewFlipper");
    private static readonly By Video = Locate.AppId("videoView");
    private static readonly By PlayVideo = Locate.Description("Play video");
    private static readonly By PlayPause = Locate.AppId("btnPlayPause");
    private static readonly By Window = Locate.AndroidId("content");

    /// <summary>Waits until the post's video plays in its player.</summary>
    public Task WaitUntilVideoPlaysAsync() =>
        Waiting.UntilShownAsync(driver, Video, TimeSpan.FromSeconds(15), "The post's video playing");

    /// <summary>Waits until the post shows its preview with "Play video".</summary>
    public Task WaitUntilPlayVideoShownAsync() =>
        Waiting.UntilShownAsync(driver, PlayVideo, TimeSpan.FromSeconds(10), "\"Play video\" over the post's preview");

    /// <summary>Waits until the post shows its media: an image or a GIF.</summary>
    public Task WaitUntilShownAsync() =>
        Waiting.UntilShownAsync(driver, Media, TimeSpan.FromSeconds(10), "The post's media");

    /// <summary>Taps "Play video", which plays the video in its place.</summary>
    public Task ClickPlayVideoAsync() => OnScreen.PressAsync(driver, PlayVideo);

    /// <summary>
    /// Taps <paramref name="button"/> over the post's video, found by what it says to a screen reader. When the
    /// controls have hidden since the video started, a tap on the video pauses it and shows them, with the button.
    /// </summary>
    public async Task ClickVideoButtonAsync(VideoButton button)
    {
        var target = Locate.Description(button.ToShownText());
        if (await Waiting.FindWithinAsync(driver, target, TimeSpan.FromSeconds(2), $"\"{button.ToShownText()}\" over the post's video") is null)
        {
            await OnScreen.PressAsync(driver, Video);
            await Waiting.UntilShownAsync(driver, target, TimeSpan.FromSeconds(5), $"\"{button.ToShownText()}\" over the post's video");
        }
        await OnScreen.PressAsync(driver, target);
    }

    /// <summary>Waits until the player's controls show over the post's video, as they do when it starts.</summary>
    public Task WaitUntilControlsShownAsync() =>
        Waiting.UntilShownAsync(driver, PlayPause, TimeSpan.FromSeconds(10), "The controls of the post's video");

    /// <summary>Waits until the screen is upright again, higher than wide.</summary>
    public Task WaitUntilUprightAsync() =>
        Waiting.UntilAsync(
            () => driver.FindElements(Window).FirstOrDefault() is { } window && window.Size.Height > window.Size.Width,
            TimeSpan.FromSeconds(10),
            "The screen upright");

    /// <summary>What the post's media shows: a video playing, "Play video" over the preview, or neither (an image, a GIF).</summary>
    public Task<DetailMediaSnapshot> ReadAsync() =>
        OnScreen.ReadAsync(driver, () => new DetailMediaSnapshot(
            Shown(Media) || Shown(Video) || Shown(PlayVideo),
            Shown(Video),
            Shown(PlayVideo),
            Shown(PlayPause),
            Shown(Locate.Description(new VideoButton.FullScreen().ToShownText()))));

    /// <summary>The window the post is shown in, in pixels.</summary>
    public Task<DetailMediaLayoutSnapshot> ReadLayoutAsync() =>
        OnScreen.ReadAsync(driver, () =>
        {
            var window = driver.FindElement(Window);
            return new DetailMediaLayoutSnapshot(new Rectangle(window.Location, window.Size));
        });

    private bool Shown(By view) => driver.FindElements(view).Any(found => found.Displayed);
}

/// <summary>
/// What a post's media shows in its detail view: whether it shows at all, a video playing in its player, "Play video"
/// over its preview, the player's controls, and "Full screen" over the video.
/// </summary>
public sealed record DetailMediaSnapshot(bool Shown, bool PlaysAVideo, bool ShowsPlayVideo, bool ShowsControls, bool ShowsFullScreen);

/// <summary>The window a post's detail view is shown in, in pixels.</summary>
public sealed record DetailMediaLayoutSnapshot(Rectangle Window);
