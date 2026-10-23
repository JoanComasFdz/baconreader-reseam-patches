// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

namespace BaconReader.E2E.Infrastructure.Device;

/// <summary>
/// Records the emulator's screen while a scenario plays, with Android's own <c>screenrecord</c>, on the device: 540×960
/// at 1 Mbit/s (a cloud session reaches the emulator at ~3–4 Mbit/s: a minute is a few MB to pull). <c>screenrecord</c> stops by itself after
/// 3 minutes, so a loop on the device starts the next part until told to stop; <see cref="StopAsync"/> pulls the parts
/// and joins them with ffmpeg. Android draws each touch on the screen while recording (<c>show_touches</c>), so the
/// video shows where every tap lands.
/// </summary>
public sealed class ScreenRecording
{
    private const string Folder = "/sdcard/e2e/recording";
    private const string StopFile = "/data/local/tmp/e2e/recording.stop";

    private ScreenRecording()
    {
    }

    /// <summary>Starts recording the screen.</summary>
    public static async Task<ScreenRecording> StartAsync()
    {
        await Commands.ShellAsync($"rm -rf {Folder} {StopFile}; mkdir -p {Folder} /data/local/tmp/e2e");
        await Commands.ShellAsync("settings put system show_touches 1");
        // Detached from adb (nohup, &), so the loop outlives this shell; each part is a new file, 0.mp4, 1.mp4…
        await Commands.ShellAsync(
            $"nohup sh -c 'i=0; while [ ! -f {StopFile} ]; do screenrecord --size 540x960 --bit-rate 1000000 --time-limit 180 {Folder}/$i.mp4; i=$((i+1)); done' > /dev/null 2>&1 &");
        await WaitUntilRecordingAsync();
        return new ScreenRecording();
    }

    /// <summary>Stops recording and writes the video to <paramref name="target"/> (an MP4).</summary>
    public async Task StopAsync(string target)
    {
        await Commands.ShellAsync($"touch {StopFile}; pkill -2 screenrecord || true");
        // screenrecord finishes its file after SIGINT; the loop sees the stop file and ends.
        for (var tries = 0; tries < 50 && (await Commands.ShellAsync("pidof screenrecord || true")).Length > 0; tries++)
        {
            await Task.Delay(200);
        }

        await Commands.ShellAsync("settings put system show_touches 0");
        var parts = (await Commands.ShellAsync($"ls {Folder}"))
            .Split('\n', StringSplitOptions.RemoveEmptyEntries)
            .Where(name => name.EndsWith(".mp4", StringComparison.Ordinal))
            .OrderBy(name => int.Parse(Path.GetFileNameWithoutExtension(name), System.Globalization.CultureInfo.InvariantCulture))
            .ToArray();
        var local = Path.Combine(Path.GetTempPath(), "e2e-recording-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(local);
        try
        {
            foreach (var part in parts)
            {
                await Commands.AdbAsync("pull", $"{Folder}/{part}", Path.Combine(local, part));
            }

            await Commands.ShellAsync($"rm -rf {Folder} {StopFile}");
            Directory.CreateDirectory(Path.GetDirectoryName(target)!);
            if (parts.Length == 1)
            {
                File.Copy(Path.Combine(local, parts[0]), target, overwrite: true);
            }
            else if (parts.Length > 1)
            {
                var list = Path.Combine(local, "parts.txt");
                await File.WriteAllLinesAsync(list, parts.Select(part => $"file '{Path.Combine(local, part)}'"));
                await Commands.RunOrFailAsync("ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", list, "-c", "copy", target);
            }
        }
        finally
        {
            Directory.Delete(local, recursive: true);
        }
    }

    // screenrecord takes a moment to start drawing: a scenario that starts at once would miss its first frames.
    private static async Task WaitUntilRecordingAsync()
    {
        for (var tries = 0; tries < 25; tries++)
        {
            if ((await Commands.ShellAsync("pidof screenrecord || true")).Length > 0)
            {
                await Task.Delay(500);
                return;
            }

            await Task.Delay(200);
        }

        throw new InvalidOperationException("screenrecord didn't start on the emulator.");
    }
}
