package com.beone.app.bridge

import com.beone.app.auth.DeviceAuthenticator
import com.beone.app.auth.VirtualCardActivationViewModel

/**
 * 브리지 요청을 가상카드 활성 상태(ViewModel)에 연결한다.
 * 요청 가능 여부·응답 대기 시간·늦은 결과 처리는 ViewModel과 AuthRequestGate가 정하고 여기서는 바꾸지 않는다.
 */
class ActivationBridgePort(
    private val viewModel: VirtualCardActivationViewModel,
    private val authenticator: DeviceAuthenticator,
) : BridgeAuthPort {

    /** 대기 단계가 아니거나 전달되지 않으면 ViewModel이 거부하고, 응답은 그 뒤의 상태로 대신한다(C4). */
    override fun requestAuthentication() {
        viewModel.requestAuthentication(authenticator)
    }

    /** 활성 상태를 먼저 읽어 응답 대기 시간 만료를 반영한 뒤 요청 단계와 남은 시간을 읽는다. */
    override fun currentState(): BridgeState {
        val state = viewModel.state
        return BridgeState.of(state, viewModel.requestPhase, viewModel.remainingMillis())
    }
}
