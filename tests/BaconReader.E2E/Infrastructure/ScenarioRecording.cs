// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

namespace BaconReader.E2E.Infrastructure;

/// <summary>
/// What tools/e2e/record.sh reads next to a run's video, <c>&lt;gif&gt;.mp4</c> (each project's BaseE2E writes it,
/// camelCase JSON): the scenario's names and Gherkin, the screen it was played on, the patches its app was patched
/// with, when the app's first screen was shown (seconds into the video) and whether it passed.
/// </summary>
public sealed record ScenarioRecording(
    string Domain,
    string Class,
    string Method,
    string Title,
    string Gherkin,
    string Screen,
    string Gif,
    IReadOnlyList<string> Patches,
    double? HomeShownSeconds,
    bool Passed);
