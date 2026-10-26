// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using BaconReader.E2E.Infrastructure;
using BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;
using BaconReader.E2E.Infrastructure.Vocabulary;
using JoanComasFdz.AssertingThat;

namespace BaconReader.E2E.Scenarios.E2E;

/// <summary>
/// "E2E fake Reddit" (test only), which BaseE2E patches every scenario's app with: BaconReader talks to the fake Reddit,
/// and shows what a scenario's Given made there.
/// </summary>
public sealed class E2EFakeRedditTests : BaseE2E
{
    [Theory, PlayOnPhone]
    [PatchedWith]
    [TestDescription("""
        @phone
        Scenario: BaconReader logged out shows the posts Reddit has in All
          Given Reddit has a post "First fake post" in r/e2e
          And Reddit has a post "Second fake post" in r/e2e
          And BaconReader is installed
          When I open BaconReader
          And I tap "GOT IT :(" on "FINAL RELEASE ANNOUNCEMENT!"
          And I select "All" in the toolbar's feed list
          Then the toolbar shows the "All" feed
          And the feed shows the posts "First fake post" and "Second fake post"
        """)]
    public async Task BaconReaderLoggedOutShowsThePostsRedditHasInAll(Screen screen)
    {
        // Given
        Reddit.HasAPost("First fake post", "e2e");
        Reddit.HasAPost("Second fake post", "e2e");

        // When
        await Phone.Launcher.OpenBaconReaderAsync();
        await Phone.BaconReader.FinalReleaseAnnouncement.WaitUntilShownAsync();
        await Phone.BaconReader.FinalReleaseAnnouncement.ClickGotItAsync();
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
