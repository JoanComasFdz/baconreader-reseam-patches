// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using System.Collections.Immutable;
using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;

/// <summary>The main screen's list of the feed's posts, under the toolbar and the sort bar.</summary>
public sealed class PostList(AndroidDriver driver)
{
    private static readonly OpenQA.Selenium.By List = Locate.AppId("rv_links");
    private static readonly OpenQA.Selenium.By Title = Locate.AppId("title");

    /// <summary>Waits until the list shows a post (Reddit takes a moment to answer).</summary>
    public Task WaitUntilPostsShownAsync() =>
        Waiting.UntilShownAsync(driver, Title, TimeSpan.FromSeconds(30), "A post in the main screen's list");

    /// <summary>Taps the post titled <paramref name="title"/>, which opens it with its comments.</summary>
    public async Task ClickPostAsync(string title)
    {
        var post = Locate.AppIdWithText("title", title);
        await Waiting.UntilShownAsync(driver, post, TimeSpan.FromSeconds(30), $"The post \"{title}\" in the main screen's list");
        await OnScreen.PressAsync(driver, post);
    }

    /// <summary>What the list shows: the titles of the posts on the screen, top to bottom.</summary>
    public Task<PostListSnapshot> ReadAsync() =>
        OnScreen.ReadAsync(driver, () => new PostListSnapshot(
            [.. driver.FindElement(List).FindElements(Title).Select(title => title.Text.Trim())]));
}

/// <summary>What the list of posts shows: the titles of the posts on the screen, top to bottom.</summary>
public sealed record PostListSnapshot(ImmutableArray<string> Titles);
