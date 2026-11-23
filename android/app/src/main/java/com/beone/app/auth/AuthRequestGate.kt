package com.beone.app.auth

// 앱이 라이브러리에 보낸 인증 요청을 동시에 하나로 제한하고, 도착한 결과를 적용할지 버릴지 정한다.
// BiometricPrompt 콜백에는 요청 식별자가 없으므로 "인증 중인 동안 도착한 결과 = 그 요청의 결과"로만 귀속한다.
// 대기로 돌아가는 근거는 앱이 받은 최종 결과뿐이다. 시간 만료는 표시 전환(정지)에만 쓰고 요청 종료로 보지 않는다.
// Android에 의존하지 않는 순수 로직이며, 실제 라이브러리 콜백·창 표시를 보장하지 않는다.
// trace는 전이가 끝난 뒤 관측 기록만 남기며 전이·반환값을 바꾸지 않는다.
class AuthRequestGate(
    private val elapsedRealtimeMillis: () -> Long,
    private val timeouts: AuthResponseTimeouts,
    private val onAuthenticatingStalled: () -> Unit = {},
    private val trace: AuthTracer = AuthTracer.NONE,
) {

    private var current: AuthRequestPhase = AuthRequestPhase.Idle
    private var deadlineMillis: Long = 0L
    private var lastRequestId: Long = 0L

    val phase: AuthRequestPhase
        get() {
            refresh()
            return current
        }

    // 응답 대기 시간이 지났으면 정지로 바꾼다. 인증 중이던 요청이 정지되면 생성 시 받은 알림을 부른다.
    // 모든 공개 동작이 먼저 부르므로, 대기 시간이 지난 뒤 도착한 결과는 정지 상태에서 처리된다.
    fun refresh() {
        if (elapsedRealtimeMillis() < deadlineMillis) return
        when (val phase = current) {
            is AuthRequestPhase.Authenticating -> {
                current = AuthRequestPhase.Stalled(phase.requestId, cancelRequested = false)
                trace.emit(AuthTraceType.TIMEOUT, from = phase, to = current, requestId = phase.requestId)
                onAuthenticatingStalled()
            }
            is AuthRequestPhase.Draining -> {
                current = AuthRequestPhase.Stalled(phase.requestId, cancelRequested = true)
                trace.emit(AuthTraceType.TIMEOUT, from = phase, to = current, requestId = phase.requestId)
            }
            AuthRequestPhase.Idle, is AuthRequestPhase.Stalled -> Unit
        }
    }

    // 대기 상태에서만 새 요청을 연다. 외부 authenticate 호출 전에 불러 상태를 먼저 바꾼다.
    fun begin(): Long? {
        refresh()
        if (current != AuthRequestPhase.Idle) {
            trace.emit(AuthTraceType.BEGIN_REJECTED, from = current, to = current, requestId = current.traceRequestId)
            return null
        }
        val requestId = ++lastRequestId
        current = AuthRequestPhase.Authenticating(requestId)
        deadlineMillis = elapsedRealtimeMillis() + timeouts.authenticatingMillis
        trace.emit(AuthTraceType.BEGIN, from = AuthRequestPhase.Idle, to = current, requestId = requestId)
        return requestId
    }

    // 라이브러리에 전달하지 못한 요청을 되돌린다. 그 사이 다른 전이가 있었으면 건드리지 않는다.
    fun withdrawUnsent(requestId: Long) {
        val before = current
        val withdrawn = before == AuthRequestPhase.Authenticating(requestId)
        if (withdrawn) {
            current = AuthRequestPhase.Idle
        }
        trace.emit(
            AuthTraceType.WITHDRAW_UNSENT,
            from = before,
            to = current,
            requestId = requestId,
            fields = listOf("applied" to withdrawn.toString()),
        )
    }

    // 결과를 활성 상태에 적용해야 하면 true. 인증 중인 요청의 결과만 적용한다.
    // 최종 결과는 어느 단계에서 왔든 그 요청을 끝내고 대기로 돌린다(정리 중·정지 중이면 버린다).
    fun accept(outcome: AuthOutcome): Boolean {
        refresh()
        val phase = current
        if (phase == AuthRequestPhase.Idle) {
            traceResult(outcome, phase, applied = false)
            return false
        }
        if (outcome.isTerminal) {
            current = AuthRequestPhase.Idle
        }
        val applied = phase is AuthRequestPhase.Authenticating
        traceResult(outcome, phase, applied)
        return applied
    }

    private fun traceResult(outcome: AuthOutcome, before: AuthRequestPhase, applied: Boolean) {
        trace.emit(
            AuthTraceType.RESULT,
            from = before,
            to = current,
            requestId = before.traceRequestId,
            fields = outcome.traceFields() + listOf(
                "terminal" to outcome.isTerminal.toString(),
                "applied" to applied.toString(),
            ),
        )
    }

    // 앱 이탈. 아직 취소를 요청하지 않은 요청이 있으면 상태를 먼저 바꾸고 true를 돌려 호출 측이 취소하게 한다.
    fun onAppLeft(): Boolean {
        refresh()
        val before = current
        val shouldCancel = when (val phase = before) {
            is AuthRequestPhase.Authenticating -> {
                current = AuthRequestPhase.Draining(phase.requestId)
                deadlineMillis = elapsedRealtimeMillis() + timeouts.drainingMillis
                true
            }
            is AuthRequestPhase.Stalled -> {
                if (phase.cancelRequested) {
                    false
                } else {
                    current = phase.copy(cancelRequested = true)
                    true
                }
            }
            AuthRequestPhase.Idle, is AuthRequestPhase.Draining -> false
        }
        trace.emit(
            AuthTraceType.APP_LEFT,
            from = before,
            to = current,
            requestId = before.traceRequestId,
            fields = listOf("cancel" to shouldCancel.toString()),
        )
        return shouldCancel
    }
}

sealed interface AuthRequestPhase {

    // 새 인증 요청을 받을 수 있음
    data object Idle : AuthRequestPhase

    // 라이브러리에 요청을 전달했고 최종 결과를 기다림. 실제 창 표시 여부는 알 수 없다.
    data class Authenticating(val requestId: Long) : AuthRequestPhase

    // 앱 이탈로 취소를 요청했고 그 요청의 최종 결과를 기다림. 도착한 결과는 버린다.
    data class Draining(val requestId: Long) : AuthRequestPhase

    // 응답 대기 시간을 넘김. 여전히 그 요청의 최종 결과를 기다리며 새 요청을 받지 않는다.
    data class Stalled(val requestId: Long, val cancelRequested: Boolean) : AuthRequestPhase
}

// 응답 대기 시간. 기본값 120초·10초는 근거 없는 제안이며 실기기 관측으로 조정한다.
// 정상 PIN 입력 시간과 창 미표시를 구분할 근거는 없다.
data class AuthResponseTimeouts(
    val authenticatingMillis: Long = 120_000L,
    val drainingMillis: Long = 10_000L,
) {
    init {
        require(authenticatingMillis > 0L && drainingMillis > 0L) { "응답 대기 시간은 0보다 커야 한다." }
    }
}
