package com.beone.app.bridge

import com.beone.app.web.WebOrigin

/** 브리지가 네이티브 인증 상태에 접근하는 경계. 실제 구현은 화면이, 테스트는 대체 구현이 맡는다. */
interface BridgeAuthPort {

    /** 기기 인증 요청을 시도한다. 받을 수 없는 단계이거나 전달되지 않아도 별도 결과를 돌려주지 않는다. */
    fun requestAuthentication()

    /** 지금 시점의 네이티브 상태. */
    fun currentState(): BridgeState
}

/**
 * 웹 메시지 하나를 계약의 검사 순서대로 처리하고 보낼 응답을 정한다. Android WebView API에 의존하지 않는다.
 * 1단계(신뢰 origin·최상위 프레임)를 통과하지 못하면 어떤 응답도 만들지 않는다.
 */
class BridgeMessageHandler(
    trustedOrigins: Set<WebOrigin>,
    private val port: BridgeAuthPort,
) {

    private val trustedOrigins: Set<WebOrigin> = trustedOrigins.toSet()

    /** 처리 결과. */
    sealed interface Outcome {
        /** 신뢰하지 않는 origin이나 하위 프레임. 응답하지 않는다. */
        data object Ignored : Outcome

        /** 최상위 프레임의 신뢰 origin 메시지. [message]를 같은 프레임에 보낸다. */
        data class Reply(val message: String) : Outcome
    }

    /** [sourceOrigin]은 WebView가 알려 준 보낸 쪽 origin 문자열이다. 해석할 수 없으면 신뢰하지 않는다. */
    fun handle(message: String?, sourceOrigin: String, isMainFrame: Boolean): Outcome {
        if (!isTrustedMainFrame(sourceOrigin, isMainFrame)) return Outcome.Ignored
        val reply = when (val parsed = BridgeMessageParser.parse(message)) {
            is BridgeMessageParser.Result.Rejected -> BridgeOutbound.error(parsed.code)
            is BridgeMessageParser.Result.Accepted -> respond(parsed.request)
        }
        return Outcome.Reply(reply)
    }

    /** 상태가 바뀌었을 때 밀어 보낼 `state` 메시지. 받을 프레임은 호출 측이 이미 신뢰 판정을 통과한 프레임이어야 한다. */
    fun stateMessage(): String = BridgeOutbound.state(port.currentState())

    /** 보낸 쪽이 최상위 프레임이고 origin이 신뢰 목록과 정확히 같은지 판정한다. */
    fun isTrustedMainFrame(sourceOrigin: String, isMainFrame: Boolean): Boolean {
        if (!isMainFrame) return false
        val origin = WebOrigin.parse(sourceOrigin) ?: return false
        return origin in trustedOrigins
    }

    private fun respond(request: BridgeRequest): String = when (request) {
        // 받을 수 없는 단계·거부·미전달이어도 별도 메시지 없이 그 뒤의 현재 상태를 보낸다.
        BridgeRequest.AUTH_REQUEST -> {
            port.requestAuthentication()
            BridgeOutbound.state(port.currentState())
        }
        BridgeRequest.STATE_GET -> BridgeOutbound.state(port.currentState())
        BridgeRequest.PAYMENT_CHECK -> BridgeOutbound.paymentCheckResult(port.currentState())
    }
}
