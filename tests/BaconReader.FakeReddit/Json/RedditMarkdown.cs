// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

using System.Net;
using System.Text;
using System.Text.RegularExpressions;
using BaconReader.FakeReddit.World;

namespace BaconReader.FakeReddit.Json;

/// <summary>
/// Turns Markdown into the HTML Reddit sends in <c>body_html</c> and <c>selftext_html</c>, only as far as the scenarios
/// use it: paragraphs, quotes, lists, headings, code, links (also bare URLs and r/ u/ names), bold, italics,
/// strikethrough, inline code, and media uploaded in comments (<c>![gif](id)</c>). Reddit sends that HTML entity-escaped
/// (<c>&amp;lt;div class="md"&amp;gt;…</c>), and so does <see cref="ToEscapedHtml"/>.
/// </summary>
public static partial class RedditMarkdown
{
    /// <summary>Reddit's <c>body_html</c> for <paramref name="markdown"/>: <c>&lt;div class="md"&gt;…&lt;/div&gt;</c>, entity-escaped.</summary>
    public static string ToEscapedHtml(string markdown, string baseUrl, IReadOnlyDictionary<string, CommentMedia>? media = null) =>
        Escape($"<div class=\"md\">{ToHtml(markdown, baseUrl, media)}</div>");

    /// <summary>
    /// Reddit's <c>selftext_html</c> for a text post's <paramref name="markdown"/>: the same, inside Reddit's
    /// <c>&lt;!-- SC_OFF --&gt;</c> comments; null for an empty text, as Reddit sends it.
    /// </summary>
    public static string? ToEscapedSelfTextHtml(string? markdown, string baseUrl) =>
        string.IsNullOrEmpty(markdown)
            ? null
            : Escape($"<!-- SC_OFF --><div class=\"md\">{ToHtml(markdown, baseUrl, null)}</div><!-- SC_ON -->");

    /// <summary>The HTML inside Reddit's <c>&lt;div class="md"&gt;</c>, unescaped.</summary>
    public static string ToHtml(string markdown, string baseUrl, IReadOnlyDictionary<string, CommentMedia>? media)
    {
        var html = new StringBuilder();
        foreach (var block in Blocks().Split(markdown.Replace("\r\n", "\n").Trim('\n')).Where(block => block.Trim().Length > 0))
        {
            html.Append(ToBlock(block, baseUrl, media)).Append('\n');
        }

        return html.ToString();
    }

    // Reddit escapes &, < and > in its HTML fields, and leaves quotes alone.
    private static string Escape(string html) => html.Replace("&", "&amp;").Replace("<", "&lt;").Replace(">", "&gt;");

    private static string ToBlock(string block, string baseUrl, IReadOnlyDictionary<string, CommentMedia>? media)
    {
        var lines = block.Split('\n');
        string Inline(string text) => ToInline(text, baseUrl, media);

        if (lines.All(line => line.StartsWith("    ", StringComparison.Ordinal)))
        {
            return $"<pre><code>{WebUtility.HtmlEncode(string.Join('\n', lines.Select(line => line[4..])))}\n</code></pre>";
        }

        if (lines.All(line => line.StartsWith('>')))
        {
            var quoted = string.Join('\n', lines.Select(line => line.TrimStart('>').TrimStart()));
            return $"<blockquote>\n{ToHtml(quoted, baseUrl, media)}</blockquote>";
        }

        if (Heading().Match(block) is { Success: true } heading)
        {
            var level = heading.Groups[1].Length;
            return $"<h{level}>{Inline(heading.Groups[2].Value.Trim())}</h{level}>";
        }

        if (lines.All(line => Bullet().IsMatch(line)))
        {
            return "<ul>\n" + string.Concat(lines.Select(line => $"<li>{Inline(Bullet().Replace(line, ""))}</li>\n")) + "</ul>";
        }

        if (lines.All(line => Numbered().IsMatch(line)))
        {
            return "<ol>\n" + string.Concat(lines.Select(line => $"<li>{Inline(Numbered().Replace(line, ""))}</li>\n")) + "</ol>";
        }

        return $"<p>{Inline(block)}</p>";
    }

    private static string ToInline(string text, string baseUrl, IReadOnlyDictionary<string, CommentMedia>? media)
    {
        var html = new StringBuilder();
        var at = 0;
        foreach (Match match in InlineToken().Matches(text))
        {
            html.Append(WebUtility.HtmlEncode(text[at..match.Index]));
            html.Append(ToInlineToken(match, baseUrl, media));
            at = match.Index + match.Length;
        }

        html.Append(WebUtility.HtmlEncode(text[at..]));
        return html.ToString();
    }

    private static string ToInlineToken(Match match, string baseUrl, IReadOnlyDictionary<string, CommentMedia>? media)
    {
        string Inline(string inner) => ToInline(inner, baseUrl, media);
        var groups = match.Groups;
        if (groups["media"].Success)
        {
            // Media uploaded in a comment: the link is the file, around a preview of its size.
            return media is not null && media.TryGetValue(groups["media"].Value, out var found)
                ? $"<a href=\"{baseUrl}/_media/{found.Path}\" target=\"_blank\"><img src=\"{baseUrl}/_media/{found.Path}\" width=\"{found.Width}\" height=\"{found.Height}\"></a>"
                : WebUtility.HtmlEncode(match.Value);
        }

        if (groups["linkText"].Success)
        {
            return $"<a href=\"{WebUtility.HtmlEncode(groups["linkUrl"].Value)}\">{Inline(groups["linkText"].Value)}</a>";
        }

        if (groups["url"].Success)
        {
            var url = WebUtility.HtmlEncode(groups["url"].Value);
            return $"<a href=\"{url}\">{url}</a>";
        }

        if (groups["bold"].Success)
        {
            return $"<strong>{Inline(groups["bold"].Value)}</strong>";
        }

        if (groups["strike"].Success)
        {
            return $"<del>{Inline(groups["strike"].Value)}</del>";
        }

        if (groups["italic"].Success)
        {
            return $"<em>{Inline(groups["italic"].Value)}</em>";
        }

        if (groups["code"].Success)
        {
            return $"<code>{WebUtility.HtmlEncode(groups["code"].Value)}</code>";
        }

        // r/name and u/name, which Reddit links relative to itself.
        var name = groups["name"].Value;
        return $"<a href=\"/{name.TrimStart('/')}\">{name}</a>";
    }

    [GeneratedRegex(@"\n[ \t]*\n+")]
    private static partial Regex Blocks();

    [GeneratedRegex(@"^(#{1,6})\s+(.*)$", RegexOptions.Singleline)]
    private static partial Regex Heading();

    [GeneratedRegex(@"^[*+-]\s+")]
    private static partial Regex Bullet();

    [GeneratedRegex(@"^\d+\.\s+")]
    private static partial Regex Numbered();

    [GeneratedRegex(
        @"!\[(?:gif|img)\]\((?<media>[^)|\s]+)\)"
        + @"|\[(?<linkText>[^\]]+)\]\((?<linkUrl>[^)\s]+)\)"
        + @"|(?<![\w/])(?<url>https?://[^\s<>()]+)"
        + @"|\*\*(?<bold>.+?)\*\*"
        + @"|~~(?<strike>.+?)~~"
        + @"|(?<![\w*])\*(?<italic>[^*\s][^*]*?)\*"
        + @"|`(?<code>[^`]+)`"
        + @"|(?<![\w/])(?<name>/?[ru]/[A-Za-z0-9_]+)")]
    private static partial Regex InlineToken();
}
