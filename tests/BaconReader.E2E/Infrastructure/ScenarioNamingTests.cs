// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

namespace BaconReader.E2E.Infrastructure;

/// <summary>The scenario names record.sh and the PR's GIFs rely on (runs without the emulator).</summary>
public sealed class ScenarioNamingTests
{
    [Fact]
    public void MethodName_IsTheTitlesWordsEachCapitalized() =>
        Assert.Equal(
            "BaconReaderPatchedWithSpoofClientOpensOnTheFrontPage",
            ScenarioNaming.ToMethodName("BaconReader patched with Spoof client opens on the front page"));

    [Fact]
    public void MethodName_LeavesOutPunctuationAndEmoji() =>
        Assert.Equal(
            "SpoofClientLogsYouInAgain",
            ScenarioNaming.ToMethodName("Spoof client: logs you in, again · 🥓"));

    [Fact]
    public void GifName_IsTheMethodNameKebaberizedWithItsScreen() =>
        Assert.Equal(
            "bacon-reader-patched-with-spoof-client-opens-on-the-front-page-phone",
            ScenarioNaming.ToGifName("BaconReaderPatchedWithSpoofClientOpensOnTheFrontPage", Screen.Phone));

    [Fact]
    public void DomainFolder_IsTheNamespaceUnderScenarios() =>
        Assert.Equal("Account", ScenarioNaming.ToDomainFolder(typeof(Scenarios.Account.SpoofClientTests)));

    [Fact]
    public void DomainFolder_OfANamespaceOutsideScenariosIsUnsorted() =>
        Assert.Equal("Unsorted", ScenarioNaming.ToDomainFolder("BaconReader.E2E.Infrastructure"));

    [Fact]
    public void Gherkin_ReadsItsTagsTitleAndDedentedText()
    {
        var gherkin = Gherkin.Read("""
            @phone
            Scenario: BaconReader opens
              Given BaconReader is patched with "Spoof client" and installed
            """);

        Assert.Equal(["@phone"], gherkin.Tags);
        Assert.Equal("BaconReader opens", gherkin.Title);
        Assert.Equal("@phone\nScenario: BaconReader opens\n  Given BaconReader is patched with \"Spoof client\" and installed", gherkin.Text);
    }
}
