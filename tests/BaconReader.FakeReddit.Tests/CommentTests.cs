// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using System.Net;
using System.Text.Json;
using BaconReader.FakeReddit.Tests.Support;

namespace BaconReader.FakeReddit.Tests;

/// <summary>A post's comments: the post and its comment tree, Reddit's quirks included, and media uploaded in a comment.</summary>
public sealed class CommentTests(FakeRedditFixture fixture) : FakeRedditTest(fixture)
{
    [Theory]
    [InlineData("/comments/{0}/.json?sort=confidence")]
    [InlineData("/comments/{0}")]
    [InlineData("/r/e2e/comments/{0}/weekly_thread/.json")]
    public async Task A_posts_comments_are_its_listing_then_its_comment_tree(string path)
    {
        var post = World.AddPost("e2e", "Weekly thread");
        var top = World.AddComment(post, "Top comment");
        World.AddReply(top, "A reply");

        var page = await GetJsonAsync(string.Format(path, post.Id));

        Assert.Equal(2, page.GetArrayLength());
        Assert.Equal(["Weekly thread"], Titles(page[0]));
        var comment = page[1].GetProperty("data").GetProperty("children")[0];
        Assert.Equal("t1", comment.GetProperty("kind").GetString());
        Assert.Equal("Top comment", comment.GetProperty("data").GetProperty("body").GetString());
        Assert.Equal(0, comment.GetProperty("data").GetProperty("depth").GetInt32());
        Assert.Equal(post.FullName, comment.GetProperty("data").GetProperty("parent_id").GetString());
        var reply = comment.GetProperty("data").GetProperty("replies").GetProperty("data").GetProperty("children")[0].GetProperty("data");
        Assert.Equal("A reply", reply.GetProperty("body").GetString());
        Assert.Equal(1, reply.GetProperty("depth").GetInt32());
        Assert.Equal(top.FullName, reply.GetProperty("parent_id").GetString());
    }

    [Fact]
    public async Task A_comment_without_replies_has_an_empty_string_for_them_as_Reddit_sends()
    {
        var post = World.AddPost("e2e", "Thread");
        World.AddComment(post, "Alone");

        var page = await GetJsonAsync($"/comments/{post.Id}/.json");

        var replies = page[1].GetProperty("data").GetProperty("children")[0].GetProperty("data").GetProperty("replies");
        Assert.Equal(JsonValueKind.String, replies.ValueKind);
        Assert.Equal("", replies.GetString());
    }

    [Fact]
    public async Task A_comments_body_html_is_its_markdown_escaped_as_Reddit_does()
    {
        var post = World.AddPost("e2e", "Thread");
        World.AddComment(post, "Hello **world** & [a link](https://example.com)");

        var page = await GetJsonAsync($"/comments/{post.Id}/.json");

        var html = page[1].GetProperty("data").GetProperty("children")[0].GetProperty("data").GetProperty("body_html").GetString();
        Assert.Equal(
            "&lt;div class=\"md\"&gt;&lt;p&gt;Hello &lt;strong&gt;world&lt;/strong&gt; &amp;amp; &lt;a href=\"https://example.com\"&gt;a link&lt;/a&gt;&lt;/p&gt;\n&lt;/div&gt;",
            html);
    }

    [Fact]
    public async Task A_comment_with_a_gif_names_it_in_its_body_metadata_and_html_and_the_fake_serves_it()
    {
        var post = World.AddPost("e2e", "Weekly thread");
        var comment = World.AddComment(post, "", build => build.WithGif());
        var id = Assert.Single(comment.Media.Keys);

        var page = await GetJsonAsync($"/comments/{post.Id}/.json");

        var data = page[1].GetProperty("data").GetProperty("children")[0].GetProperty("data");
        Assert.Equal($"![gif]({id})", data.GetProperty("body").GetString());
        var metadata = data.GetProperty("media_metadata").GetProperty(id);
        Assert.Equal("valid", metadata.GetProperty("status").GetString());
        Assert.Equal("AnimatedImage", metadata.GetProperty("e").GetString());
        Assert.Equal("image/gif", metadata.GetProperty("m").GetString());
        var gif = metadata.GetProperty("s").GetProperty("gif").GetString()!;
        Assert.Equal($"{Server.Url}/_media/i.redd.it/{id}.gif", gif);
        Assert.Contains($"&lt;a href=\"{gif}\" target=\"_blank\"&gt;&lt;img src=\"{gif}\" width=\"240\" height=\"180\"&gt;&lt;/a&gt;", data.GetProperty("body_html").GetString());
        using var served = await SendAsync(HttpMethod.Get, new Uri(gif).PathAndQuery);
        Assert.Equal(HttpStatusCode.OK, served.StatusCode);
        Assert.Equal("image/gif", served.Content.Headers.ContentType!.MediaType);
    }

    [Fact]
    public async Task A_link_to_one_comment_shows_its_thread()
    {
        var post = World.AddPost("e2e", "Thread");
        World.AddComment(post, "Another");
        var wanted = World.AddComment(post, "Wanted");

        var page = await GetJsonAsync($"/r/e2e/comments/{post.Id}/thread/{wanted.Id}/.json");

        var only = Assert.Single(page[1].GetProperty("data").GetProperty("children").EnumerateArray());
        Assert.Equal("Wanted", only.GetProperty("data").GetProperty("body").GetString());
    }

    [Fact]
    public async Task An_unknown_post_answers_404()
    {
        using var response = await SendAsync(HttpMethod.Get, "/comments/zzzzz/.json", await ReaderAsync());

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }
}
