// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using BaconReader.FakeReddit.World;

namespace BaconReader.E2E.Infrastructure.Reddit;

/// <summary>
/// X and Bluesky, which the fake Reddit answers too ("E2E fake Reddit" moves X's embed service and Bluesky's public API
/// to it), in the Gherkins' words. Comments link a post at the service's own address, which the patch knows it by.
/// Every post is made on 2 October 2025, so the patch shows its date as "Oct 2, 2025" in any later year.
/// </summary>
public sealed partial class Reddit
{
    private static readonly DateTimeOffset SocialPostDate = new(2025, 10, 2, 15, 22, 58, TimeSpan.Zero);

    /// <summary>"X has a post by "&lt;name&gt;" @&lt;handle&gt; saying "&lt;text&gt;"", with <paramref name="media"/> under it; a <paramref name="long"/> one X's embed service cuts.</summary>
    public void XHasAPost(string name, string handle, string text, XMedia media = XMedia.None, bool @long = false) =>
        world.X.AddPost(name, handle.TrimStart('@'), text, SocialPostDate, media, @long);

    /// <summary>"Bluesky has a post by "&lt;name&gt;" @&lt;handle&gt; saying "&lt;text&gt;"", with <paramref name="embed"/> under it.</summary>
    public void BlueskyHasAPost(string name, string handle, string text, BlueskyEmbed embed = BlueskyEmbed.None) =>
        world.Bluesky.AddPost(name, handle.TrimStart('@'), text, SocialPostDate, embed);
}
