package com.beone.app.bridge

import com.beone.app.bridge.BridgeErrorCode.INVALID_MESSAGE
import com.beone.app.bridge.BridgeErrorCode.UNKNOWN_TYPE
import com.beone.app.bridge.BridgeErrorCode.UNSUPPORTED_VERSION
import com.beone.app.bridge.BridgeMessageParser.Result.Accepted
import com.beone.app.bridge.BridgeMessageParser.Result.Rejected
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 계약 3절의 검사 순서(크기 → JSON → v → type)와 오류 코드. 기대값은 계약 문서에서 정했다. */
class BridgeMessageParserTest {

    @Test
    fun acceptsThreeRequestTypes() {
        assertEquals(Accepted(BridgeRequest.AUTH_REQUEST), parse("""{"v":1,"type":"auth.request"}"""))
        assertEquals(Accepted(BridgeRequest.STATE_GET), parse("""{"v":1,"type":"state.get"}"""))
        assertEquals(Accepted(BridgeRequest.PAYMENT_CHECK), parse("""{"v":1,"type":"payment.check"}"""))
    }

    @Test
    fun longestNormalMessageIsThirtyUnits() {
        // 계약: 현재 가장 긴 정상 메시지는 30.
        assertEquals(30, """{"v":1,"type":"payment.check"}""".length)
    }

    @Test
    fun acceptsWhitespaceAndKeyOrder() {
        assertEquals(Accepted(BridgeRequest.STATE_GET), parse(" {\n\t\"type\" : \"state.get\" , \"v\" : 1 }\r\n"))
    }

    @Test
    fun ignoresExtraFields() {
        assertEquals(Accepted(BridgeRequest.STATE_GET), parse("""{"v":1,"type":"state.get","x":[1,{"y":null}]}"""))
    }

    @Test
    fun unknownTypeWithVersionOne() {
        assertEquals(Rejected(UNKNOWN_TYPE), parse("""{"v":1,"type":"unknown"}"""))
        // 네이티브 → 웹 type을 웹이 보내도 웹 → 네이티브 type이 아니다.
        assertEquals(Rejected(UNKNOWN_TYPE), parse("""{"v":1,"type":"state"}"""))
        assertEquals(Rejected(UNKNOWN_TYPE), parse("""{"v":1,"type":"error"}"""))
        assertEquals(Rejected(UNKNOWN_TYPE), parse("""{"v":1,"type":"AUTH.REQUEST"}"""))
        assertEquals(Rejected(UNKNOWN_TYPE), parse("""{"v":1,"type":""}"""))
    }

    @Test
    fun unsupportedVersionWhenVersionIsNotNumberOne() {
        assertEquals(Rejected(UNSUPPORTED_VERSION), parse("""{"v":2,"type":"state.get"}"""))
        assertEquals(Rejected(UNSUPPORTED_VERSION), parse("""{"v":0,"type":"state.get"}"""))
        assertEquals(Rejected(UNSUPPORTED_VERSION), parse("""{"v":-1,"type":"state.get"}"""))
        assertEquals(Rejected(UNSUPPORTED_VERSION), parse("""{"v":1.5,"type":"state.get"}"""))
        assertEquals(Rejected(UNSUPPORTED_VERSION), parse("""{"v":"1","type":"state.get"}"""))
        assertEquals(Rejected(UNSUPPORTED_VERSION), parse("""{"v":true,"type":"state.get"}"""))
        assertEquals(Rejected(UNSUPPORTED_VERSION), parse("""{"v":null,"type":"state.get"}"""))
        assertEquals(Rejected(UNSUPPORTED_VERSION), parse("""{"v":[1],"type":"state.get"}"""))
        assertEquals(Rejected(UNSUPPORTED_VERSION), parse("""{"v":1e999999999999,"type":"state.get"}"""))
    }

    @Test
    fun numericallyEqualVersionOneIsAccepted() {
        // JSON은 1과 1.0을 같은 숫자로 본다(구현 선택, 진행 기록에 기록).
        assertEquals(Accepted(BridgeRequest.STATE_GET), parse("""{"v":1.0,"type":"state.get"}"""))
        assertEquals(Accepted(BridgeRequest.STATE_GET), parse("""{"v":1e0,"type":"state.get"}"""))
    }

    @Test
    fun missingVersionIsInvalidMessage() {
        assertEquals(Rejected(INVALID_MESSAGE), parse("""{"type":"state.get"}"""))
    }

    @Test
    fun missingOrNonStringTypeIsInvalidMessage() {
        assertEquals(Rejected(INVALID_MESSAGE), parse("""{"v":1}"""))
        assertEquals(Rejected(INVALID_MESSAGE), parse("""{"v":1,"type":null}"""))
        assertEquals(Rejected(INVALID_MESSAGE), parse("""{"v":1,"type":1}"""))
        assertEquals(Rejected(INVALID_MESSAGE), parse("""{"v":1,"type":["state.get"]}"""))
    }

    @Test
    fun malformedJsonIsInvalidMessage() {
        val malformed = listOf(
            "",
            "   ",
            "hello",
            "{",
            """{"v":1,"type":"state.get"""",
            """{"v":1,"type":"state.get",}""",
            """{"v":1,"type":"state.get"} x""",
            """{"v":1,"type":"state.get"}{}""",
            """{v:1,"type":"state.get"}""",
            """{'v':1,'type':'state.get'}""",
            """{"v":01,"type":"state.get"}""",
            """{"v":1.,"type":"state.get"}""",
            """{"v":+1,"type":"state.get"}""",
            """{"v":1,"type":"state\x"}""",
            "{\"v\":1,\"type\":\"state\u0001.get\"}",
            """{"v":1,"type":"state.get","v":1}""",
            """{"v":1,"type":TRUE}""",
        )
        malformed.forEach { assertEquals(it, Rejected(INVALID_MESSAGE), parse(it)) }
    }

    @Test
    fun nonObjectRootIsInvalidMessage() {
        listOf("[]", """[{"v":1,"type":"state.get"}]""", "1", "\"state.get\"", "null", "true").forEach {
            assertEquals(it, Rejected(INVALID_MESSAGE), parse(it))
        }
    }

    @Test
    fun nonStringMessageIsInvalidMessage() {
        assertEquals(Rejected(INVALID_MESSAGE), BridgeMessageParser.parse(null))
    }

    @Test
    fun escapedTypeIsDecodedBeforeComparison() {
        assertEquals(Accepted(BridgeRequest.STATE_GET), parse("""{"v":1,"type":"state.get"}"""))
    }

    // 검사 순서: 오류가 겹치면 앞 단계가 우선한다.

    @Test
    fun versionIsCheckedBeforeType() {
        // 계약 예시
        assertEquals(Rejected(INVALID_MESSAGE), parse("""{"type":"unknown"}"""))
        assertEquals(Rejected(UNSUPPORTED_VERSION), parse("""{"v":2,"type":"unknown"}"""))
        assertEquals(Rejected(UNSUPPORTED_VERSION), parse("""{"v":2}"""))
    }

    @Test
    fun jsonIsCheckedBeforeVersion() {
        assertEquals(Rejected(INVALID_MESSAGE), parse("""{"v":2,"type":"unknown",}"""))
    }

    @Test
    fun sizeIsCheckedBeforeJsonVersionAndType() {
        // 크기를 넘으면 v=2·모르는 type이어도 JSON을 해석하지 않고 INVALID_MESSAGE다.
        assertEquals(Rejected(INVALID_MESSAGE), parse(padded(4097, 'a', version = 2, type = "unknown")))
        assertEquals(Rejected(INVALID_MESSAGE), parse(padded(4097, 'a', type = "unknown")))
        // 상한 안이면 같은 메시지가 뒤 단계의 코드를 받는다.
        assertEquals(Rejected(UNSUPPORTED_VERSION), parse(padded(4096, 'a', version = 2, type = "unknown")))
        assertEquals(Rejected(UNKNOWN_TYPE), parse(padded(4096, 'a', type = "unknown")))
    }

    // 크기 경계: Kotlin String.length(UTF-16 코드 단위) 4096 허용, 4097 거부.

    @Test
    fun asciiBoundary() {
        val atLimit = padded(4096, 'a')
        val overLimit = padded(4097, 'a')
        assertEquals(4096, atLimit.length)
        assertEquals(4097, overLimit.length)
        assertEquals(Accepted(BridgeRequest.STATE_GET), parse(atLimit))
        assertEquals(Rejected(INVALID_MESSAGE), parse(overLimit))
    }

    @Test
    fun hangulCountsOneUnitEach() {
        val atLimit = padded(4096, '가')
        val overLimit = padded(4097, '가')
        assertEquals(4096, atLimit.length)
        // UTF-8 바이트로는 상한을 크게 넘지만 UTF-16 코드 단위로 센다.
        assertTrue(atLimit.toByteArray(Charsets.UTF_8).size > 4096)
        assertEquals(Accepted(BridgeRequest.STATE_GET), parse(atLimit))
        assertEquals(Rejected(INVALID_MESSAGE), parse(overLimit))
    }

    @Test
    fun supplementaryCharacterCountsTwoUnits() {
        val atLimit = padded(4096, EMOJI)
        val overLimit = padded(4097, EMOJI)
        assertEquals(4096, atLimit.length)
        assertEquals(4097, overLimit.length)
        // 보이는 글자(코드 포인트) 수는 상한보다 훨씬 작아도 코드 단위로 센다.
        assertTrue(overLimit.codePointCount(0, overLimit.length) < 4096)
        assertEquals(Accepted(BridgeRequest.STATE_GET), parse(atLimit))
        assertEquals(Rejected(INVALID_MESSAGE), parse(overLimit))
    }

    private fun parse(message: String) = BridgeMessageParser.parse(message)

    private companion object {
        const val EMOJI = "😀" // U+1F600

        /** `pad` 필드를 채워 전체 길이를 [total]로 맞춘다. 채울 문자가 2단위면 남는 1단위는 ASCII로 채운다. */
        fun padded(total: Int, fill: Char, version: Int = 1, type: String = "state.get"): String =
            padded(total, fill.toString(), version, type)

        fun padded(total: Int, fill: String, version: Int = 1, type: String = "state.get"): String {
            val head = """{"v":$version,"type":"$type","pad":""""
            val tail = "\"}"
            val room = total - head.length - tail.length
            val pad = fill.repeat(room / fill.length) + "a".repeat(room % fill.length)
            return (head + pad + tail).also { check(it.length == total) }
        }
    }
}
