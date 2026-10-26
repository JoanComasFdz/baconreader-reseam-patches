// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using JoanComasFdz.AssertingThat;

namespace BaconReader.E2E.Infrastructure.Phone.BaconReader.Screens;

/// <summary>What a test checks on a post's comments.</summary>
public static class CommentsSnapshotAssertions
{
    /// <summary>The post's one comment shows <paramref name="gifs"/> GIFs, as images, under its text.</summary>
    public static AssertingThat<CommentsSnapshot> TheCommentShowsGifs(this AssertingThat<CommentsSnapshot> that, int gifs)
    {
        var comments = that.InstanceToAssert.Comments;
        Assert.True(comments.Length == 1, $"The post shows {comments.Length} comments, but one was expected.");
        var comment = comments[0];
        Assert.True(
            comment.Gifs == gifs,
            $"The comment shows {comment.Gifs} GIFs (and {comment.Images} images), but {gifs} GIFs were expected.");
        return that;
    }
}
