// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

namespace BaconReader.E2E.Infrastructure.Device;

/// <summary>
/// Lets the phone reach the fake Reddit, which runs in the test process: the phone's port forwarded to this machine's
/// (<c>adb reverse</c>), and no HTTP proxy on the phone.
/// </summary>
public static class FakeRedditLink
{
    /// <summary>
    /// Forwards the phone's port of <see cref="E2EDevice.FakeRedditUrl"/> to the same port here, unless it already is
    /// (a forward is lost when adb loses the device), and clears the phone's global HTTP proxy: Android sends every app's
    /// requests through it, 127.0.0.1's too, and a proxy set once stays in use after <c>settings delete</c> (a proxy at
    /// 10.0.2.2:8888 refused every request of the emulator's apps). <c>:0</c> clears it for good.
    /// </summary>
    public static async Task EnsureAsync()
    {
        var port = new Uri(E2EDevice.FakeRedditUrl).Port;
        var forwards = await Commands.AdbAsync("reverse", "--list");
        if (!forwards.Contains($"tcp:{port} tcp:{port}", StringComparison.Ordinal))
        {
            await Commands.AdbAsync("reverse", $"tcp:{port}", $"tcp:{port}");
        }

        if (await Commands.ShellAsync("settings get global http_proxy") != ":0")
        {
            await Commands.ShellAsync("settings put global http_proxy :0");
        }
    }
}
