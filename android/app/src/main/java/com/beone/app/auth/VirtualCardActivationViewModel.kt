package com.beone.app.auth

import androidx.lifecycle.ViewModel

// 화면 재생성(회전·폴드·테마 변경) 동안 활성 상태와 진행 중인 인증 요청 단계를 유지한다.
// 응답 대기 시간은 운영 값이 정해지지 않아 기본값 없이 주입받는다.
// trace는 관측 기록용이며 기본값은 무동작이다. 기록 여부와 관계없이 호출·전이 순서는 같다.
class VirtualCardActivationViewModel(
    elapsedRealtimeMillis: () -> Long,
    responseTimeouts: AuthResponseTimeouts,
    private val trace: AuthTracer = AuthTracer.NONE,
) : ViewModel() {

    private val activation = VirtualCardActivation(elapsedRealtimeMillis)

    // 인증 중인 요청이 응답 대기 시간을 넘겨 정지되면 활성 상태를 남기지 않는다.
    private val gate = AuthRequestGate(
        elapsedRealtimeMillis,
        responseTimeouts,
        onAuthenticatingStalled = {
            activation.onNoResponse()
            traceActivation()
        },
        trace = trace,
    )

    // authenticate·cancel 호출 중 동기 콜백이 다시 요청해도 라이브러리 호출을 겹치지 않게 막는다.
    private var callingAuthenticator = false

    val state: ActivationState
        get() {
            refresh()
            return activation.state
        }

    val requestPhase: AuthRequestPhase get() = gate.phase

    fun remainingMillis(): Long {
        refresh()
        return activation.remainingMillis()
    }

    // 라이브러리에 인증 요청을 전달했으면 true. 창 표시를 확인한 것은 아니다.
    // 대기 단계가 아니거나(인증 중·정리 중·정지) 기기에서 인증할 수 없으면 false.
    fun requestAuthentication(authenticator: DeviceAuthenticator): Boolean {
        trace.emit(AuthTraceType.REQUEST_ATTEMPT)
        if (callingAuthenticator) {
            trace.emit(AuthTraceType.REQUEST_REJECTED, fields = listOf("reason" to "IN_CALL"))
            return false
        }
        val phase = gate.phase
        if (phase != AuthRequestPhase.Idle) {
            trace.emit(
                AuthTraceType.REQUEST_REJECTED,
                from = phase,
                to = phase,
                requestId = phase.traceRequestId,
                fields = listOf("reason" to "NOT_IDLE"),
            )
            return false
        }
        val blocked = authenticator.availability()
        if (blocked != null) {
            activation.onAuthOutcome(blocked)
            trace.emit(AuthTraceType.AVAILABILITY_BLOCKED, fields = blocked.traceFields())
            traceActivation()
            return false
        }
        val requestId = gate.begin() ?: return false
        val dispatch = callAuthenticator { authenticator.authenticate() }
        trace.emit(AuthTraceType.DISPATCH, requestId = requestId, fields = listOf("result" to dispatch.name))
        if (dispatch == AuthRequestDispatch.NOT_SENT) {
            gate.withdrawUnsent(requestId)
            return false
        }
        return true
    }

    // 인증 중인 요청의 결과만 활성 상태에 적용한다. 정리 중·정지 중 결과는 버리고 최종 결과면 대기로 돌린다.
    fun onAuthOutcome(outcome: AuthOutcome): ActivationState {
        if (gate.accept(outcome)) {
            activation.onAuthOutcome(outcome)
            traceActivation()
        }
        return activation.state
    }

    // 화면 구성 변경으로 멈춘 경우는 앱 이탈로 보지 않는다.
    // 앱 이탈 때 인증 중이면 상태를 정리 중으로 먼저 바꾼 뒤 취소를 요청한다.
    fun onScreenStopped(isChangingConfigurations: Boolean, authenticator: DeviceAuthenticator) {
        if (isChangingConfigurations) return
        val shouldCancel = gate.onAppLeft()
        activation.onAppLeft(canceledPendingRequest = shouldCancel)
        traceActivation()
        if (shouldCancel) {
            trace.emit(AuthTraceType.CANCEL_REQUESTED)
            callAuthenticator { authenticator.cancel() }
        }
    }

    // 활성 상태를 읽기 전에 응답 대기 시간 만료를 먼저 반영한다.
    private fun refresh() {
        gate.refresh()
    }

    // 만료 전이를 일으키지 않는 읽기만 써서 기록한다.
    private fun traceActivation() {
        trace.emit(AuthTraceType.ACTIVATION, fields = activation.traceFields())
    }

    private inline fun <T> callAuthenticator(call: () -> T): T {
        val outer = callingAuthenticator
        callingAuthenticator = true
        try {
            return call()
        } finally {
            callingAuthenticator = outer
        }
    }
}
