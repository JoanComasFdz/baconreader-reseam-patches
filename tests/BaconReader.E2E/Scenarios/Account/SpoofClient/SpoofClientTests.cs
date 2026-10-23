// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using BaconReader.E2E.Infrastructure;
using BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;
using BaconReader.E2E.Infrastructure.Vocabulary;
using JoanComasFdz.AssertingThat;

namespace BaconReader.E2E.Scenarios.Account;

/// <summary>"Spoof client": BaconReader talks to Reddit as another app (RedReader by default), so logging in works again.</summary>
public sealed class SpoofClientTests : BaseE2E
{
    [Theory, PlayOnPhone]
    [PatchedWith(Patch.SpoofClient)]
    [TestDescription("""
        @phone
        Scenario: BaconReader patched with Spoof client opens on the front page
          Given BaconReader is patched with "Spoof client" and installed
          When I open BaconReader
          And I tap "GOT IT :(" on "FINAL RELEASE ANNOUNCEMENT!"
          Then the toolbar shows the "Front Page" feed
        """)]
    public async Task BaconReaderPatchedWithSpoofClientOpensOnTheFrontPage(Screen screen)
    {
        // Given

        // When
        await Phone.Launcher.OpenBaconReaderAsync();
        await Phone.BaconReader.FinalReleaseAnnouncement.WaitUntilShownAsync();
        await Phone.BaconReader.FinalReleaseAnnouncement.ClickGotItAsync();
        await Phone.BaconReader.Main.WaitUntilShownAsync();

        // Then
        var toolbar = await Phone.BaconReader.Main.Toolbar.ReadAsync();
        Asserting.That(toolbar).ShowsFeed(new Feed.FrontPage());
    }
}
