// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using BaconReader.E2E.Infrastructure.Vocabulary;
using JoanComasFdz.AssertingThat;

namespace BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;

/// <summary>What a test checks on the left menu's accounts, under the user name.</summary>
public static class LeftMenuAccountsSnapshotAssertions
{
    /// <summary>
    /// The accounts show the test account's user name (<see cref="TestAccount"/>), then <paramref name="next"/>, and
    /// nothing else.
    /// </summary>
    public static AssertingThat<LeftMenuAccountsSnapshot> ShowsTheTestAccountsUserNameThen(this AssertingThat<LeftMenuAccountsSnapshot> that, MenuEntry next)
    {
        var rows = that.InstanceToAssert.Rows;
        Assert.True(
            rows.Length == 2 && rows[0].ToShownText() == TestAccount.UserName && rows[1] == next,
            $"The left menu's accounts show {Listed(rows)}, but \"{TestAccount.UserName}\", \"{next.ToShownText()}\" was expected.");
        return that;
    }

    /// <summary>The accounts don't show <paramref name="row"/>.</summary>
    public static AssertingThat<LeftMenuAccountsSnapshot> DoesNotShow(this AssertingThat<LeftMenuAccountsSnapshot> that, MenuEntry row)
    {
        var rows = that.InstanceToAssert.Rows;
        Assert.False(
            rows.Contains(row),
            $"The left menu's accounts show {Listed(rows)}, with \"{row.ToShownText()}\", which was expected gone.");
        return that;
    }

    private static string Listed(IEnumerable<MenuEntry> rows) =>
        rows.Any() ? string.Join(", ", rows.Select(row => $"\"{row.ToShownText()}\"")) : "nothing";
}
