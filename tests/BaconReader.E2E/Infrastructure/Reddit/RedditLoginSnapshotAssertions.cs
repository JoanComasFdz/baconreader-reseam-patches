// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using JoanComasFdz.AssertingThat;

namespace BaconReader.E2E.Infrastructure.Reddit;

/// <summary>What a test checks of what Reddit's login saw.</summary>
public static class RedditLoginSnapshotAssertions
{
    // RedReader's identity, which "Spoof client" sends by default.
    private const string RedReaderClientId = "yH0aTnJEt6qUgGn835B4vg";
    private const string RedReaderRedirectUri = "redreader://rr_oauth_redir";
    private const string RedReaderUserAgent = "org.quantumbadger.redreader/1.25.1";

    /// <summary>
    /// The app asked for the login page and traded the code as RedReader: its client ID and redirect URI at both steps,
    /// and its User-Agent on the token request (the login pages themselves go through the web view, with the web
    /// view's).
    /// </summary>
    public static AssertingThat<RedditLoginSnapshot> SawRedReadersClientIdRedirectUriAndUserAgent(this AssertingThat<RedditLoginSnapshot> that)
    {
        var steps = that.InstanceToAssert.Steps;
        var authorize = steps.FirstOrDefault(step => step.Step == "authorize");
        var token = steps.FirstOrDefault(step => step.Step == "token");
        Assert.True(authorize is not null, "Reddit never showed its login page, but the app was expected to ask for it.");
        Assert.True(token is not null, "The app never traded the login for a token, but it was expected to.");
        Assert.True(
            authorize.ClientId == RedReaderClientId && token.ClientId == RedReaderClientId,
            $"The app logged in with client ID \"{authorize.ClientId}\" and traded the code with \"{token.ClientId}\", but RedReader's, \"{RedReaderClientId}\", was expected.");
        Assert.True(
            authorize.RedirectUri == RedReaderRedirectUri && token.RedirectUri == RedReaderRedirectUri,
            $"The app asked to be sent back to \"{authorize.RedirectUri}\" and traded the code for \"{token.RedirectUri}\", but RedReader's, \"{RedReaderRedirectUri}\", was expected.");
        Assert.True(
            token.UserAgent == RedReaderUserAgent,
            $"The app's User-Agent was \"{token.UserAgent}\", but RedReader's, \"{RedReaderUserAgent}\", was expected.");
        return that;
    }
}
