package com.beone.app.web

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * 신뢰 origin 밖으로의 메인 프레임 탐색을 막고 SSL 오류를 무시하지 않는 WebViewClient.
 * 리디렉션 뒤의 POST, iframe 안의 POST, fetch 같은 하위 요청의 전송까지 막는다고 보장하지 않는다.
 */
class RestrictedWebViewClient(
    private val policy: WebNavigationPolicy,
    private val listener: Listener,
) : WebViewClient() {

    /** 탐색 결과를 화면에 알린다. */
    interface Listener {
        /** 신뢰 origin 밖 메인 프레임 탐색을 막았을 때 호출된다. */
        fun onNavigationBlocked()

        /** 신뢰 origin의 페이지가 시작될 때 호출된다. */
        fun onTrustedPageStarted()

        /** 웹 기록이 바뀔 때 뒤로 갈 수 있는지 전달한다. */
        fun onHistoryChanged(canGoBack: Boolean)
    }

    /** 페이지·사용자·리디렉션이 시작한 탐색을 막는다. 공식 문서상 POST와 loadUrl에는 호출되지 않는다. */
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (policy.isAllowed(request.url.toString())) return false
        // 신뢰하지 않는 주소는 WebView에서 열지 않는다. 외부 앱으로 넘기는 동작도 아직 정하지 않았다.
        if (request.isForMainFrame) listener.onNavigationBlocked()
        return true
    }

    /**
     * shouldOverrideUrlLoading이 불리지 않는 POST 폼 제출의 최초 요청에 빈 응답을 돌려준다.
     * 공식 문서상 리디렉션 뒤 주소에는 호출되지 않는다. UI 스레드가 아니므로 화면은 건드리지 않는다.
     */
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        if (!policy.shouldBlockRequest(request.url.toString(), request.isForMainFrame)) return null
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            BLOCKED_STATUS,
            BLOCKED_REASON,
            emptyMap(),
            ByteArrayInputStream(ByteArray(0)),
        )
    }

    /**
     * 페이지가 시작된 뒤 탐색을 중지하고 안내하는 보조 단계다. 이미 나간 요청을 되돌리지 못한다.
     * 공식 문서상 메인 프레임 로드에만 호출된다.
     */
    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        if (policy.isAllowed(url)) {
            listener.onTrustedPageStarted()
            return
        }
        view.stopLoading()
        if (url != BLANK_PAGE) listener.onNavigationBlocked()
    }

    /** 웹 기록 변경을 화면에 전달한다. */
    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        listener.onHistoryChanged(view.canGoBack())
    }

    /** 인증서 오류를 무시하지 않고 연결을 취소한다. */
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
    }

    private companion object {
        const val BLANK_PAGE = "about:blank"
        const val BLOCKED_STATUS = 403
        const val BLOCKED_REASON = "Blocked"
    }
}
