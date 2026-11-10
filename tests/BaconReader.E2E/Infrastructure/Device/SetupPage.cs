// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

namespace BaconReader.E2E.Infrastructure.Device;

/// <summary>
/// The fake Reddit's setup page (<c>/_fake/setup</c>) in the phone's Chrome: a recording starts on it, so the video shows
/// the scenario's Given lines make its accounts and posts, before the app opens over it. Only when recording: a run that
/// doesn't record never opens Chrome. Chrome's first run must have been done on the phone (the README, "The phone").
/// </summary>
public static class SetupPage
{
    private const string Chrome = "com.android.chrome";

    // Chrome takes a moment to start and show the page before the recording starts.
    private static readonly TimeSpan Loading = TimeSpan.FromMilliseconds(1500);

    /// <summary>Opens the setup page in Chrome, in the same tab each time.</summary>
    public static async Task ShowAsync()
    {
        // The application id makes Chrome reuse the tab it opened for it, so tabs don't pile up scenario after scenario.
        await Commands.ShellAsync(
            $"am start -a android.intent.action.VIEW -d {E2EDevice.FakeRedditUrl}/_fake/setup"
            + $" -e com.android.browser.application_id e2e-setup {Chrome}");
        await Task.Delay(Loading);
    }

    /// <summary>Closes Chrome, so the next scenario starts from the home screen.</summary>
    public static Task CloseAsync() => Commands.ShellAsync($"am force-stop {Chrome}");
}
