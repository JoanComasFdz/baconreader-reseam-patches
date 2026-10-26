// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using System.Diagnostics;
using System.Reflection;
using System.Text.Json;
using BaconReader.E2E.Infrastructure.Device;
using BaconReader.E2E.Infrastructure.Reddit;
using BaconReader.E2E.Infrastructure.Vocabulary;
using BaconReader.FakeReddit;
using OpenQA.Selenium.Appium;
using OpenQA.Selenium.Appium.Android;
using Xunit.v3;

namespace BaconReader.E2E.Infrastructure;

/// <summary>
/// Everything around a scenario that isn't the scenario (E2E-TEST-CODE-RULES.md, rule b):
/// - skips it unless <c>E2E_APPIUM_URL</c> points at the emulator's Appium (README, "The emulator");
/// - the fake Reddit the app talks to (README, "The fake Reddit"): started once per run, in this process, on
///   <c>E2E_FAKE_REDDIT_URL</c>'s port, which the phone reaches through <c>adb reverse</c> (<see cref="FakeRedditLink"/>);
///   emptied before each scenario, with the test account (<see cref="TestAccount"/>) made again; the scenario builds
///   its Reddit data through <see cref="Reddit"/>; after it, the scenario fails if the app made a request the fake
///   couldn't answer (<see cref="UnhandledRequests"/>);
/// - patches the clean BaconReader APK with the scenario's <see cref="PatchedWithAttribute"/> patches, the fewest it
///   needs, and the ones every scenario needs (<see cref="PatchedApps.Always"/>: "E2E fake Reddit"…), and installs it
///   (<see cref="PatchedApps"/>: once per set of patches and bundle, and only when it isn't the one installed already);
/// - a fresh app per scenario: stopped, its data cleared, its notifications allowed (unless the scenario is
///   <see cref="WithoutNotificationsAllowedAttribute"/>), so scenarios share nothing (no login, no setting, no cache);
/// - an Appium session on the phone (<see cref="Screen.Phone"/>, the scenario's <c>[PlayOnPhone]</c>) that leaves the
///   app alone until the scenario opens it;
/// - when the run records (<c>E2E_VIDEO_DIR</c>): records the screen from before the app opens to the end
///   (<see cref="ScreenRecording"/>) and saves the video, also when the scenario fails,
///   with its record next to it (title, Gherkin, screen, patches, GIF name, domain folder, when the main screen first
///   showed, whether it passed);
/// - gives the scenario its <see cref="Phone"/>.
/// </summary>
public abstract class BaseE2E : IAsyncLifetime
{
    private static readonly TimeSpan CommandTimeout = TimeSpan.FromMinutes(2);
    private static readonly SemaphoreSlim StartingFakeReddit = new(1, 1);
    private static FakeRedditServer? fakeReddit;

    private AndroidDriver? driver;
    private ScreenRecording? recording;
    private IReadOnlyList<Patch> patches = [];
    private Screen screen;
    private readonly Stopwatch sinceRecording = new();
    private double? appShownAt;

    /// <summary>The phone as the user holds it: Android's screens and BaconReader on it.</summary>
    protected Phone.Phone Phone { get; private set; } = null!;

    /// <summary>The fake Reddit the app talks to: a Given builds the scenario's Reddit data here.</summary>
    protected Reddit.Reddit Reddit { get; private set; } = null!;

    public async ValueTask InitializeAsync()
    {
        Assert.SkipWhen(
            E2EDevice.AppiumUrl is null,
            "E2E_APPIUM_URL isn't set: end-to-end tests need the emulator (README, \"The emulator\").");

        var fake = await StartFakeRedditAsync();
        fake.World.Reset();
        fake.World.AddAccount(TestAccount.UserName, TestAccount.Password);
        Reddit = new Reddit.Reddit(fake.World);

        screen = ReadScreen();
        patches = ReadPatches();
        var apk = await PatchedApps.PatchAsync([.. patches.Union(PatchedApps.Always)]);
        await PatchedApps.InstallAsync(apk);
        await FakeRedditLink.EnsureAsync();
        await PatchedApps.ResetAsync(allowNotifications: ReadMethod()?.GetCustomAttribute<WithoutNotificationsAllowedAttribute>() is null);
        driver = await Task.Run(OpenDriver);
        Phone = new Phone.Phone(driver, patches, NoteAppShown);

        if (E2EDevice.VideoDirectory is not null)
        {
            try
            {
                recording = await ScreenRecording.StartAsync();
                sinceRecording.Start();
            }
            catch (Exception)
            {
                // xunit doesn't dispose a test whose start failed.
                await CloseAsync(passed: false);
                throw;
            }
        }
    }

    public async ValueTask DisposeAsync()
    {
        var unhandled = fakeReddit is null ? [] : UnhandledRequests.Of(fakeReddit.World);
        await CloseAsync(TestContext.Current.TestState?.Result == TestResult.Passed && unhandled.IsEmpty);
        GC.SuppressFinalize(this);
        if (!unhandled.IsEmpty)
        {
            throw new Xunit.Sdk.XunitException(UnhandledRequests.Describe(unhandled));
        }
    }

    // The fake Reddit, started by the first scenario of the run, on the port the phone's forward reaches.
    private static async Task<FakeRedditServer> StartFakeRedditAsync()
    {
        await StartingFakeReddit.WaitAsync();
        try
        {
            return fakeReddit ??= await FakeRedditServer.StartAsync(E2EDevice.FakeRedditUrl);
        }
        finally
        {
            StartingFakeReddit.Release();
        }
    }

    // Ends the recording and the Appium session (once), and saves the video with the scenario's record.
    private async Task CloseAsync(bool passed)
    {
        var closing = driver;
        driver = null;
        if (recording is not null)
        {
            var stopping = recording;
            recording = null;
            await SaveRecordingAsync(stopping, passed);
        }

        if (closing is not null)
        {
            await Task.Run(() =>
            {
                closing.TerminateApp(E2EDevice.Package);
                closing.Quit();
            });
        }
    }

    // A session that doesn't touch the app: BaseE2E installs and resets it, the scenario opens it.
    // skipServerInstallation / skipDeviceInitialization: Appium's helper apps stay installed on the emulator (README, "The emulator").
    private static AndroidDriver OpenDriver()
    {
        var options = new AppiumOptions
        {
            PlatformName = "Android",
            AutomationName = "UiAutomator2",
        };
        options.AddAdditionalAppiumOption("udid", E2EDevice.AppiumUdid);
        options.AddAdditionalAppiumOption("noReset", true);
        options.AddAdditionalAppiumOption("autoLaunch", false);
        options.AddAdditionalAppiumOption("newCommandTimeout", 300);
        // UiAutomator2 waits for the app to go idle before each command, up to 10 s, and BaconReader's dialogs never
        // do: each lookup on one stood still for those 10 s, on screen. The scenarios wait for what they need themselves.
        options.AddAdditionalAppiumOption("waitForIdleTimeout", 0);
        if (Environment.GetEnvironmentVariable("E2E_APPIUM_INSTALL_HELPERS") is not "1")
        {
            options.AddAdditionalAppiumOption("skipServerInstallation", true);
            options.AddAdditionalAppiumOption("skipDeviceInitialization", true);
        }
        else
        {
            // Installing the helper apps over a slow link to the emulator (a cloud session's, ~3–4 Mbit/s) takes longer
            // than Appium's 20 s defaults.
            options.AddAdditionalAppiumOption("uiautomator2ServerInstallTimeout", 180_000);
            options.AddAdditionalAppiumOption("adbExecTimeout", 180_000);
        }

        return new AndroidDriver(E2EDevice.AppiumUrl!, options, CommandTimeout);
    }

    // The first time the main screen shows, in seconds into the video: the run's startup time in the PR's post.
    private void NoteAppShown()
    {
        if (appShownAt is null && sinceRecording.IsRunning)
        {
            appShownAt = sinceRecording.Elapsed.TotalSeconds;
        }
    }

    // The screen the scenario's [PlayOnPhone] row plays it on (its one argument).
    private static Screen ReadScreen() =>
        TestContext.Current.Test is IXunitTest { TestMethodArguments: [Screen played] } ? played : Screen.Phone;

    private IReadOnlyList<Patch> ReadPatches() =>
        ReadMethod()?.GetCustomAttribute<PatchedWithAttribute>()?.Patches ?? [];

    private MethodInfo? ReadMethod() =>
        TestContext.Current.TestMethod?.MethodName is { } name ? GetType().GetMethod(name) : null;

    private async Task SaveRecordingAsync(ScreenRecording stopping, bool passed)
    {
        var videos = E2EDevice.VideoDirectory!;
        var method = TestContext.Current.TestMethod?.MethodName ?? "unnamed";
        var gherkin = Gherkin.Read(ReadMethod()?.GetCustomAttribute<TestDescriptionAttribute>()?.Gherkin ?? "");
        var domain = ScenarioNaming.ToDomainFolder(GetType());
        var gifName = ScenarioNaming.ToGifName(method, screen);
        var folder = Path.Combine(videos, domain);
        await stopping.StopAsync(Path.Combine(folder, gifName + ".mp4"));
        var record = new ScenarioRecording(
            domain,
            GetType().Name,
            method,
            gherkin.Title.Length > 0 ? gherkin.Title : method,
            gherkin.Text,
            screen.ToString().ToLowerInvariant(),
            gifName,
            [.. patches.Select(patch => patch.ToShownText())],
            appShownAt,
            passed);
        await File.WriteAllTextAsync(Path.Combine(folder, gifName + ".json"), JsonSerializer.Serialize(record, RecordingJson));
        TestContext.Current.SendDiagnosticMessage($"{GetType().Name}.{method}: recorded {sinceRecording.Elapsed.TotalSeconds:0} s");
    }

    private static readonly JsonSerializerOptions RecordingJson = new() { WriteIndented = true, PropertyNamingPolicy = JsonNamingPolicy.CamelCase };
}
