// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using OpenQA.Selenium.Appium.Android;

namespace BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;

/// <summary>
/// The reply screen (PostActivity), "Post Comment", which "Comment" in a comment's action bar opens: "Reply to
/// &lt;author&gt; in r/&lt;subreddit&gt; as &lt;you&gt;" over the comment being answered, then the text box.
/// </summary>
public sealed class ReplyScreen(AndroidDriver driver)
{
    private static readonly OpenQA.Selenium.By ReplyTo = Locate.AppId("ReplyCommentSubText");

    /// <summary>Waits until the screen shows whom the reply answers.</summary>
    public Task WaitUntilShownAsync() =>
        Waiting.UntilShownAsync(driver, ReplyTo, TimeSpan.FromSeconds(10), "The reply screen, \"Post Comment\"");

    /// <summary>What the screen shows over the text box: "Reply to … in r/… as …".</summary>
    public Task<ReplyScreenSnapshot> ReadAsync() =>
        OnScreen.ReadAsync(driver, () => new ReplyScreenSnapshot(driver.FindElement(ReplyTo).Text));
}

/// <summary>What the reply screen shows over its text box: "Reply to ￼ avatar_user in r/e2e as e2e_user".</summary>
public sealed record ReplyScreenSnapshot(string ReplyTo);
