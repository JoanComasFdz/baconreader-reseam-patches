// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using BaconReader.E2E.Infrastructure;
using BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;
using BaconReader.E2E.Infrastructure.Phone.ReseamManager.Screens;
using BaconReader.E2E.Infrastructure.Vocabulary;
using JoanComasFdz.AssertingThat;

namespace BaconReader.E2E.Scenarios.Manager.Patching;

/// <summary>
/// How a user patches BaconReader the first time, with Reseam Manager, as the README's "Install" says: the repository's
/// "➕ Add to Reseam Manager" opens Reseam Manager, which adds the patch source at its newest release, then patches the
/// user's APK, picked with "Pick an APK", and installs it once Android lets it install apps. The user guide starts with
/// its recording.
/// </summary>
public sealed class PatchWithReseamManagerTests : BaseE2E
{
    private const string Repository = "https://github.com/JoanComasFdz/baconreader-reseam-patches";
    private const string Source = "baconreader-patches";
    private const string Apk = "BaconReaderPremium-6.1.4.apk";

    [Theory, PlayOnPhone]
    [PatchedByTheManager]
    [TestDescription("""
        @phone
        Scenario: Reseam Manager patches BaconReader Premium with the BaconReader patches for the first time
          Given Reseam Manager is installed
          And the BaconReader Premium 6.1.4 APK is in Downloads
          When I open "https://github.com/JoanComasFdz/baconreader-reseam-patches" in Chrome
          And I tap "➕ Add to Reseam Manager"
          And I tap "Add" on "Add baconreader-patches?"
          Then Reseam Manager shows the patch source "baconreader-patches" and its version
          When I tap the back arrow
          And I tap "Pick an APK"
          And I tap "BaconReaderPremium-6.1.4.apk"
          And I tap "Patch"
          And I tap "Don’t allow" on "Allow Reseam Manager to send you notifications?"
          And I tap "Install" on "BaconReader Premium is ready"
          And I turn on "Allow from this source"
          And I tap the back arrow on "Install unknown apps"
          And I tap "Install"
          And I tap "Install" on "Install this app?"
          And I tap "Open"
          And I tap "Don’t allow" on "Allow BaconReader Premium to send you notifications?"
          And I tap "Not now" on "Open Reddit links with BaconReader?"
          And I open the left menu
          Then the bottom of the left menu shows "Patches <that version> (Reseam <its version>)"
        """)]
    public async Task ReseamManagerPatchesBaconReaderPremiumWithTheBaconReaderPatchesForTheFirstTime(Screen screen)
    {
        // Given

        // When
        await Phone.Chrome.OpenAsync(Repository);
        await Phone.Chrome.GitHubRepository.WaitUntilShownAsync();
        await Phone.Chrome.GitHubRepository.ClickAddToReseamManagerAsync();
        await Phone.ReseamManager.AddSource.WaitUntilShownAsync();
        await Phone.ReseamManager.AddSource.ClickAddAsync();
        await Phone.ReseamManager.PatchSource.WaitUntilShownAsync();

        // Then
        var source = await Phone.ReseamManager.PatchSource.ReadAsync();
        Asserting.That(source).ShowsThePatchSource(Source);

        // When
        await Phone.ReseamManager.PatchSource.ClickBackAsync();
        await Phone.ReseamManager.Home.WaitUntilShownAsync();
        await Phone.ReseamManager.Home.ClickPickAnApkAsync();
        await Phone.FilePicker.WaitUntilFileShownAsync(Apk);
        await Phone.FilePicker.ClickFileAsync(Apk);
        await Phone.ReseamManager.AppPage.WaitUntilShownAsync();
        await Phone.ReseamManager.AppPage.ClickPatchAsync();
        await Phone.NotificationPermission.WaitUntilShownAsync("Reseam Manager");
        await Phone.NotificationPermission.ClickDontAllowAsync();
        await Phone.ReseamManager.Ready.WaitUntilShownAsync();
        await Phone.ReseamManager.Ready.ClickInstallAsync();
        await Phone.InstallUnknownApps.WaitUntilShownAsync();
        await Phone.InstallUnknownApps.TickAllowFromThisSourceAsync();
        await Phone.InstallUnknownApps.ClickBackArrowAsync();
        await Phone.ReseamManager.Ready.WaitUntilShownAsync();
        await Phone.ReseamManager.Ready.ClickInstallAsync();
        await Phone.PackageInstaller.WaitUntilShownAsync();
        await Phone.PackageInstaller.ClickInstallAsync();
        await Phone.ReseamManager.Ready.WaitUntilInstalledAsync();
        await Phone.ReseamManager.Ready.ClickOpenAsync();
        await Phone.NotificationPermission.WaitUntilShownAsync("BaconReader Premium");
        await Phone.NotificationPermission.ClickDontAllowAsync();
        await Phone.BaconReader.OpenRedditLinksPrompt.WaitUntilShownAsync();
        await Phone.BaconReader.OpenRedditLinksPrompt.ClickNotNowAsync();
        await Phone.BaconReader.Main.LeftMenu.OpenAsync();
        await Phone.BaconReader.Main.LeftMenu.WaitUntilShownAsync();

        // Then
        var footer = await Phone.BaconReader.Main.LeftMenu.ReadFooterAsync();
        Asserting.That(footer).ShowsThePatches(source.Version);
    }
}
