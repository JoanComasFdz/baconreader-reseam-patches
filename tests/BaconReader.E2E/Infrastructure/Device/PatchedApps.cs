// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using BaconReader.E2E.Infrastructure.Vocabulary;

namespace BaconReader.E2E.Infrastructure.Device;

/// <summary>
/// Patches the clean BaconReader APK with a scenario's patches and installs it on the phone, as cheaply as a slow
/// link to it allows (a cloud session's reaches it at ~3–4 Mbit/s):
/// - the released bundle and the test-only one (e2e-patches/) are both loaded, and "E2E fake Reddit" gets the fake Reddit
///   server's URL (<see cref="E2EDevice.FakeRedditUrl"/>), in every scenario's app (<see cref="Always"/>);
/// - a Startup scenario's set of patches gets <c>--preset none</c> and an <c>--enable</c> for each; a LoggedIn scenario's,
///   every patch, <c>--preset all</c>: every patch of both bundles, "E2E fake Reddit" included;
/// - each set of patches is patched once per bundle (<c>build/e2e/patched/&lt;hash&gt;.apk</c>; reseam takes about a second);
/// - every APK is signed with one test key (<c>build/e2e/signing/</c>, made by reseam on the first patch), so one installs over
///   another; an install signed with another key (the owner's, another session's) is uninstalled first;
/// - an APK already installed (the device's copy has the same SHA-256) isn't sent again: sending one takes ~40 s.
/// </summary>
public static partial class PatchedApps
{
    private const string DeviceApk = "/data/local/tmp/e2e/app.apk";
    private static readonly SemaphoreSlim OneAtATime = new(1, 1);

    private static string Reseam => Environment.GetEnvironmentVariable("E2E_RESEAM") is { Length: > 0 } reseam ? reseam : "reseam";

    // "E2E fake Reddit"'s option, by its patch ID (its package and property).
    private const string FakeRedditUrlOption = "com.joancomasfdz.patches.baconreader.e2e.e2eFakeReddit.baseUrl";

    private static string SigningFolder => Path.Combine(E2EDevice.WorkFolder, "signing");

    /// <summary>
    /// The patches every scenario's app is patched with, besides those its Given names, which never name these:
    /// "E2E fake Reddit", so the app talks to the fake Reddit, never to Reddit.
    /// </summary>
    public static IReadOnlyList<Patch> Always { get; } = [Patch.E2EFakeReddit];

    /// <summary>
    /// Patches the clean APK with <paramref name="patchSet"/> and <see cref="Always"/> (once per bundle) and returns the
    /// patched APK.
    /// </summary>
    public static async Task<string> PatchAsync(PatchSet patchSet)
    {
        await OneAtATime.WaitAsync();
        try
        {
            CheckExists(E2EDevice.Bundle, "the bundle: build it with ./gradlew bundle (README)");
            CheckExists(E2EDevice.TestBundle, "the e2e bundle: build it with ./gradlew -p e2e-patches bundle (README)");
            CheckExists(E2EDevice.BaseApk, "the clean BaconReader APK: the phone's connection script downloads it (README, \"The phone\")");
            // Every patch is every patch of both bundles, Always's among them.
            var names = patchSet is PatchSet.Only only ? new PatchSet.Only([.. only.Patches.Union(Always)]).ToShownText() : patchSet.ToShownText();
            var key = Hash(
                $"{HashFile(E2EDevice.Bundle)}\n{HashFile(E2EDevice.TestBundle)}\n{HashFile(E2EDevice.BaseApk)}\n{E2EDevice.FakeRedditUrl}\n{string.Join('\n', names)}")[..16];
            var patched = Path.Combine(E2EDevice.WorkFolder, "patched", key + ".apk");
            if (File.Exists(patched))
            {
                return patched;
            }

            Directory.CreateDirectory(Path.GetDirectoryName(patched)!);
            var arguments = new List<string>
            {
                "patch", E2EDevice.BaseApk,
                "--bundle", E2EDevice.Bundle,
                "--bundle", E2EDevice.TestBundle,
            };
            foreach (var signer in new[] { await ReadSignerAsync(E2EDevice.Bundle), await ReadSignerAsync(E2EDevice.TestBundle) }.Distinct())
            {
                arguments.AddRange(["--trust", signer]);
            }

            if (patchSet is PatchSet.Every)
            {
                arguments.AddRange(["--preset", "all"]);
            }
            else
            {
                arguments.AddRange(["--preset", "none"]);
                foreach (var name in names)
                {
                    arguments.AddRange(["--enable", name]);
                }
            }

            // Every set has "E2E fake Reddit" (Always): reseam refuses an option of a patch it doesn't run.
            arguments.AddRange(["--option", $"{FakeRedditUrlOption}={E2EDevice.FakeRedditUrl}"]);

            var signingKey = Path.Combine(SigningFolder, "e2e.pk8");
            var signingCert = Path.Combine(SigningFolder, "e2e.der");
            var hasKey = File.Exists(signingKey) && File.Exists(signingCert);
            if (hasKey)
            {
                arguments.AddRange(["--key", signingKey, "--cert", signingCert]);
            }

            var writing = Path.Combine(Path.GetDirectoryName(patched)!, key + "-writing.apk");
            arguments.AddRange(["--output", writing]);
            var ran = await Commands.RunAsync(Reseam, [.. arguments]);
            var log = ran.Output + ran.Error;
            if (!ran.Succeeded || FailedCount().Match(log) is not { Success: true } finished || finished.Groups[1].Value != "0")
            {
                throw new InvalidOperationException($"reseam couldn't apply every patch of {string.Join(", ", names)}:\n{log}");
            }

            if (!hasKey)
            {
                // Without --key reseam signs with a key it makes and leaves next to the APK: kept, every later APK uses it.
                Directory.CreateDirectory(SigningFolder);
                File.Move(Path.ChangeExtension(writing, ".pk8"), signingKey, overwrite: true);
                File.Move(Path.ChangeExtension(writing, ".der"), signingCert, overwrite: true);
            }

            File.Move(writing, patched, overwrite: true);
            return patched;
        }
        finally
        {
            OneAtATime.Release();
        }
    }

    /// <summary>Installs <paramref name="apk"/> on the phone, with its runtime permissions granted, unless it's already the one installed.</summary>
    public static async Task InstallAsync(string apk)
    {
        var sha = HashFile(apk);
        if (await ReadInstalledShaAsync() == sha)
        {
            return;
        }

        var installing = System.Diagnostics.Stopwatch.StartNew();
        await Commands.ShellAsync("mkdir -p /data/local/tmp/e2e");
        await Commands.AdbAsync("push", apk, DeviceApk);
        var installed = await Commands.ShellAsync($"pm install -r -g {DeviceApk} 2>&1 || true");
        if (installed.Contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE", StringComparison.Ordinal))
        {
            await Commands.ShellAsync($"pm uninstall {E2EDevice.Package}");
            installed = await Commands.ShellAsync($"pm install -g {DeviceApk} 2>&1 || true");
        }

        await Commands.ShellAsync($"rm -f {DeviceApk}");
        if (!installed.Contains("Success", StringComparison.Ordinal))
        {
            throw new InvalidOperationException($"Installing {Path.GetFileName(apk)} failed: {installed}");
        }

        RunLog.Installed(Path.GetFileName(apk), installing.Elapsed);
    }

    /// <summary>
    /// Leaves the installed app as if just installed: stopped, its data cleared (logins, settings, what it cached), and no
    /// link it declares selected on Android's "Open by default" screen, which clearing its data keeps ("Prompt to open
    /// Reddit links" asks only while none of its Reddit links is); with its notifications denied, as if the user had
    /// answered "Don’t allow". Otherwise Android asks "Allow BaconReader Premium to send you notifications?" when
    /// BaconReader opens its next screen ("Add account"), over it, not as it opens: Android requests the question at
    /// launch, but drops it. No scenario is about it, so none answers it (docs/given-when-then-test-rules.md, section 4).
    /// </summary>
    public static async Task ResetAsync()
    {
        const string notifications = "android.permission.POST_NOTIFICATIONS";
        await Commands.ShellAsync($"am force-stop {E2EDevice.Package}");
        await Commands.ShellAsync($"pm clear {E2EDevice.Package}");
        await Commands.ShellAsync(
            $"pm set-app-links-user-selection --user 0 --package {E2EDevice.Package} false all"
            + $" && pm set-app-links-allowed --user 0 --package {E2EDevice.Package} true");
        // USER_SET: the user answered, so Android doesn't ask (PermissionPolicyService asks only when it isn't set).
        await Commands.ShellAsync(
            $"pm revoke {E2EDevice.Package} {notifications} && pm set-permission-flags {E2EDevice.Package} {notifications} user-set");
    }

    private static async Task<string?> ReadInstalledShaAsync()
    {
        var path = await Commands.ShellAsync($"pm path {E2EDevice.Package} | grep base.apk | sed 's/^package://' || true");
        if (path.Length == 0)
        {
            return null;
        }

        var sum = await Commands.ShellAsync($"sha256sum {path}");
        return sum.Split(' ', 2)[0];
    }

    // The key that signed a bundle, which reseam must be told to trust.
    private static async Task<string> ReadSignerAsync(string bundle)
    {
        var listed = await Commands.RunOrFailAsync(Reseam, "bundle", "list", bundle);
        var signer = Signer().Match(listed);
        return signer.Success ? signer.Groups[1].Value : throw new InvalidOperationException($"reseam bundle list names no signer:\n{listed}");
    }

    private static void CheckExists(string path, string what)
    {
        if (!File.Exists(path))
        {
            throw new FileNotFoundException($"{path} is missing: {what}.", path);
        }
    }

    private static string HashFile(string path)
    {
        using var stream = File.OpenRead(path);
        return Convert.ToHexStringLower(SHA256.HashData(stream));
    }

    private static string Hash(string text) => Convert.ToHexStringLower(SHA256.HashData(Encoding.UTF8.GetBytes(text)));

    [GeneratedRegex(@"signer: ([0-9a-f]{64})")]
    private static partial Regex Signer();

    [GeneratedRegex(@"failed\S*=\S*?(\d+)")]
    private static partial Regex FailedCount();
}
