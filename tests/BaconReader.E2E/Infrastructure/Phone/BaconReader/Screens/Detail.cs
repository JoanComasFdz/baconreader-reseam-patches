// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using System.Collections.Immutable;
using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;

/// <summary>The screen a post opens on (DetailActivity): the post, then its comments.</summary>
public sealed class Detail(AndroidDriver driver)
{
    /// <summary>The post's comments, under the post and the sort bar.</summary>
    public Comments Comments { get; } = new(driver);
}

/// <summary>
/// The comments of the post shown, each its text and, with "Embed comment images", the GIFs and images under it (views
/// the patch makes, which it names <c>baconreader_patches_comment_gif</c> and <c>baconreader_patches_comment_image</c>).
/// </summary>
public sealed class Comments(AndroidDriver driver)
{
    private static readonly OpenQA.Selenium.By Comment = Locate.AppId("commentBodyContainer");
    private static readonly OpenQA.Selenium.By Header = Locate.AppId("commentSubText");
    private static readonly OpenQA.Selenium.By Text = Locate.AppId("commentBody");
    private static readonly OpenQA.Selenium.By Gif = Locate.AppId("baconreader_patches_comment_gif");
    private static readonly OpenQA.Selenium.By Image = Locate.AppId("baconreader_patches_comment_image");

    /// <summary>
    /// Waits until a comment shows (Reddit takes a moment to answer): its header, the author and score, which every
    /// comment shows; its text doesn't show when it was only an image.
    /// </summary>
    public Task WaitUntilShownAsync() =>
        Waiting.UntilShownAsync(driver, Header, TimeSpan.FromSeconds(30), "A comment of the post");

    /// <summary>Waits until a GIF or an image under a comment has loaded: its view is empty, and so hidden, until then.</summary>
    public Task WaitUntilMediaShownAsync() =>
        Waiting.UntilAsync(
            () => driver.FindElements(Gif).Concat(driver.FindElements(Image)).Any(media => media.Displayed && media.Size.Height > 0),
            TimeSpan.FromSeconds(30),
            "A GIF or an image under a comment loading");

    /// <summary>What the comments show, top to bottom: each one's text, and how many GIFs and images it shows.</summary>
    public Task<CommentsSnapshot> ReadAsync() =>
        OnScreen.ReadAsync(driver, () => new CommentsSnapshot(
            [.. driver.FindElements(Comment).Select(comment => new CommentSnapshot(
                comment.FindElements(Text).FirstOrDefault()?.Text.Trim() ?? "",
                comment.FindElements(Gif).Count(gif => gif.Displayed && gif.Size.Height > 0),
                comment.FindElements(Image).Count(image => image.Displayed && image.Size.Height > 0)))]));
}

/// <summary>What the comments on the screen show, top to bottom.</summary>
public sealed record CommentsSnapshot(ImmutableArray<CommentSnapshot> Comments);

/// <summary>What a comment shows: its text, and the GIFs and images under it.</summary>
public sealed record CommentSnapshot(string Text, int Gifs, int Images);
