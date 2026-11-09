// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using System.Diagnostics;
using System.Text.RegularExpressions;
using BaconReader.E2E.Infrastructure.Device;

namespace BaconReader.E2E.Infrastructure.Steps;

/// <summary>
/// Marks in a scenario's recording when each line of its Gherkin plays (<see cref="ScreenRecording.NoteLine"/>), for the
/// site's player to highlight it as the video passes it. A statement of the scenario starts when it asks BaseE2E for
/// <c>Phone</c> or <c>Reddit</c> (<see cref="NoteCaller"/>): its Given or When line is marked then. An assertion asks
/// for neither, so its Then line is marked once the scenario has gone past it: when the next statement starts, or when
/// the scenario ends. The scenario then stands still for <see cref="Hold"/> on each Then line, the screen it read on the
/// video, so each line is highlighted in turn. When the scenario fails, the line that failed is marked as failed: the
/// assertion its stack trace names, or else the line the last statement played or served.
/// </summary>
public sealed partial class GherkinMarking
{
    /// <summary>How long the recording stays on each Then line.</summary>
    public static readonly TimeSpan Hold = TimeSpan.FromMilliseconds(800);

    private readonly ScenarioSteps steps;
    private readonly ScreenRecording recording;
    private readonly Type scenario;

    // The last statement that started, an index in steps.Steps.
    private int reached = -1;

    /// <summary>
    /// Marks the lines BaseE2E plays itself (<c>BaconReader is patched with …</c>) at <paramref name="started"/>, the
    /// recording's start (a <see cref="Stopwatch.GetTimestamp"/>), and the others as <paramref name="scenario"/>'s
    /// statements play them.
    /// </summary>
    public GherkinMarking(ScenarioSteps steps, ScreenRecording recording, Type scenario, long started)
    {
        this.steps = steps;
        this.recording = recording;
        this.scenario = scenario;
        foreach (var line in steps.PlayedByBaseE2E)
        {
            recording.NoteLine(line, started, failed: false);
        }
    }

    /// <summary>
    /// Called when the scenario asks for <c>Phone</c> or <c>Reddit</c>: if a new statement of it starts, holds on the
    /// Then lines whose assertions passed since the last one, then marks its own line.
    /// </summary>
    public void NoteCaller()
    {
        var index = FindStatement();
        if (index <= reached)
        {
            return;
        }

        Thread.Sleep(HoldOnPassed(AssertionsSince(index)));
        reached = index;
        if (steps.Steps[index].Plays is { } line)
        {
            recording.NoteLine(line, Stopwatch.GetTimestamp(), failed: false);
        }
    }

    /// <summary>
    /// Called when the scenario ends, before its recording stops: holds on the Then lines whose assertions passed since
    /// its last statement started, and, when it failed with <paramref name="stackTraces"/>, marks the line that failed.
    /// </summary>
    public async Task EndAsync(bool passed, IReadOnlyList<string?> stackTraces)
    {
        var pending = AssertionsSince(steps.Steps.Length);
        var failed = stackTraces.SelectMany(trace => AssertionCall().Matches(trace ?? "")).Select(call => call.Groups[1].Value).ToHashSet();
        if (passed || stackTraces.Count == 0)
        {
            await Task.Delay(HoldOnPassed(pending));
            return;
        }

        var failing = pending.FindIndex(assertion => assertion.Names.Any(failed.Contains));
        if (failing < 0)
        {
            if (reached >= 0)
            {
                recording.NoteLine(steps.Steps[reached].FailsAs, Stopwatch.GetTimestamp(), failed: true);
                await Task.Delay(Hold);
            }

            return;
        }

        var held = HoldOnPassed([.. pending.Take(failing)]);
        recording.NoteLine(pending[failing].Line, Stopwatch.GetTimestamp() + Ticks(held), failed: true);
        await Task.Delay(held + Hold);
    }

    // Marks each of these Then lines Hold after the one before, from now, and says how long to stand still for them.
    private TimeSpan HoldOnPassed(List<Assertion> passed)
    {
        var now = Stopwatch.GetTimestamp();
        for (var at = 0; at < passed.Count; at++)
        {
            recording.NoteLine(passed[at].Line, now + Ticks(Hold * at), failed: false);
        }

        return Hold * passed.Count;
    }

    // The assertions of the statements between the last one that started and the one at `index`.
    private List<Assertion> AssertionsSince(int index) =>
        [.. steps.Steps.Skip(reached + 1).Take(index - reached - 1).SelectMany(step => step.Assertions)];

    // The statement of the scenario on the stack, by its line; -1 when the scenario isn't on it.
    private int FindStatement() => FindLine(scenario) is { } line ? steps.IndexOf(line) : -1;

    /// <summary>
    /// The line (1-based, in its file) of the first frame of <paramref name="scenario"/>'s code on the stack: its method,
    /// or the async state machine the compiler made of it; none when it isn't on the stack.
    /// </summary>
    internal static int? FindLine(Type scenario) =>
        new StackTrace(fNeedFileInfo: true).GetFrames()
            .Where(frame => frame.GetMethod()?.DeclaringType is { } type && (type == scenario || type.DeclaringType == scenario))
            .Select(frame => frame.GetFileLineNumber())
            .FirstOrDefault(line => line > 0) is var found and > 0 ? found : null;

    private static long Ticks(TimeSpan time) => (long)(time.TotalSeconds * Stopwatch.Frequency);

    // An assertion in a stack trace: "at …ScreenSnapshotAssertions.ShowsThePost(AssertingThat`1 that, …)".
    [GeneratedRegex(@"SnapshotAssertions\.(\w+)\(")]
    private static partial Regex AssertionCall();
}
