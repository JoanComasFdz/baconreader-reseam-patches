// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using System.Collections.Immutable;

namespace BaconReader.FakeReddit.World;

/// <summary>A request the fake received: what it was, whose it was (null logged out) and what it answered.</summary>
/// <param name="Path">The path as the app sent it, before <c>.json</c> and a trailing slash were dropped.</param>
public sealed record RecordedRequest(
    DateTimeOffset At,
    string Method,
    string Path,
    string Query,
    string? Account,
    string? UserAgent,
    int Status);

/// <summary>An action the app sent (a vote, a save, a comment…): its name, whose it was and its form fields.</summary>
/// <param name="Name">The endpoint's name: <c>vote</c> for <c>POST /api/vote</c>.</param>
public sealed record RecordedAction(DateTimeOffset At, string Name, string? Account, ImmutableDictionary<string, string> Form);

/// <summary>
/// What the login saw of the app logging in: the client ID and redirect URI it asked the login page for, and its
/// User-Agent ("Spoof client" sends RedReader's).
/// </summary>
public sealed record LoginSeen(string Step, string? ClientId, string? RedirectUri, string? UserAgent);

/// <summary>How the fake login page looks, which a scenario can change.</summary>
public sealed class LoginPageOptions
{
    /// <summary>Shows Reddit's cookie banner first, "Let us know your cookie preferences".</summary>
    public bool ShowCookieBanner { get; set; }
}
