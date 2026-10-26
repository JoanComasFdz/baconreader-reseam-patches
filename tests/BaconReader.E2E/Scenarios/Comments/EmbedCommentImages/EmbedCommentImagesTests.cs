// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using BaconReader.E2E.Infrastructure;
using BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;
using BaconReader.E2E.Infrastructure.Vocabulary;
using JoanComasFdz.AssertingThat;

namespace BaconReader.E2E.Scenarios.Comments;

/// <summary>"Embed comment images": BaconReader shows the images and GIFs in comments as images, under the text.</summary>
public sealed class EmbedCommentImagesTests : BaseE2E
{
    [Theory, PlayOnPhone]
    [PatchedWith(Patch.EmbedCommentImages, Patch.SpoofClient)]
    [TestDescription("""
        @phone
        Scenario: Embed comment images shows a GIF in a comment as an image
          Given Reddit has a post "Weekly thread" in r/e2e
          And the post "Weekly thread" has a comment with a GIF
          And BaconReader is patched with "Embed comment images"
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
          # BaconReader comes back from the login with the left menu open.
          And I close the left menu
          And I tap the post "Weekly thread"
          Then the comment shows 1 GIF
        """)]
    public async Task EmbedCommentImagesShowsAGIFInACommentAsAnImage(Screen screen)
    {
        // Given
        Reddit.HasAPost("Weekly thread", "e2e");
        Reddit.ThePost("Weekly thread").HasACommentWithAGif();

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
        // BaconReader comes back from the login with the left menu open.
        await Phone.BaconReader.Main.LeftMenu.WaitUntilShownAsync();
        await Phone.BaconReader.Main.LeftMenu.CloseAsync();
        await Phone.BaconReader.Main.Posts.ClickPostAsync("Weekly thread");
        await Phone.BaconReader.Detail.Comments.WaitUntilShownAsync();
        await Phone.BaconReader.Detail.Comments.WaitUntilMediaShownAsync();

        // Then
        var comments = await Phone.BaconReader.Detail.Comments.ReadAsync();
        Asserting.That(comments).TheCommentShowsGifs(1);
    }
}
