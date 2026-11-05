package com.beone.app.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// 관측 기록 연결의 계약만 검증한다. 기록 순서는 가짜 authenticator로 만든 사건 순서이며,
// 실제 BiometricPrompt 콜백 도착·순서나 OS 동작을 입증하지 않는다.
class AuthTraceTest {

    private class FakeAuthenticator : DeviceAuthenticator {
        var promptCount = 0
        var cancelCount = 0
        var blocked: AuthOutcome? = null
        var dispatch = AuthRequestDispatch.SENT_TO_LIBRARY

        override fun availability(): AuthOutcome? = blocked

        override fun authenticate(): AuthRequestDispatch {
            promptCount++
            return dispatch
        }

        override fun cancel() {
            cancelCount++
        }
    }

    private class RecordingSink : AuthTraceSink {
        val events = mutableListOf<AuthTraceEvent>()

        override fun record(event: AuthTraceEvent) {
            events += event
        }
    }

    private val throwingSink = AuthTraceSink { throw IllegalStateException("관측 실패") }

    // 같은 사건 순서를 하나의 관측 설정으로 실행하는 환경
    private class Harness(sink: AuthTraceSink) {
        var now = 5_000L
        val authenticator = FakeAuthenticator()
        val viewModel = VirtualCardActivationViewModel(
            { now },
            AuthResponseTimeouts(authenticatingMillis = 30_000L, drainingMillis = 5_000L),
            if (sink === AuthTraceSink.None) AuthTracer.NONE else AuthTracer({ now }, sink),
        )
        val snapshots = mutableListOf<List<Any?>>()

        fun snapshot(step: String, returned: Any? = null) {
            snapshots += listOf(
                step,
                returned,
                viewModel.state,
                viewModel.requestPhase,
                viewModel.remainingMillis(),
                authenticator.promptCount,
                authenticator.cancelCount,
            )
        }

        fun request() = snapshot("request", viewModel.requestAuthentication(authenticator))

        fun outcome(outcome: AuthOutcome) = snapshot("outcome", viewModel.onAuthOutcome(outcome))

        fun leave() {
            viewModel.onScreenStopped(isChangingConfigurations = false, authenticator)
            snapshot("leave")
        }

        fun advance(millis: Long) {
            now += millis
            snapshot("advance")
        }
    }

    // 대표 경로를 모두 지나는 사건 순서
    private fun runScript(sink: AuthTraceSink): List<List<Any?>> = Harness(sink).apply {
        request()
        outcome(AuthOutcome.Succeeded(AuthMethod.BIOMETRIC))
        advance(10_000L)
        request()
        outcome(AuthOutcome.Mismatch)
        leave()
        request()
        outcome(AuthOutcome.Succeeded(AuthMethod.BIOMETRIC))
        request()
        advance(30_000L)
        request()
        outcome(AuthOutcome.Succeeded(AuthMethod.DEVICE_CREDENTIAL))
        request()
        leave()
        advance(5_000L)
        outcome(AuthOutcome.Canceled(errorCode = 5))
        authenticator.dispatch = AuthRequestDispatch.NOT_SENT
        request()
        authenticator.dispatch = AuthRequestDispatch.SENT_TO_LIBRARY
        authenticator.blocked = AuthOutcome.SetupRequired
        request()
        authenticator.blocked = null
        request()
        outcome(AuthOutcome.Succeeded(AuthMethod.BIOMETRIC))
        viewModel.onScreenStopped(isChangingConfigurations = true, authenticator)
        snapshot("rotate")
        advance(60_000L)
    }.snapshots

    private fun recordedRun(script: Harness.() -> Unit): List<AuthTraceEvent> {
        val sink = RecordingSink()
        Harness(sink).script()
        return sink.events
    }

    private fun AuthTraceEvent.summary(): String =
        "$type $from>$to req=${requestId ?: "-"} " + fields.joinToString(" ") { (key, value) -> "$key=$value" }

    @Test
    fun recordingDoesNotChangeStateTimeOrLibraryCalls() {
        val withoutTrace = runScript(AuthTraceSink.None)
        val recording = RecordingSink()

        assertEquals(withoutTrace, runScript(recording))
        assertTrue(recording.events.isNotEmpty())
    }

    @Test
    fun throwingSinkDoesNotChangeBehavior() {
        assertEquals(runScript(AuthTraceSink.None), runScript(throwingSink))
    }

    @Test
    fun throwingSinkIsSwallowedOnEveryEmit() {
        var calls = 0
        val tracer = AuthTracer({ 1L }) { calls++; throw IllegalStateException("관측 실패") }

        tracer.emit(AuthTraceType.REQUEST_ATTEMPT)
        tracer.emit(AuthTraceType.REQUEST_ATTEMPT)

        assertEquals(2, calls)
    }

    @Test
    fun disabledTracerNeverCallsClockOrSink() {
        var clockCalls = 0
        val tracer = AuthTracer({ clockCalls++; 0L }, AuthTraceSink.None)

        tracer.emit(AuthTraceType.REQUEST_ATTEMPT)

        assertEquals(0, clockCalls)
    }

    @Test
    fun successPathEventOrder() {
        val events = recordedRun {
            viewModel.requestAuthentication(authenticator)
            viewModel.onAuthOutcome(AuthOutcome.Succeeded(AuthMethod.BIOMETRIC))
        }

        assertEquals(
            listOf(
                "REQUEST_ATTEMPT ->- req=- ",
                "BEGIN IDLE>AUTHENTICATING req=1 ",
                "DISPATCH ->- req=1 result=SENT_TO_LIBRARY",
                "RESULT AUTHENTICATING>IDLE req=1 kind=SUCCEEDED method=BIOMETRIC terminal=true applied=true",
                "ACTIVATION ->- req=- activation=ACTIVE method=BIOMETRIC expiresAt=65000",
            ),
            events.map { it.summary() },
        )
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), events.map { it.seq })
    }

    @Test
    fun leaveThenDrainingThenFinalResultThenIdleEventOrder() {
        val events = recordedRun {
            viewModel.requestAuthentication(authenticator)
            viewModel.onScreenStopped(isChangingConfigurations = false, authenticator)
            viewModel.onAuthOutcome(AuthOutcome.Mismatch)
            viewModel.onAuthOutcome(AuthOutcome.Canceled(errorCode = 5))
        }

        assertEquals(
            listOf(
                "REQUEST_ATTEMPT ->- req=- ",
                "BEGIN IDLE>AUTHENTICATING req=1 ",
                "DISPATCH ->- req=1 result=SENT_TO_LIBRARY",
                "APP_LEFT AUTHENTICATING>DRAINING req=1 cancel=true",
                "ACTIVATION ->- req=- activation=INACTIVE reason=LEFT_APP",
                "CANCEL_REQUESTED ->- req=- ",
                "RESULT DRAINING>DRAINING req=1 kind=MISMATCH terminal=false applied=false",
                "RESULT DRAINING>IDLE req=1 kind=CANCELED code=5 terminal=true applied=false",
            ),
            events.map { it.summary() },
        )
    }

    @Test
    fun authenticatingTimeoutThenLateResultThenIdleEventOrder() {
        val events = recordedRun {
            viewModel.requestAuthentication(authenticator)
            now += 30_000L
            viewModel.requestPhase
            assertFalse(viewModel.requestAuthentication(authenticator))
            viewModel.onAuthOutcome(AuthOutcome.Succeeded(AuthMethod.DEVICE_CREDENTIAL))
        }

        assertEquals(
            listOf(
                "REQUEST_ATTEMPT ->- req=- ",
                "BEGIN IDLE>AUTHENTICATING req=1 ",
                "DISPATCH ->- req=1 result=SENT_TO_LIBRARY",
                "TIMEOUT AUTHENTICATING>STALLED req=1 ",
                "ACTIVATION ->- req=- activation=INACTIVE reason=NO_RESPONSE",
                "REQUEST_ATTEMPT ->- req=- ",
                "REQUEST_REJECTED STALLED>STALLED req=1 reason=NOT_IDLE",
                "RESULT STALLED>IDLE req=1 kind=SUCCEEDED method=DEVICE_CREDENTIAL terminal=true applied=false",
            ),
            events.map { it.summary() },
        )
    }

    @Test
    fun drainingTimeoutEventOrder() {
        val events = recordedRun {
            viewModel.requestAuthentication(authenticator)
            viewModel.onScreenStopped(isChangingConfigurations = false, authenticator)
            now += 5_000L
            viewModel.requestPhase
        }

        assertEquals(
            "TIMEOUT DRAINING>STALLED_CANCEL_REQUESTED req=1 ",
            events.last().summary(),
        )
    }

    @Test
    fun notSentRequestIsWithdrawnEventOrder() {
        val events = recordedRun {
            authenticator.dispatch = AuthRequestDispatch.NOT_SENT
            viewModel.requestAuthentication(authenticator)
        }

        assertEquals(
            listOf(
                "REQUEST_ATTEMPT ->- req=- ",
                "BEGIN IDLE>AUTHENTICATING req=1 ",
                "DISPATCH ->- req=1 result=NOT_SENT",
                "WITHDRAW_UNSENT AUTHENTICATING>IDLE req=1 applied=true",
            ),
            events.map { it.summary() },
        )
    }

    @Test
    fun availabilityBlockedRecordsOutcomeKindOnly() {
        val events = recordedRun {
            authenticator.blocked = AuthOutcome.Unavailable(errorCode = 1)
            viewModel.requestAuthentication(authenticator)
        }

        assertEquals(
            listOf(
                "REQUEST_ATTEMPT ->- req=- ",
                "AVAILABILITY_BLOCKED ->- req=- kind=UNAVAILABLE code=1",
                "ACTIVATION ->- req=- activation=INACTIVE reason=UNAVAILABLE",
            ),
            events.map { it.summary() },
        )
    }

    @Test
    fun lineFormatIsFixed() {
        val event = AuthTraceEvent(
            atMillis = 12_345L,
            seq = 7L,
            type = AuthTraceType.RESULT,
            from = "AUTHENTICATING",
            to = "IDLE",
            requestId = 3L,
            fields = AuthOutcome.Failed(errorCode = 7).traceFields() + listOf("terminal" to "true", "applied" to "true"),
        )

        assertEquals(
            "t=12345 seq=7 ev=RESULT from=AUTHENTICATING to=IDLE req=3 kind=FAILED code=7 terminal=true applied=true",
            event.toLine(),
        )
        assertEquals(
            "t=1 seq=1 ev=REQUEST_ATTEMPT from=- to=- req=-",
            AuthTraceEvent(1L, 1L, AuthTraceType.REQUEST_ATTEMPT, "-", "-", null, emptyList()).toLine(),
        )
    }
}
