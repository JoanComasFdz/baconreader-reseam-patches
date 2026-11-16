// SPDX-FileCopyrightText: 2026 JoanComasFdz
// SPDX-License-Identifier: GPL-3.0-only

package com.joancomasfdz.baconreader.settingsbackup

/** A line of a settings file that can't be read, numbered from 1. */
class SettingsYamlException(val line: Int, message: String) : Exception(message)

/**
 * The YAML of a settings file: comments, top-level scalars, and maps of one level under a top-level
 * key. Values are what SharedPreferences holds: booleans and ints as YAML writes them, longs tagged
 * `!long`, floats tagged `!float`, strings double-quoted and string sets tagged `!set` as a flow
 * sequence. Reading also takes plain and single-quoted strings, untagged numbers and untagged flow
 * sequences, for files edited by hand.
 */
object SettingsYaml {
    private val PLAIN_KEY = Regex("[A-Za-z_][A-Za-z0-9_.\\-]*")
    private val INTEGER = Regex("[-+]?[0-9]+")
    private val DECIMAL =
        Regex("[-+]?([0-9]+\\.[0-9]*|\\.[0-9]+)([eE][-+]?[0-9]+)?|[-+]?[0-9]+[eE][-+]?[0-9]+")
    private val RESERVED = setOf("true", "false", "yes", "no", "on", "off", "null", "y", "n")

    /** The file: [comments] first, then [scalars], then each map of [maps]. */
    fun write(
        comments: List<String>,
        scalars: Map<String, Any>,
        maps: Map<String, Map<String, Any>>,
    ): String {
        val out = StringBuilder()
        comments.forEach { out.append("# ").append(it).append('\n') }
        scalars.forEach { (key, value) ->
            out.append(key(key)).append(": ").append(value(value)).append('\n')
        }
        maps.forEach { (name, map) ->
            out.append(key(name)).append(':')
            if (map.isEmpty()) out.append(" {}")
            out.append('\n')
            map.forEach { (key, value) ->
                out.append("  ").append(key(key)).append(": ").append(value(value)).append('\n')
            }
        }
        return out.toString()
    }

    /** The top-level scalars and maps of [text]. An empty map reads as an empty map. */
    fun read(text: String): Pair<Map<String, Any>, Map<String, Map<String, Any>>> {
        val scalars = LinkedHashMap<String, Any>()
        val maps = LinkedHashMap<String, MutableMap<String, Any>>()
        var current: MutableMap<String, Any>? = null
        text.removePrefix("\uFEFF").lines().forEachIndexed { index, raw ->
            val number = index + 1
            val line = raw.trimEnd()
            val content = line.trimStart()
            if (content.isEmpty() || content.startsWith("#")) return@forEachIndexed
            if (content == "---" || content == "...") return@forEachIndexed
            val indent = line.length - content.length
            if (line.substring(0, indent).contains('\t')) {
                throw SettingsYamlException(number, "tabs can't indent YAML")
            }
            val parser = Parser(content, number)
            val key = parser.key()
            if (indent == 0) {
                current = null
                if (parser.atEnd()) {
                    current = LinkedHashMap<String, Any>().also { maps[key] = it }
                } else if (parser.rest() == "{}") {
                    maps[key] = LinkedHashMap()
                } else {
                    parser.value()?.let { scalars[key] = it }
                }
            } else {
                val map =
                    current ?: throw SettingsYamlException(number, "indented line outside a map")
                if (parser.atEnd()) throw SettingsYamlException(number, "$key has no value")
                parser.value()?.let { map[key] = it }
            }
        }
        return scalars to maps
    }

    private fun key(key: String): String =
        if (PLAIN_KEY.matches(key) && key.lowercase() !in RESERVED) key else quote(key)

    private fun value(value: Any): String =
        when (value) {
            is Boolean,
            is Int -> value.toString()
            is Long -> "!long $value"
            is Float -> "!float $value"
            is Set<*> ->
                value
                    .map { it.toString() }
                    .sorted()
                    .joinToString(", ", prefix = "!set [", postfix = "]") { quote(it) }
            else -> quote(value.toString())
        }

    private fun quote(text: String): String {
        val out = StringBuilder("\"")
        for (char in text) {
            when {
                char == '"' -> out.append("\\\"")
                char == '\\' -> out.append("\\\\")
                char == '\n' -> out.append("\\n")
                char == '\r' -> out.append("\\r")
                char == '\t' -> out.append("\\t")
                char < ' ' ||
                    char in '\u007f'..'\u009f' ||
                    char == '\u2028' ||
                    char == '\u2029' ||
                    char == '\uFEFF' -> out.append(String.format("\\u%04x", char.code))
                else -> out.append(char)
            }
        }
        return out.append('"').toString()
    }

    private class Parser(private val text: String, private val line: Int) {
        private var at = 0

        fun atEnd(): Boolean {
            skipSpaces()
            return at >= text.length || text[at] == '#'
        }

        fun rest(): String = text.substring(at).substringBefore(" #").trim()

        fun key(): String {
            val key =
                when (peek()) {
                    '"' -> doubleQuoted()
                    '\'' -> singleQuoted()
                    else -> {
                        val start = at
                        while (at < text.length && !(text[at] == ':' && isSpaceOrEnd(at + 1))) at++
                        text.substring(start, at).trim()
                    }
                }
            skipSpaces()
            if (peek() != ':') fail("a key must end with \":\"")
            at++
            if (!isSpaceOrEnd(at)) fail("\":\" must be followed by a space")
            if (key.isEmpty()) fail("empty key")
            return key
        }

        /** The value, or null for `null` and `~`. */
        fun value(): Any? {
            skipSpaces()
            val value: Any? =
                when {
                    text.startsWith("!long ", at) -> {
                        at += 6
                        plain().let { it.toLongOrNull() ?: fail("not a whole number: $it") }
                    }
                    text.startsWith("!float ", at) -> {
                        at += 7
                        plain().let { it.toFloatOrNull() ?: fail("not a number: $it") }
                    }
                    text.startsWith("!set ", at) -> {
                        at += 5
                        skipSpaces()
                        sequence()
                    }
                    peek() == '[' -> sequence()
                    peek() == '"' -> doubleQuoted()
                    peek() == '\'' -> singleQuoted()
                    peek() == '!' -> fail("unknown tag: ${plain()}")
                    else -> {
                        val plain = plain()
                        when {
                            plain == "~" || plain.equals("null", true) -> null
                            plain.equals("true", true) -> true
                            plain.equals("false", true) -> false
                            INTEGER.matches(plain) ->
                                plain.toIntOrNull() ?: plain.toLongOrNull() ?: plain
                            DECIMAL.matches(plain) -> plain.toFloat()
                            else -> plain
                        }
                    }
                }
            if (!atEnd()) fail("unexpected text after the value: ${text.substring(at)}")
            return value
        }

        private fun sequence(): Set<String> {
            if (peek() != '[') fail("!set needs a [ ] list")
            at++
            val items = LinkedHashSet<String>()
            skipSpaces()
            if (peek() == ']') {
                at++
                return items
            }
            while (true) {
                skipSpaces()
                val item =
                    when (peek()) {
                        '"' -> doubleQuoted()
                        '\'' -> singleQuoted()
                        else -> {
                            val start = at
                            while (at < text.length && text[at] != ',' && text[at] != ']') at++
                            text.substring(start, at).trim()
                        }
                    }
                items.add(item)
                skipSpaces()
                when (peek()) {
                    ',' -> at++
                    ']' -> {
                        at++
                        return items
                    }
                    else -> fail("a list item must be followed by \",\" or \"]\"")
                }
            }
        }

        private fun plain(): String {
            val start = at
            while (at < text.length && !(text[at] == '#' && at > start && text[at - 1] == ' ')) at++
            return text.substring(start, at).trim()
        }

        private fun doubleQuoted(): String {
            at++
            val out = StringBuilder()
            while (true) {
                if (at >= text.length) fail("a string has no closing \"")
                val char = text[at++]
                when (char) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (at >= text.length) fail("a string has no closing \"")
                        when (val escape = text[at++]) {
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            '0' -> out.append('\u0000')
                            '"',
                            '\\',
                            '/',
                            ' ' -> out.append(escape)
                            'x' -> out.appendCodePoint(hex(2))
                            'u' -> out.appendCodePoint(hex(4))
                            'U' -> out.appendCodePoint(hex(8))
                            else -> fail("unknown escape: \\$escape")
                        }
                    }
                    else -> out.append(char)
                }
            }
        }

        private fun hex(digits: Int): Int {
            if (at + digits > text.length) fail("an escape needs $digits hex digits")
            val code =
                text.substring(at, at + digits).toIntOrNull(16)?.takeIf {
                    Character.isValidCodePoint(it)
                } ?: fail("an escape needs hex digits")
            at += digits
            return code
        }

        private fun singleQuoted(): String {
            at++
            val out = StringBuilder()
            while (true) {
                if (at >= text.length) fail("a string has no closing '")
                val char = text[at++]
                if (char == '\'') {
                    if (peek() == '\'') {
                        out.append('\'')
                        at++
                    } else {
                        return out.toString()
                    }
                } else {
                    out.append(char)
                }
            }
        }

        private fun peek(): Char? = text.getOrNull(at)

        private fun isSpaceOrEnd(index: Int) = index >= text.length || text[index] == ' '

        private fun skipSpaces() {
            while (at < text.length && text[at] == ' ') at++
        }

        private fun fail(message: String): Nothing = throw SettingsYamlException(line, message)
    }
}
