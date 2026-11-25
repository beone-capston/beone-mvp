package com.beone.app.bridge

import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * [BridgeMessageHandler]를 AndroidX `WebViewCompat.addWebMessageListener`에 연결한다.
 * 허용 origin 검사는 플랫폼이 먼저 하고, 핸들러가 origin과 `isMainFrame`을 다시 확인한다.
 * 상태를 밀어 보낼 대상은 마지막으로 신뢰 판정을 통과한 최상위 프레임이다.
 */
class WebMessageBridge(private val handler: BridgeMessageHandler) {

    private var replyProxy: JavaScriptReplyProxy? = null

    /**
     * 리스너를 붙이고 붙였으면 true. 페이지를 불러오기 전에 불러야 한다.
     * WebView가 `WEB_MESSAGE_LISTENER`를 지원하지 않으면 붙이지 않고 false다(계약 밖, 미정).
     */
    fun attach(webView: WebView, jsObjectName: String, allowedOriginRules: Set<String>): Boolean {
        if (allowedOriginRules.isEmpty()) return false
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return false
        WebViewCompat.addWebMessageListener(webView, jsObjectName, allowedOriginRules) { _, message, sourceOrigin, isMainFrame, proxy ->
            onMessage(message, sourceOrigin.toString(), isMainFrame, proxy)
        }
        return true
    }

    /** 상태가 바뀐 뒤 부른다. 아직 신뢰 프레임의 메시지를 받은 적이 없으면 보내지 않는다. */
    fun pushState() {
        val proxy = replyProxy ?: return
        post(proxy, handler.stateMessage())
    }

    private fun onMessage(message: WebMessageCompat, sourceOrigin: String, isMainFrame: Boolean, proxy: JavaScriptReplyProxy) {
        // 문자열이 아닌 메시지(ArrayBuffer)는 형식 오류로 넘긴다.
        val data = if (message.type == WebMessageCompat.TYPE_STRING) message.data else null
        when (val outcome = handler.handle(data, sourceOrigin, isMainFrame)) {
            BridgeMessageHandler.Outcome.Ignored -> Unit
            is BridgeMessageHandler.Outcome.Reply -> {
                replyProxy = proxy
                post(proxy, outcome.message)
            }
        }
    }

    /** 응답 대상은 리스너가 붙은 뒤에만 생기지만, 기능 지원을 보내기 직전에도 확인한다. */
    private fun post(proxy: JavaScriptReplyProxy, message: String) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            proxy.postMessage(message)
        }
    }
}
