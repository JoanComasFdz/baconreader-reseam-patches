// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using BaconReader.E2E.Infrastructure;
using BaconReader.E2E.Infrastructure.Phone;
using BaconReader.E2E.Infrastructure.Phone.BaconReader.Dialogs;
using BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;
using BaconReader.E2E.Infrastructure.Vocabulary;
using JoanComasFdz.AssertingThat;

namespace BaconReader.E2E.Scenarios.Startup.Links;

/// <summary>
/// "Prompt to open Reddit links": BaconReader asks once whether to open Android's "Open by default" screen, where the user
/// selects the Reddit links to open in it, since Android doesn't let it select them itself.
/// </summary>
public sealed class PromptToOpenRedditLinksTests : BaseE2E
{
    [Theory, PlayOnPhone]
    [PatchedWith(Patch.PromptToOpenRedditLinks)]
    [TestDescription("""
        @phone
        Scenario: Prompt to open Reddit links asks once until it is answered or Reddit links open in BaconReader
          Given BaconReader is patched with "Prompt to open Reddit links"
          And BaconReader is installed
          When I open BaconReader
          And I tap "GOT IT :(" on "FINAL RELEASE ANNOUNCEMENT!"
          # The prompt waits until the announcement is closed.
          Then BaconReader asks "Open Reddit links with BaconReader?"
          And it offers "Not now" and "Set up"
          When I tap "Not now" on "Open Reddit links with BaconReader?"
          Then the toolbar shows the "Front Page" feed
          When I force stop BaconReader
          And I open BaconReader
          Then the toolbar shows the "Front Page" feed
          And "Open Reddit links with BaconReader?" doesn't show
          # As Android's App info → Storage & cache → Clear storage does: BaconReader starts as just installed.
          When I clear BaconReader's storage
          And I open BaconReader
          And I tap "GOT IT :(" on "FINAL RELEASE ANNOUNCEMENT!"
          Then BaconReader asks "Open Reddit links with BaconReader?"
          When I go back
          Then the toolbar shows the "Front Page" feed
          When I force stop BaconReader
          And I open BaconReader
          # Back answers the prompt too.
          Then the toolbar shows the "Front Page" feed
          And "Open Reddit links with BaconReader?" doesn't show
          When I clear BaconReader's storage
          And I open BaconReader
          And I tap "GOT IT :(" on "FINAL RELEASE ANNOUNCEMENT!"
          And I tap "Set up" on "Open Reddit links with BaconReader?"
          Then Android shows "Open by default" for "BaconReader Premium"
          # reddit.com verifies only Reddit's own app: "0 verified links".
          And "Links to open in this app" lists no link
          When I go back
          Then the toolbar shows the "Front Page" feed
          When I force stop BaconReader
          And I open BaconReader
          # "Set up" answers the prompt, even with no link selected.
          Then the toolbar shows the "Front Page" feed
          And "Open Reddit links with BaconReader?" doesn't show
          When I clear BaconReader's storage
          And I open BaconReader
          And I tap "GOT IT :(" on "FINAL RELEASE ANNOUNCEMENT!"
          And I tap "Set up" on "Open Reddit links with BaconReader?"
          And I tap "Add link"
          And I tick "reddit.com"
          And I tap "Add"
          Then "Links to open in this app" lists "reddit.com"
          When I go back
          And I clear BaconReader's storage
          And I open BaconReader
          And I tap "GOT IT :(" on "FINAL RELEASE ANNOUNCEMENT!"
          # Android keeps the links selected when BaconReader's storage is cleared: the prompt is unanswered again, but has nothing to ask.
          Then the toolbar shows the "Front Page" feed
          And "Open Reddit links with BaconReader?" doesn't show
        """)]
    public async Task PromptToOpenRedditLinksAsksOnceUntilItIsAnsweredOrRedditLinksOpenInBaconReader(Screen screen)
    {
        // Given

        // When
        await Phone.Launcher.OpenBaconReaderAsync();
        await Phone.BaconReader.FinalReleaseAnnouncement.WaitUntilShownAsync();
        await Phone.BaconReader.FinalReleaseAnnouncement.ClickGotItAsync();
        await Phone.BaconReader.OpenRedditLinksPrompt.WaitUntilShownAsync();

        // Then
        // The prompt waits until the announcement is closed.
        var prompt = await Phone.BaconReader.OpenRedditLinksPrompt.ReadAsync();
        Asserting.That(prompt).IsShown().OffersNotNowAndSetUp();

        // When
        await Phone.BaconReader.OpenRedditLinksPrompt.ClickNotNowAsync();
        await Phone.BaconReader.Main.WaitUntilShownAsync();

        // Then
        var afterNotNow = await Phone.BaconReader.Main.Toolbar.ReadAsync();
        Asserting.That(afterNotNow).ShowsFeed(new Feed.FrontPage());

        // When
        await Phone.AppInfo.ForceStopBaconReaderAsync();
        await Phone.Launcher.OpenBaconReaderAsync();
        await Phone.BaconReader.Main.WaitUntilShownAsync();
        await Phone.BaconReader.OpenRedditLinksPrompt.WaitUntilNotShownAsync();

        // Then
        var reopenedAfterNotNow = await Phone.BaconReader.Main.Toolbar.ReadAsync();
        var promptAfterNotNow = await Phone.BaconReader.OpenRedditLinksPrompt.ReadAsync();
        Asserting.That(reopenedAfterNotNow).ShowsFeed(new Feed.FrontPage());
        Asserting.That(promptAfterNotNow).IsNotShown();

        // When
        await Phone.AppInfo.ClearBaconReadersStorageAsync();
        await Phone.Launcher.OpenBaconReaderAsync();
        await Phone.BaconReader.FinalReleaseAnnouncement.WaitUntilShownAsync();
        await Phone.BaconReader.FinalReleaseAnnouncement.ClickGotItAsync();
        await Phone.BaconReader.OpenRedditLinksPrompt.WaitUntilShownAsync();

        // Then
        var promptAfterClearing = await Phone.BaconReader.OpenRedditLinksPrompt.ReadAsync();
        Asserting.That(promptAfterClearing).IsShown();

        // When
        await Phone.GoBackAsync();
        await Phone.BaconReader.Main.WaitUntilShownAsync();

        // Then
        var afterBack = await Phone.BaconReader.Main.Toolbar.ReadAsync();
        Asserting.That(afterBack).ShowsFeed(new Feed.FrontPage());

        // When
        await Phone.AppInfo.ForceStopBaconReaderAsync();
        await Phone.Launcher.OpenBaconReaderAsync();
        await Phone.BaconReader.Main.WaitUntilShownAsync();
        await Phone.BaconReader.OpenRedditLinksPrompt.WaitUntilNotShownAsync();

        // Then
        // Back answers the prompt too.
        var reopenedAfterBack = await Phone.BaconReader.Main.Toolbar.ReadAsync();
        var promptAfterBack = await Phone.BaconReader.OpenRedditLinksPrompt.ReadAsync();
        Asserting.That(reopenedAfterBack).ShowsFeed(new Feed.FrontPage());
        Asserting.That(promptAfterBack).IsNotShown();

        // When
        await Phone.AppInfo.ClearBaconReadersStorageAsync();
        await Phone.Launcher.OpenBaconReaderAsync();
        await Phone.BaconReader.FinalReleaseAnnouncement.WaitUntilShownAsync();
        await Phone.BaconReader.FinalReleaseAnnouncement.ClickGotItAsync();
        await Phone.BaconReader.OpenRedditLinksPrompt.WaitUntilShownAsync();
        await Phone.BaconReader.OpenRedditLinksPrompt.ClickSetUpAsync();
        await Phone.OpenByDefault.WaitUntilShownAsync();

        // Then
        // reddit.com verifies only Reddit's own app: "0 verified links".
        var openByDefault = await Phone.OpenByDefault.ReadAsync();
        Asserting.That(openByDefault).IsOpenByDefaultFor("BaconReader Premium").ListsNoLink();

        // When
        await Phone.GoBackAsync();
        await Phone.BaconReader.Main.WaitUntilShownAsync();

        // Then
        var afterSetUp = await Phone.BaconReader.Main.Toolbar.ReadAsync();
        Asserting.That(afterSetUp).ShowsFeed(new Feed.FrontPage());

        // When
        await Phone.AppInfo.ForceStopBaconReaderAsync();
        await Phone.Launcher.OpenBaconReaderAsync();
        await Phone.BaconReader.Main.WaitUntilShownAsync();
        await Phone.BaconReader.OpenRedditLinksPrompt.WaitUntilNotShownAsync();

        // Then
        // "Set up" answers the prompt, even with no link selected.
        var reopenedAfterSetUp = await Phone.BaconReader.Main.Toolbar.ReadAsync();
        var promptAfterSetUp = await Phone.BaconReader.OpenRedditLinksPrompt.ReadAsync();
        Asserting.That(reopenedAfterSetUp).ShowsFeed(new Feed.FrontPage());
        Asserting.That(promptAfterSetUp).IsNotShown();

        // When
        await Phone.AppInfo.ClearBaconReadersStorageAsync();
        await Phone.Launcher.OpenBaconReaderAsync();
        await Phone.BaconReader.FinalReleaseAnnouncement.WaitUntilShownAsync();
        await Phone.BaconReader.FinalReleaseAnnouncement.ClickGotItAsync();
        await Phone.BaconReader.OpenRedditLinksPrompt.WaitUntilShownAsync();
        await Phone.BaconReader.OpenRedditLinksPrompt.ClickSetUpAsync();
        await Phone.OpenByDefault.WaitUntilShownAsync();
        await Phone.OpenByDefault.ClickAddLinkAsync();
        await Phone.OpenByDefault.SupportedLinks.WaitUntilShownAsync();
        await Phone.OpenByDefault.SupportedLinks.TickAsync(new AppLink.RedditCom());
        await Phone.OpenByDefault.SupportedLinks.ClickAddAsync();
        await Phone.OpenByDefault.WaitUntilShownAsync();

        // Then
        var selected = await Phone.OpenByDefault.ReadAsync();
        Asserting.That(selected).ListsLink(new AppLink.RedditCom());

        // When
        await Phone.GoBackAsync();
        await Phone.BaconReader.Main.WaitUntilShownAsync();
        await Phone.AppInfo.ClearBaconReadersStorageAsync();
        await Phone.Launcher.OpenBaconReaderAsync();
        await Phone.BaconReader.FinalReleaseAnnouncement.WaitUntilShownAsync();
        await Phone.BaconReader.FinalReleaseAnnouncement.ClickGotItAsync();
        await Phone.BaconReader.Main.WaitUntilShownAsync();
        await Phone.BaconReader.OpenRedditLinksPrompt.WaitUntilNotShownAsync();

        // Then
        // Android keeps the links selected when BaconReader's storage is cleared: the prompt is unanswered again, but has nothing to ask.
        var withRedditLinks = await Phone.BaconReader.Main.Toolbar.ReadAsync();
        var promptWithRedditLinks = await Phone.BaconReader.OpenRedditLinksPrompt.ReadAsync();
        Asserting.That(withRedditLinks).ShowsFeed(new Feed.FrontPage());
        Asserting.That(promptWithRedditLinks).IsNotShown();
    }
}
