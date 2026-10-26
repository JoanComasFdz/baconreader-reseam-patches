// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using BaconReader.E2E.Infrastructure;
using BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;
using BaconReader.E2E.Infrastructure.Vocabulary;
using JoanComasFdz.AssertingThat;

namespace BaconReader.E2E.Scenarios.E2E;

/// <summary>
/// "E2E fake Reddit" (test only), which BaseE2E patches every scenario's app with: BaconReader talks to the fake Reddit,
/// and, once logged in (Reddit refuses a logged-out app), shows what a scenario's Given made there.
/// </summary>
public sealed class E2EFakeRedditTests : BaseE2E
{
    [Theory, PlayOnPhone]
    [PatchedWith(Patch.SpoofClient)]
    [TestDescription("""
        @phone
        Scenario: BaconReader logged in shows the posts Reddit has in All
          Given Reddit has a post "First fake post" in r/e2e
          And Reddit has a post "Second fake post" in r/e2e
          And BaconReader is patched with "Spoof client"
          And BaconReader is installed
          When I open BaconReader
          And I tap "GOT IT :(" on "FINAL RELEASE ANNOUNCEMENT!"
          And I open the left menu
          And I tap "Add account"
          And I type the test account's user name in "Email or username"
          And I type the test account's password in "Password"
          And I tap "Log In"
          And I tap "Accept"
          And I select "All" in the toolbar's feed list
          Then the toolbar shows the "All" feed
          And the feed shows the posts "First fake post" and "Second fake post"
        """)]
    public async Task BaconReaderLoggedInShowsThePostsRedditHasInAll(Screen screen)
    {
        // Given
        Reddit.HasAPost("First fake post", "e2e");
        Reddit.HasAPost("Second fake post", "e2e");

        // When
        await Phone.Launcher.OpenBaconReaderAsync();
        await Phone.BaconReader.FinalReleaseAnnouncement.WaitUntilShownAsync();
        await Phone.BaconReader.FinalReleaseAnnouncement.ClickGotItAsync();
        await Phone.BaconReader.Main.WaitUntilShownAsync();
        await Phone.BaconReader.Main.LeftMenu.OpenAsync();
        await Phone.BaconReader.Main.LeftMenu.WaitUntilShownAsync();
        await Phone.BaconReader.Main.LeftMenu.ClickAsync(new MenuEntry.AddAccount());
        await Phone.BaconReader.LogIn.WaitUntilShownAsync();
        await Phone.BaconReader.LogIn.TypeTheTestAccountsUserNameAsync();
        await Phone.BaconReader.LogIn.TypeTheTestAccountsPasswordAsync();
        await Phone.BaconReader.LogIn.ClickLogInAsync();
        await Phone.BaconReader.LogIn.WaitUntilConnectShownAsync();
        await Phone.BaconReader.LogIn.ClickAcceptAsync();
        await Phone.BaconReader.Main.WaitUntilShownAsync();
        await Phone.BaconReader.Main.Toolbar.SelectFeedAsync(new Feed.All());
        await Phone.BaconReader.Main.Posts.WaitUntilPostsShownAsync();

        // Then
        var toolbar = await Phone.BaconReader.Main.Toolbar.ReadAsync();
        var posts = await Phone.BaconReader.Main.Posts.ReadAsync();
        Asserting.That(toolbar).ShowsFeed(new Feed.All());
        Asserting.That(posts).ShowsThePosts("First fake post", "Second fake post");
    }
}
