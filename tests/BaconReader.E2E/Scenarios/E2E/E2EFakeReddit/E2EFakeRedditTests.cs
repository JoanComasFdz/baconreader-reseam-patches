// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using BaconReader.E2E.Infrastructure;
using BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;
using BaconReader.E2E.Infrastructure.Vocabulary;
using JoanComasFdz.AssertingThat;

namespace BaconReader.E2E.Scenarios.E2E;

/// <summary>"E2E fake Reddit" (test only): BaconReader talks to a fake Reddit server instead of Reddit.</summary>
public sealed class E2EFakeRedditTests : BaseE2E
{
    [Theory, PlayOnPhone]
    [PatchedWith(Patch.E2EFakeReddit)]
    [TestDescription("""
        @phone
        Scenario: BaconReader patched with E2E fake Reddit opens on the front page
          Given BaconReader is patched with "E2E fake Reddit" and installed
          When I open BaconReader
          And I tap "GOT IT :(" on "FINAL RELEASE ANNOUNCEMENT!"
          Then the toolbar shows the "Front Page" feed
        """)]
    public async Task BaconReaderPatchedWithE2EFakeRedditOpensOnTheFrontPage(Screen screen)
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
