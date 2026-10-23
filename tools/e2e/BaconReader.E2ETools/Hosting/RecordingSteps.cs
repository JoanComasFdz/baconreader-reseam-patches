// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using System.Collections.Immutable;
using BaconReader.E2ETools.Core;

namespace BaconReader.E2ETools.Hosting;

/// <summary>
/// The steps the e2e scripts call (tools/e2e/README.md): record.sh's unrecorded runs' report and scenarios.json, and
/// push-gifs.sh's files to push. Each reads its files, calls the pure logic and prints what the script reads.
/// </summary>
public static class RecordingSteps
{
    /// <summary>The unrecorded runs' table; an error when a results file is missing or no run passed or failed.</summary>
    public static Result<Unit, string> ReportRuns(ToolCommand.ReportRuns command)
    {
        var missing = command.TestResults.Where(path => !File.Exists(path)).ToImmutableArray();   // impure
        if (!missing.IsEmpty)
        {
            return new Result<Unit, string>.Error($"No test results were written ({Path.GetFileName(missing[0])}).");
        }

        var texts = command.TestResults.Select(path => (Path: path, Text: E2EFiles.ReadFile(path))).ToImmutableArray();   // impure
        var runs = texts.Select(file => file.Text.Bind(trx => RunReporting.ReadTestRuns(trx, file.Path))).CombineAll()
            .MapError(failures => failures[0]).Map(each => each.SelectMany(fileRuns => fileRuns).ToImmutableArray());   // pure
        if (runs is not Result<ImmutableArray<TestRun>, string>.Ok all)
        {
            return new Result<Unit, string>.Error(((Result<ImmutableArray<TestRun>, string>.Error)runs).Failure);
        }

        var report = RunReporting.WriteReport(all.Value);   // pure
        E2EFiles.ReportAll(report);   // impure
        return RunReporting.HasRunAny(all.Value)
            ? new Result<Unit, string>.Ok(new Unit.Value())
            : new Result<Unit, string>.Error("No scenario ran.");
    }

    /// <summary><c>&lt;out&gt;/scenarios.json</c> from the runs' records and the selection, then the summary.</summary>
    public static Result<Unit, string> WriteScenarios(ToolCommand.WriteScenarios command)
    {
        var texts = command.Records.Select(path => (Path: path, Text: E2EFiles.ReadFile(path))).ToImmutableArray();   // impure
        var read = texts.Select(file => file.Text.Bind(JsonReading.ReadRunRecord).MapError(failure => $"{file.Path}: {failure}"))
            .CombineAll()
            .MapError(failures => failures[0]);   // pure
        if (read is not Result<ImmutableArray<RunRecord>, string>.Ok records)
        {
            return new Result<Unit, string>.Error(((Result<ImmutableArray<RunRecord>, string>.Error)read).Failure);
        }

        var tsv = E2EFiles.ReadFile(command.Selection);   // impure
        if (tsv is not Result<string, string>.Ok selectionText)
        {
            return new Result<Unit, string>.Error(((Result<string, string>.Error)tsv).Failure);
        }

        var selection = ScenarioListing.ReadSelection(selectionText.Value);   // pure
        var json = ScenarioListing.WriteScenarios(records.Value, selection);   // pure
        var summary = ScenarioListing.WriteSummary(records.Value, selection);   // pure
        var written = E2EFiles.WriteFile(Path.Combine(command.Out, "scenarios.json"), json);   // impure
        return written.Map(done =>
        {
            E2EFiles.ReportAll(summary);   // impure
            return done;
        });
    }

    /// <summary>The files a scenarios.json lists, a path a line.</summary>
    public static Result<Unit, string> ListFiles(ToolCommand.ListFiles command)
    {
        var json = E2EFiles.ReadFile(command.Scenarios);   // impure
        var files = json.Bind(JsonReading.ReadListedFiles).MapError(failure => $"{command.Scenarios}: {failure}");   // pure
        return files.Map(lines =>
        {
            E2EFiles.ReportAll(lines);   // impure
            return (Unit)new Unit.Value();
        });
    }
}
