package com.beone.app.bridge

import com.beone.app.bridge.BridgeMessageHandler.Outcome
import com.beone.app.web.WebOrigin
import org.junit.Assert.assertEquals
import org.junit.Test

/** 검사 순서 1단계(신뢰 origin·최상위 프레임)와 메시지별 응답. 기대 문자열은 계약 3절 예시 형식에서 정했다. */
class BridgeMessageHandlerTest {

    private val port = FakePort()
    private val handler = BridgeMessageHandler(setOf(WebOrigin("https", "beonepay.site", 443)), port)

    @Test
    fun untrustedOriginsAreIgnoredWithoutAnyResponse() {
        val untrusted = listOf(
            "https://www.beonepay.site",
            "http://beonepay.site",
            "https://beonepay.site:8443",
            "https://beonepay.site.evil.example",
            "https://evil.example",
            "http://localhost:5173",
            "null",
            "",
            "file://",
        )
        untrusted.forEach { origin ->
            ALL_KINDS.forEach { message ->
                assertEquals("$origin $message", Outcome.Ignored, handler.handle(message, origin, isMainFrame = true))
            }
        }
        assertEquals(0, port.authRequests)
        assertEquals(0, port.stateReads)
    }

    @Test
    fun subframeOfTrustedOriginIsIgnoredWithoutAnyResponse() {
        ALL_KINDS.forEach { message ->
            assertEquals(message, Outcome.Ignored, handler.handle(message, TRUSTED, isMainFrame = false))
        }
        assertEquals(0, port.authRequests)
        assertEquals(0, port.stateReads)
    }

    @Test
    fun trustedOriginMatchesWithExplicitDefaultPortAndCase() {
        assertEquals(stateReply(port.state), handler.handle(STATE_GET, "https://BeOnePay.site:443", isMainFrame = true))
    }

    @Test
    fun errorsAreSentOnlyToTrustedMainFrameAndDoNotTouchAuthState() {
        assertEquals(Outcome.Reply(ERROR_UNKNOWN_TYPE), handler.handle("""{"v":1,"type":"unknown"}""", TRUSTED, true))
        assertEquals(Outcome.Reply(ERROR_UNSUPPORTED_VERSION), handler.handle("""{"v":2,"type":"auth.request"}""", TRUSTED, true))
        assertEquals(Outcome.Reply(ERROR_INVALID_MESSAGE), handler.handle("""{"type":"auth.request"}""", TRUSTED, true))
        assertEquals(Outcome.Reply(ERROR_INVALID_MESSAGE), handler.handle("not json", TRUSTED, true))
        assertEquals(Outcome.Reply(ERROR_INVALID_MESSAGE), handler.handle(null, TRUSTED, true))
        val oversized = """{"v":1,"type":"auth.request","pad":"${"a".repeat(4096)}"}"""
        assertEquals(Outcome.Reply(ERROR_INVALID_MESSAGE), handler.handle(oversized, TRUSTED, true))
        assertEquals(0, port.authRequests)
        assertEquals(0, port.stateReads)
    }

    @Test
    fun errorVersionIsAlwaysOne() {
        assertEquals(Outcome.Reply(ERROR_UNSUPPORTED_VERSION), handler.handle("""{"v":7,"type":"x"}""", TRUSTED, true))
        assertEquals("""{"v":1,"type":"error","code":"UNSUPPORTED_VERSION"}""", ERROR_UNSUPPORTED_VERSION)
    }

    @Test
    fun authRequestTriesAuthenticationThenSendsResultingState() {
        port.onAuthRequest = {
            port.state = BridgeState(false, 0, "NOT_AUTHENTICATED", null, authenticating = true)
        }
        val outcome = handler.handle(AUTH_REQUEST, TRUSTED, true)
        assertEquals(1, port.authRequests)
        assertEquals(
            Outcome.Reply("""{"v":1,"type":"state","active":false,"remainingMs":0,"reason":"NOT_AUTHENTICATED","authenticating":true}"""),
            outcome,
        )
    }

    @Test
    fun refusedAuthRequestStillSendsCurrentState() {
        // 받을 수 없는 단계·거부·미전달이면 상태가 그대로이고, 별도 메시지 없이 현재 상태를 다시 보낸다.
        port.state = BridgeState(false, 0, "NO_RESPONSE", null, authenticating = true)
        assertEquals(stateReply(port.state), handler.handle(AUTH_REQUEST, TRUSTED, true))
        assertEquals(1, port.authRequests)
    }

    @Test
    fun stateGetSendsStateWithoutRequestingAuthentication() {
        port.state = BridgeState(true, 60_000, "ACTIVE", "BIOMETRIC", authenticating = false)
        assertEquals(
            Outcome.Reply("""{"v":1,"type":"state","active":true,"remainingMs":60000,"reason":"ACTIVE","method":"BIOMETRIC","authenticating":false}"""),
            handler.handle(STATE_GET, TRUSTED, true),
        )
        assertEquals(0, port.authRequests)
    }

    @Test
    fun paymentCheckAllowsOnlyWhenActive() {
        port.state = BridgeState(true, 1, "ACTIVE", "DEVICE_CREDENTIAL", authenticating = false)
        assertEquals(
            Outcome.Reply("""{"v":1,"type":"payment.check.result","allowed":true,"reason":"ACTIVE"}"""),
            handler.handle(PAYMENT_CHECK, TRUSTED, true),
        )
        port.state = BridgeState(false, 0, "EXPIRED", null, authenticating = false)
        assertEquals(
            Outcome.Reply("""{"v":1,"type":"payment.check.result","allowed":false,"reason":"EXPIRED"}"""),
            handler.handle(PAYMENT_CHECK, TRUSTED, true),
        )
        assertEquals(0, port.authRequests)
    }

    @Test
    fun emptyTrustedSetIgnoresEverything() {
        val noTrust = BridgeMessageHandler(emptySet(), port)
        assertEquals(Outcome.Ignored, noTrust.handle(STATE_GET, TRUSTED, true))
    }

    @Test
    fun stateMessageUsesCurrentState() {
        port.state = BridgeState(false, 0, "LEFT_APP", null, authenticating = false)
        assertEquals(
            """{"v":1,"type":"state","active":false,"remainingMs":0,"reason":"LEFT_APP","authenticating":false}""",
            handler.stateMessage(),
        )
    }

    private fun stateReply(state: BridgeState) = Outcome.Reply(BridgeOutbound.state(state))

    private class FakePort : BridgeAuthPort {
        var state = BridgeState(false, 0, "NOT_AUTHENTICATED", null, authenticating = false)
        var onAuthRequest: () -> Unit = {}
        var authRequests = 0
        var stateReads = 0

        override fun requestAuthentication() {
            authRequests++
            onAuthRequest()
        }

        override fun currentState(): BridgeState {
            stateReads++
            return state
        }
    }

    private companion object {
        const val TRUSTED = "https://beonepay.site"
        const val AUTH_REQUEST = """{"v":1,"type":"auth.request"}"""
        const val STATE_GET = """{"v":1,"type":"state.get"}"""
        const val PAYMENT_CHECK = """{"v":1,"type":"payment.check"}"""
        const val ERROR_UNKNOWN_TYPE = """{"v":1,"type":"error","code":"UNKNOWN_TYPE"}"""
        const val ERROR_UNSUPPORTED_VERSION = """{"v":1,"type":"error","code":"UNSUPPORTED_VERSION"}"""
        const val ERROR_INVALID_MESSAGE = """{"v":1,"type":"error","code":"INVALID_MESSAGE"}"""

        // 정상·오류·크기 초과·비문자열 메시지를 모두 포함한다.
        val ALL_KINDS = listOf(
            AUTH_REQUEST,
            STATE_GET,
            PAYMENT_CHECK,
            """{"v":1,"type":"unknown"}""",
            """{"v":2,"type":"state.get"}""",
            "not json",
            """{"v":1,"type":"auth.request","pad":"${"a".repeat(4096)}"}""",
            null,
        )
    }
}
