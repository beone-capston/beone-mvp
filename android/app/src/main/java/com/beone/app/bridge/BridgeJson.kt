package com.beone.app.bridge

/**
 * 브리지 메시지 검사용 최소 JSON 해석기(RFC 8259 문법).
 * Android 프레임워크의 org.json은 로컬 단위 테스트에서 동작하지 않고, 새 의존성을 늘리지 않기 위해 직접 둔다.
 * 숫자는 원문 문자열([JsonNumber])로만 보관하고, 같은 객체 안의 중복 키는 형식 오류로 본다.
 */
internal object BridgeJson {

    /** JSON 숫자의 원문. 값 비교가 필요한 쪽에서 해석한다. */
    @JvmInline
    value class JsonNumber(val literal: String)

    /** JSON `null`. 키가 없는 경우와 구분하려고 따로 둔다. */
    object JsonNull

    /** 형식이 잘못된 JSON. 메시지에는 입력 내용을 넣지 않는다. */
    class MalformedJsonException(message: String) : Exception(message)

    /** 전체 문자열을 JSON 값 하나로 해석한다. 앞뒤 공백 외의 남은 문자가 있으면 형식 오류다. */
    fun parse(text: String): Any {
        val reader = Reader(text)
        reader.skipWhitespace()
        val value = reader.readValue()
        reader.skipWhitespace()
        if (!reader.atEnd()) throw MalformedJsonException("값 뒤에 남은 문자가 있음")
        return value
    }

    private class Reader(private val text: String) {
        private var index = 0

        fun atEnd(): Boolean = index >= text.length

        fun skipWhitespace() {
            while (index < text.length && text[index] in WHITESPACE) index++
        }

        fun readValue(): Any {
            if (atEnd()) throw MalformedJsonException("값이 없음")
            return when (text[index]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", JsonNull)
                else -> readNumber()
            }
        }

        private fun readObject(): Map<String, Any> {
            index++ // '{'
            val result = LinkedHashMap<String, Any>()
            skipWhitespace()
            if (peek() == '}') {
                index++
                return result
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') throw MalformedJsonException("객체 키는 문자열이어야 함")
                val key = readString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                val value = readValue()
                if (result.put(key, value) != null) throw MalformedJsonException("중복 키")
                skipWhitespace()
                when (next()) {
                    ',' -> continue
                    '}' -> return result
                    else -> throw MalformedJsonException("객체 구분자 오류")
                }
            }
        }

        private fun readArray(): List<Any> {
            index++ // '['
            val result = ArrayList<Any>()
            skipWhitespace()
            if (peek() == ']') {
                index++
                return result
            }
            while (true) {
                skipWhitespace()
                result.add(readValue())
                skipWhitespace()
                when (next()) {
                    ',' -> continue
                    ']' -> return result
                    else -> throw MalformedJsonException("배열 구분자 오류")
                }
            }
        }

        private fun readString(): String {
            index++ // '"'
            val builder = StringBuilder()
            while (true) {
                val c = next() ?: throw MalformedJsonException("문자열이 닫히지 않음")
                when {
                    c == '"' -> return builder.toString()
                    c == '\\' -> builder.append(readEscape())
                    c < ' ' -> throw MalformedJsonException("문자열 안의 제어 문자")
                    else -> builder.append(c)
                }
            }
        }

        private fun readEscape(): Char = when (next()) {
            '"' -> '"'
            '\\' -> '\\'
            '/' -> '/'
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> readUnicodeEscape()
            else -> throw MalformedJsonException("알 수 없는 이스케이프")
        }

        private fun readUnicodeEscape(): Char {
            if (index + 4 > text.length) throw MalformedJsonException("\\u 뒤 16진수 부족")
            var code = 0
            repeat(4) {
                val digit = Character.digit(text[index++], 16)
                if (digit < 0) throw MalformedJsonException("\\u 뒤 16진수 오류")
                code = code * 16 + digit
            }
            return code.toChar()
        }

        private fun readNumber(): JsonNumber {
            val start = index
            if (peek() == '-') index++
            when {
                peek() == '0' -> index++
                peek() in '1'..'9' -> skipDigits()
                else -> throw MalformedJsonException("알 수 없는 값")
            }
            if (peek() == '.') {
                index++
                requireDigit()
                skipDigits()
            }
            if (peek() == 'e' || peek() == 'E') {
                index++
                if (peek() == '+' || peek() == '-') index++
                requireDigit()
                skipDigits()
            }
            return JsonNumber(text.substring(start, index))
        }

        private fun requireDigit() {
            if (peek() !in '0'..'9') throw MalformedJsonException("숫자 형식 오류")
        }

        private fun skipDigits() {
            while (peek() in '0'..'9') index++
        }

        private fun readLiteral(literal: String, value: Any): Any {
            if (!text.startsWith(literal, index)) throw MalformedJsonException("알 수 없는 값")
            index += literal.length
            return value
        }

        private fun expect(c: Char) {
            if (next() != c) throw MalformedJsonException("구분자 오류")
        }

        private fun peek(): Char? = if (index < text.length) text[index] else null

        private fun next(): Char? = if (index < text.length) text[index++] else null
    }

    private val WHITESPACE = charArrayOf(' ', '\t', '\n', '\r')
}
