// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using BaconReader.FakeReddit.Json;

namespace BaconReader.FakeReddit.Tests;

/// <summary>The Markdown the scenarios write, as the HTML Reddit renders it.</summary>
public sealed class RedditMarkdownTests
{
    private const string Base = "http://127.0.0.1:5080";

    [Theory]
    [InlineData("Hello", "<p>Hello</p>\n")]
    [InlineData("One\n\nTwo", "<p>One</p>\n<p>Two</p>\n")]
    [InlineData("**bold** *italic* ~~gone~~ `code`", "<p><strong>bold</strong> <em>italic</em> <del>gone</del> <code>code</code></p>\n")]
    [InlineData("[text](https://example.com/a?b=1&c=2)", "<p><a href=\"https://example.com/a?b=1&amp;c=2\">text</a></p>\n")]
    [InlineData("See https://example.com/x", "<p>See <a href=\"https://example.com/x\">https://example.com/x</a></p>\n")]
    [InlineData("Ask r/e2e or u/someone", "<p>Ask <a href=\"/r/e2e\">r/e2e</a> or <a href=\"/u/someone\">u/someone</a></p>\n")]
    [InlineData("> quoted", "<blockquote>\n<p>quoted</p>\n</blockquote>\n")]
    [InlineData("* one\n* two", "<ul>\n<li>one</li>\n<li>two</li>\n</ul>\n")]
    [InlineData("1. one\n2. two", "<ol>\n<li>one</li>\n<li>two</li>\n</ol>\n")]
    [InlineData("## Heading", "<h2>Heading</h2>\n")]
    [InlineData("I'm <here> & \"there\"", "<p>I&#39;m &lt;here&gt; &amp; &quot;there&quot;</p>\n")]
    public void Markdown_renders_as_Reddits_html(string markdown, string html) =>
        Assert.Equal(html, RedditMarkdown.ToHtml(markdown, Base, null));

    [Fact]
    public void The_html_field_is_wrapped_in_Reddits_div_and_entity_escaped() =>
        Assert.Equal("&lt;div class=\"md\"&gt;&lt;p&gt;a &amp;amp; b&lt;/p&gt;\n&lt;/div&gt;", RedditMarkdown.ToEscapedHtml("a & b", Base));

    [Fact]
    public void A_text_posts_html_is_null_without_text() => Assert.Null(RedditMarkdown.ToEscapedSelfTextHtml("", Base));

    [Fact]
    public void Media_the_comment_doesnt_hold_stays_as_written() =>
        Assert.Equal("<p>![gif](unknown)</p>\n", RedditMarkdown.ToHtml("![gif](unknown)", Base, null));
}
