package rimmods.setup

/** A Valve KeyValues (VDF) text document: quoted keys, quoted string values or `{ … }` blocks. */
sealed interface Vdf {
    data class Text(val value: String) : Vdf
    data class Block(val entries: List<Pair<String, Vdf>>) : Vdf {
        operator fun get(key: String): Vdf? = entries.firstOrNull { it.first.equals(key, ignoreCase = true) }?.second
    }

    companion object {
        /** Lenient: unquoted tokens and `//` comments are accepted, a broken tail ends the parse. */
        fun parse(text: String): Block {
            val tokens = tokenize(text)
            var i = 0
            fun block(): Block {
                val entries = mutableListOf<Pair<String, Vdf>>()
                while (i < tokens.size) {
                    val key = tokens[i++]
                    if (key == "}") break
                    if (key == "{" || i >= tokens.size) continue
                    val next = tokens[i++]
                    entries += key to if (next == "{") block() else Text(next)
                }
                return Block(entries)
            }
            return block()
        }

        private fun tokenize(text: String): List<String> {
            val out = mutableListOf<String>()
            var i = 0
            while (i < text.length) {
                val c = text[i]
                when {
                    c.isWhitespace() -> i++
                    c == '/' && text.startsWith("//", i) -> i = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                    c == '{' || c == '}' -> { out += c.toString(); i++ }
                    c == '"' -> {
                        val sb = StringBuilder()
                        i++
                        while (i < text.length && text[i] != '"') {
                            if (text[i] == '\\' && i + 1 < text.length) { sb.append(text[i + 1]); i += 2 } else sb.append(text[i++])
                        }
                        i++
                        out += sb.toString()
                    }
                    else -> {
                        val start = i
                        while (i < text.length && !text[i].isWhitespace() && text[i] != '{' && text[i] != '}' && text[i] != '"') i++
                        out += text.substring(start, i)
                    }
                }
            }
            return out
        }
    }
}
