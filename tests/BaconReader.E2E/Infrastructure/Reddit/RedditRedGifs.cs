// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using BaconReader.FakeReddit.World;

namespace BaconReader.E2E.Infrastructure.Reddit;

/// <summary>
/// RedGIFs, which the fake Reddit answers too ("E2E fake Reddit" moves RedGIFs' API to it), in the Gherkins' words.
/// Posts link a video at RedGIFs' own address, <c>https://www.redgifs.com/watch/&lt;id&gt;</c>, with no thumbnail of
/// Reddit's: BaconReader and "Fix list thumbnails" know a RedGIFs video by that address and ask RedGIFs for it.
/// </summary>
public sealed partial class Reddit
{
    /// <summary>"Reddit has a RedGIFs post "&lt;title&gt;" in r/&lt;subreddit&gt;": a link post to a video RedGIFs has.</summary>
    public void HasARedGifsPost(string title, string subreddit) =>
        world.AddPost(subreddit, title, post => post.LinkingTo(world.RedGifs.AddGif().Link));

    /// <summary>"Reddit has a RedGIFs post "&lt;title&gt;" in r/&lt;subreddit&gt; whose video was deleted": RedGIFs answers it 410.</summary>
    public void HasARedGifsPostWhoseVideoWasDeleted(string title, string subreddit) =>
        world.AddPost(subreddit, title, post => post.LinkingTo(world.RedGifs.AddGif(RedGifState.Deleted).Link));

    /// <summary>"Reddit has a RedGIFs post "&lt;title&gt;" in r/&lt;subreddit&gt; whose video RedGIFs never had": RedGIFs answers it 404.</summary>
    public void HasARedGifsPostWhoseVideoRedGifsNeverHad(string title, string subreddit) =>
        world.AddPost(subreddit, title, post => post.LinkingTo("https://www.redgifs.com/watch/neverthereatall"));

    /// <summary>
    /// "RedGIFs no longer takes the tokens it gave", as after the phone's address changed: each is refused, 401
    /// WrongSender, until BaconReader asks for a new one.
    /// </summary>
    public void RedGifsNoLongerTakesTheTokensItGave() => world.RedGifs.ForgetTokens();
}
