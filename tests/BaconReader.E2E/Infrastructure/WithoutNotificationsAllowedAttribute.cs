// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

namespace BaconReader.E2E.Infrastructure;

/// <summary>
/// The scenario's app starts as a fresh install on Android 14 does: without its notifications allowed, so BaconReader asks
/// for them when it opens ("Allow BaconReader Premium to send you notifications?"). Without it, BaseE2E allows them, and
/// Android never asks. Its Given says so in a line of its own: <see cref="InGiven"/> (<c>ScenarioShapeTests</c> checks
/// both ways).
/// </summary>
[AttributeUsage(AttributeTargets.Method, AllowMultiple = false)]
public sealed class WithoutNotificationsAllowedAttribute : Attribute
{
    /// <summary>The Given line that asks for it.</summary>
    public const string InGiven = "BaconReader isn't allowed to send notifications";
}
