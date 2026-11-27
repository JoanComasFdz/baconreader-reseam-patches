// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using BaconReader.E2E.Infrastructure;
using BaconReader.E2E.Infrastructure.Phone.BaconReader.Dialogs;
using BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;
using BaconReader.E2E.Infrastructure.Vocabulary;
using JoanComasFdz.AssertingThat;

namespace BaconReader.E2E.Scenarios.LoggedIn.Media;

/// <summary>
/// "Full screen post videos": while the controls of a post's video show, "Full screen" in its top right corner shows
/// it over the whole screen, turned sideways when it's wider than high; "Exit full screen" or Back brings it back to
/// the post. "Full screen button", on by default, turns the button off. BaconReader shows a post's media in its detail
/// view only with its own "Show large image" on.
/// </summary>
public sealed class FullScreenPostVideosTests : BaseE2E
{
    [Theory, PlayOnPhone]
    [PatchedWithEveryPatch]
    [TestDescription("""
        @phone
        Scenario: Full screen post videos shows a video post's video over the whole screen
          Given Reddit has a video post "Watch this" in r/e2e
          And BaconReader is patched with every patch
          And the test account is logged in
          And BaconReader is open
          When I select the "e2e" feed
          And I open the left menu
          And I tap "Settings"
          And I tap "Detail View"
          # BaconReader shows a post's media in its detail view only with "Show large image" on.
          And I tap "Show large image"
          And I go back to the posts
          And I open the left menu
          And I tap "Settings"
          And I tap "Patches"
          And I tap "Images and videos"
          Then the setting "Full screen button" is on
          When I go back to the posts
          And I tap the post "Watch this"
          Then the post plays its video
          When I tap "Full screen" on the post's video
          Then the video fills the screen
          # The video is wider than high: the full screen turns the phone sideways.
          And the screen is turned sideways
          When I tap "Exit full screen"
          Then the post plays its video
          And the screen is upright
          When I tap "Full screen" on the post's video
          And I tap Back
          Then the post plays its video
          And the screen is upright
          When I go back to the posts
          And I open the left menu
          And I tap "Settings"
          And I tap "Patches"
          And I tap "Images and videos"
          And I tap "Full screen button"
          And I go back to the posts
          And I tap the post "Watch this"
          Then the post plays its video
          And the post's video shows no "Full screen"
        """)]
    public async Task FullScreenPostVideosShowsAVideoPostsVideoOverTheWholeScreen(Screen screen)
    {
        // Given
        Reddit.HasAVideoPost("Watch this", "e2e");
        Reddit.TheTestAccountIsLoggedIn();
        await Phone.BaconReader.OpenAsync();

        // When
        await Phone.BaconReader.Main.Toolbar.SelectFeedAsync(new Feed.Subreddit("e2e"));
        await Phone.BaconReader.Main.Toolbar.WaitUntilFeedShownAsync(new Feed.Subreddit("e2e"));
        await Phone.BaconReader.Main.LeftMenu.OpenAsync();
        await Phone.BaconReader.Main.LeftMenu.WaitUntilShownAsync();
        await Phone.BaconReader.Main.LeftMenu.ClickAsync(new MenuEntry.Settings());
        await Phone.BaconReader.Settings.WaitUntilShownAsync(new SettingsSection.DetailView());
        await Phone.BaconReader.Settings.ClickAsync(new SettingsSection.DetailView());
        await Phone.BaconReader.Settings.WaitUntilSectionShownAsync(new SettingsSection.DetailView());
        await Phone.BaconReader.Settings.TickAsync(new BaconReaderSetting.ShowLargeImage());
        await Phone.BaconReader.GoBackToThePostsAsync();
        await Phone.BaconReader.Main.LeftMenu.OpenAsync();
        await Phone.BaconReader.Main.LeftMenu.WaitUntilShownAsync();
        await Phone.BaconReader.Main.LeftMenu.ClickAsync(new MenuEntry.Settings());
        await Phone.BaconReader.Settings.WaitUntilShownAsync(new SettingsSection.Patches());
        await Phone.BaconReader.Settings.ClickAsync(new SettingsSection.Patches());
        await Phone.BaconReader.PatchesSettings.WaitUntilShownAsync();
        await Phone.BaconReader.PatchesSettings.ClickAsync(new SettingsPage.ImagesAndVideos());
        await Phone.BaconReader.PatchesSettings.WaitUntilPageShownAsync(new SettingsPage.ImagesAndVideos());

        // Then
        var button = await Phone.BaconReader.PatchesSettings.ReadSwitchAsync(new PatchSetting.FullScreenButton());
        Asserting.That(button).IsOn();

        // When
        await Phone.BaconReader.GoBackToThePostsAsync();
        await Phone.BaconReader.Main.Posts.ClickPostAsync("Watch this");
        await Phone.BaconReader.Detail.WaitUntilShownAsync();
        await Phone.BaconReader.Detail.PostMedia.WaitUntilVideoPlaysAsync();

        // Then
        var playing = await Phone.BaconReader.Detail.PostMedia.ReadAsync();
        Asserting.That(playing).PlaysItsVideo();

        // When
        await Phone.BaconReader.Detail.PostMedia.ClickVideoButtonAsync(new VideoButton.FullScreen());
        await Phone.BaconReader.VideoFullScreen.WaitUntilShownAsync();

        // Then
        var fullScreen = await Phone.BaconReader.VideoFullScreen.ReadLayoutAsync();
        Asserting.That(fullScreen).FillsTheScreen();
        // The video is wider than high: the full screen turns the phone sideways.
        Asserting.That(fullScreen).IsSideways();

        // When
        await Phone.BaconReader.VideoFullScreen.ClickAsync(new VideoButton.ExitFullScreen());
        await Phone.BaconReader.VideoFullScreen.WaitUntilClosedAsync();
        await Phone.BaconReader.Detail.PostMedia.WaitUntilUprightAsync();

        // Then
        var afterExit = await Phone.BaconReader.Detail.PostMedia.ReadAsync();
        var afterExitLayout = await Phone.BaconReader.Detail.PostMedia.ReadLayoutAsync();
        Asserting.That(afterExit).PlaysItsVideo();
        Asserting.That(afterExitLayout).IsUpright();

        // When
        await Phone.BaconReader.Detail.PostMedia.ClickVideoButtonAsync(new VideoButton.FullScreen());
        await Phone.BaconReader.VideoFullScreen.WaitUntilShownAsync();
        await Phone.BaconReader.VideoFullScreen.ClickBackAsync();
        await Phone.BaconReader.VideoFullScreen.WaitUntilClosedAsync();
        await Phone.BaconReader.Detail.PostMedia.WaitUntilUprightAsync();

        // Then
        var afterBack = await Phone.BaconReader.Detail.PostMedia.ReadAsync();
        var afterBackLayout = await Phone.BaconReader.Detail.PostMedia.ReadLayoutAsync();
        Asserting.That(afterBack).PlaysItsVideo();
        Asserting.That(afterBackLayout).IsUpright();

        // When
        await Phone.BaconReader.GoBackToThePostsAsync();
        await Phone.BaconReader.Main.LeftMenu.OpenAsync();
        await Phone.BaconReader.Main.LeftMenu.WaitUntilShownAsync();
        await Phone.BaconReader.Main.LeftMenu.ClickAsync(new MenuEntry.Settings());
        await Phone.BaconReader.Settings.WaitUntilShownAsync(new SettingsSection.Patches());
        await Phone.BaconReader.Settings.ClickAsync(new SettingsSection.Patches());
        await Phone.BaconReader.PatchesSettings.WaitUntilShownAsync();
        await Phone.BaconReader.PatchesSettings.ClickAsync(new SettingsPage.ImagesAndVideos());
        await Phone.BaconReader.PatchesSettings.WaitUntilPageShownAsync(new SettingsPage.ImagesAndVideos());
        await Phone.BaconReader.PatchesSettings.TickAsync(new PatchSetting.FullScreenButton());
        await Phone.BaconReader.GoBackToThePostsAsync();
        await Phone.BaconReader.Main.Posts.ClickPostAsync("Watch this");
        await Phone.BaconReader.Detail.WaitUntilShownAsync();
        await Phone.BaconReader.Detail.PostMedia.WaitUntilVideoPlaysAsync();
        await Phone.BaconReader.Detail.PostMedia.WaitUntilControlsShownAsync();

        // Then
        var withoutButton = await Phone.BaconReader.Detail.PostMedia.ReadAsync();
        Asserting.That(withoutButton).PlaysItsVideo();
        Asserting.That(withoutButton).ShowsNoFullScreen();
    }
}
