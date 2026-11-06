// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using BaconReader.E2E.Infrastructure.Vocabulary;
using JoanComasFdz.AssertingThat;

namespace BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;

/// <summary>
/// What a test checks of the accounts in the main screen's left menu, where "Show user avatars" puts each account's
/// avatar before its name: an object replacement character, "￼ ", that the avatar is drawn over.
/// </summary>
public static class LeftMenuAccountsSnapshotAssertions
{
    private const string Avatar = "￼ ";

    /// <summary>The menu's header shows "<paramref name="shown"/>" with the avatar of "<paramref name="user"/>" before the name.</summary>
    public static AssertingThat<LeftMenuAccountsSnapshot> ShowsWithAnAvatarBefore(this AssertingThat<LeftMenuAccountsSnapshot> that, string shown, string user)
    {
        var header = that.InstanceToAssert.Header;
        var expected = shown.Replace(user, Avatar + user, StringComparison.Ordinal);
        Assert.True(header == expected, $"The left menu shows \"{header}\", but \"{shown}\" with an avatar before \"{user}\" was expected.");
        return that;
    }

    /// <summary>The accounts list shows "<paramref name="shown"/>" with the avatar of "<paramref name="user"/>" before the name.</summary>
    public static AssertingThat<LeftMenuAccountsSnapshot> ShowsInTheAccountsListWithAnAvatarBefore(this AssertingThat<LeftMenuAccountsSnapshot> that, string shown, string user)
    {
        var rows = that.InstanceToAssert.AccountsList;
        var expected = shown.Replace(user, Avatar + user, StringComparison.Ordinal);
        Assert.True(rows.Contains(expected), $"The accounts list shows {Listed(rows)}, but \"{shown}\" with an avatar before \"{user}\" was expected.");
        return that;
    }

    /// <summary>The accounts list shows <paramref name="entry"/>, such as "Add account", with no avatar before it.</summary>
    public static AssertingThat<LeftMenuAccountsSnapshot> ShowsInTheAccountsListWithNoAvatar(this AssertingThat<LeftMenuAccountsSnapshot> that, MenuEntry entry)
    {
        var rows = that.InstanceToAssert.AccountsList;
        Assert.True(rows.Contains(entry.ToShownText()), $"The accounts list shows {Listed(rows)}, but \"{entry.ToShownText()}\" with no avatar was expected.");
        return that;
    }

    private static string Listed(IEnumerable<string> rows) =>
        rows.Any() ? string.Join(", ", rows.Select(row => $"\"{row}\"")) : "nothing";
}
