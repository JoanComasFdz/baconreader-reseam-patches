// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

namespace BaconReader.E2E.Infrastructure.Device;

/// <summary>
/// Lets the phone reach the fake Reddit, which runs in the test process: the phone's port forwarded to this machine's
/// (<c>adb reverse</c>).
/// </summary>
public static class FakeRedditLink
{
    /// <summary>
    /// Forwards the phone's port of <see cref="E2EDevice.FakeRedditUrl"/> to the same port here, unless it already is
    /// (a forward is lost when adb loses the device).
    /// </summary>
    public static async Task EnsureAsync()
    {
        var port = new Uri(E2EDevice.FakeRedditUrl).Port;
        var forwards = await Commands.AdbAsync("reverse", "--list");
        if (!forwards.Contains($"tcp:{port} tcp:{port}", StringComparison.Ordinal))
        {
            await Commands.AdbAsync("reverse", $"tcp:{port}", $"tcp:{port}");
        }
    }
}
