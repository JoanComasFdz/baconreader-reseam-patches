// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using System.Reflection;

namespace BaconReader.E2E.Infrastructure;

/// <summary>
/// Every scenario keeps the shape E2E-TEST-CODE-RULES.md (rule a) gives it, so it stays one to one with the Gherkin it
/// implements (runs without the emulator): a theory played on at least one screen, its Gherkin in
/// <see cref="TestDescriptionAttribute"/>, its name the Gherkin's title, its screen tags its attributes, and
/// patched with exactly the patches its Given names (<see cref="Vocabulary.PatchedWithAttribute"/>). A screen
/// attribute twice doesn't compile.
/// </summary>
public sealed class ScenarioShapeTests
{
    public static TheoryData<string> Scenarios() =>
        [.. FindScenarios().Select(method => $"{method.DeclaringType!.Name}.{method.Name}")];

    [Theory]
    [MemberData(nameof(Scenarios))]
    public void Scenario_IsPlayedOnAtLeastOneScreen(string scenario)
    {
        var method = Find(scenario);
        Assert.True(method.GetCustomAttribute<TheoryAttribute>() is not null, $"{scenario} should be a [Theory], played by its [PlayOnPhone].");
        Assert.True(method.GetCustomAttributes<PlayOnAttribute>().Any(), $"{scenario} has no [PlayOnPhone]: it would never be played.");
        Assert.True(
            method.GetParameters() is [{ ParameterType: var type }] && type == typeof(Screen),
            $"{scenario} should take one parameter, the Screen its attribute plays it on.");
    }

    [Theory]
    [MemberData(nameof(Scenarios))]
    public void Scenario_IsNamedAfterItsGherkinsTitle(string scenario)
    {
        var method = Find(scenario);
        var gherkin = ReadGherkin(method, scenario);
        Assert.True(gherkin.Title.Length > 0, $"{scenario}'s [TestDescription] has no \"Scenario:\" line.");
        Assert.Equal(ScenarioNaming.ToMethodName(gherkin.Title), method.Name);
    }

    [Theory]
    [MemberData(nameof(Scenarios))]
    public void Scenario_IsPlayedOnTheScreensItsGherkinTags(string scenario)
    {
        var method = Find(scenario);
        var gherkin = ReadGherkin(method, scenario);
        var played = method.GetCustomAttributes<PlayOnAttribute>().Select(play => play.Screen.ToTag()).Order();
        Assert.Equal(gherkin.Tags.Order(), played);
    }

    [Theory]
    [MemberData(nameof(Scenarios))]
    public void Scenario_IsPatchedWithThePatchesItsGivenNames(string scenario)
    {
        var method = Find(scenario);
        var gherkin = ReadGherkin(method, scenario);
        var patches = method.GetCustomAttribute<Vocabulary.PatchedWithAttribute>()?.Patches
            ?? throw new Xunit.Sdk.XunitException($"{scenario} has no [PatchedWith]: BaseE2E wouldn't know what to patch BaconReader with.");
        var given = gherkin.Text.Split('\n').FirstOrDefault(line => line.TrimStart().StartsWith("Given ", StringComparison.Ordinal)) ?? "";
        foreach (var patch in patches)
        {
            Assert.True(
                given.Contains($"\"{Vocabulary.PatchShowing.ToShownText(patch)}\"", StringComparison.Ordinal),
                $"{scenario} is patched with \"{Vocabulary.PatchShowing.ToShownText(patch)}\", but its Given doesn't name it: {given.Trim()}");
        }
    }

    private static Gherkin ReadGherkin(MethodInfo method, string scenario) =>
        method.GetCustomAttribute<TestDescriptionAttribute>() is { } description
            ? Gherkin.Read(description.Gherkin)
            : throw new Xunit.Sdk.XunitException($"{scenario} has no [TestDescription] with the Gherkin it implements.");

    private static MethodInfo Find(string scenario) =>
        FindScenarios().Single(method => $"{method.DeclaringType!.Name}.{method.Name}" == scenario);

    private static IEnumerable<MethodInfo> FindScenarios() =>
        typeof(BaseE2E).Assembly.GetTypes()
            .Where(type => type.IsSubclassOf(typeof(BaseE2E)) && !type.IsAbstract)
            .SelectMany(type => type.GetMethods(BindingFlags.Public | BindingFlags.Instance | BindingFlags.DeclaredOnly))
            .Where(method => method.GetCustomAttributes<FactAttribute>(inherit: true).Any())
            .OrderBy(method => method.DeclaringType!.Name)
            .ThenBy(method => method.Name);
}
