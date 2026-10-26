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

    [Theory]
    [InlineData("EmbedCommentImagesShowsAGIFInACommentAsAnImage", "embed-comment-images-shows-agif-in-a-comment-as-an-image-phone")]
    [InlineData("BaconReaderPatchedWithSpoofClientOpensOnTheFrontPage", "bacon-reader-patched-with-spoof-client-opens-on-the-front-page-phone")]
    [InlineData("BaconReaderPatchedWithSpoofClientLogsInToRedditAsRedReader", "bacon-reader-patched-with-spoof-client-logs-in-to-reddit-as-red-reader-phone")]
    [InlineData("BaconReaderLoggedInShowsThePostsRedditHasInAll", "bacon-reader-logged-in-shows-the-posts-reddit-has-in-all-phone")]
    [InlineData("ReapersTitheGives3StacksForAKillOf3", "reapers-tithe-gives3-stacks-for-a-kill-of3-phone")]
    public void GifName_OfTheScenariosPostedSoFarStaysAsItIs(string methodName, string gifName) =>
        Assert.Equal(gifName, ScenarioNaming.ToGifName(methodName, Screen.Phone));

    [Theory]
    [InlineData("E2E", "e2e-phone")]
    [InlineData("BaconReaderPatchedWithE2EFakeRedditOpensOnTheFrontPage", "bacon-reader-patched-with-e2e-fake-reddit-opens-on-the-front-page-phone")]
    [InlineData("OpensWithE2E", "opens-with-e2e-phone")]
    public void GifName_KeepsARunOfCapitalsWithADigitInsideOneWord(string methodName, string gifName) =>
        Assert.Equal(gifName, ScenarioNaming.ToGifName(methodName, Screen.Phone));

    [Fact]
    public void MethodName_KeepsARunOfCapitalsWithADigitInsideOneWord() =>
        Assert.Equal(
            "BaconReaderPatchedWithE2EFakeRedditOpensOnTheFrontPage",
            ScenarioNaming.ToMethodName("BaconReader patched with E2E fake Reddit opens on the front page"));

    [Fact]
    public void DomainFolder_IsTheNamespaceUnderScenarios() =>
        Assert.Equal("Account", ScenarioNaming.ToDomainFolder(typeof(Scenarios.Account.SpoofClientTests)));

    [Fact]
    public void DomainFolder_KeepsE2EWhole() =>
        Assert.Equal("E2E", ScenarioNaming.ToDomainFolder(typeof(Scenarios.E2E.E2EFakeRedditTests)));

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
