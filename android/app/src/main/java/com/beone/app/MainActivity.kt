package com.beone.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.beone.app.auth.AuthResponseTimeouts
import com.beone.app.auth.BiometricDeviceAuthenticator
import com.beone.app.auth.VirtualCardActivationViewModel
import com.beone.app.bridge.ActivationBridgePort
import com.beone.app.bridge.BridgeContract
import com.beone.app.bridge.BridgeMessageHandler
import com.beone.app.bridge.BridgeOrigins
import com.beone.app.bridge.WebMessageBridge
import com.beone.app.web.RestrictedWebViewClient
import com.beone.app.web.WebNavigationPolicy

/** React 화면을 표시할 WebView 셸. 신뢰 origin의 최상위 프레임에만 브리지 v1을 노출한다. */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var messageText: TextView
    private lateinit var activation: VirtualCardActivationViewModel
    private lateinit var authenticator: BiometricDeviceAuthenticator

    /** 시작 주소가 없거나 WebView가 기능을 지원하지 않으면 null이다. */
    private var bridge: WebMessageBridge? = null

    /** 웹 페이지 기록이 있을 때만 켜서, 없으면 시스템 기본 뒤로 가기에 맡긴다. */
    private val webBackCallback = object : OnBackPressedCallback(false) {
        /** WebView의 이전 페이지로 돌아간다. */
        override fun handleOnBackPressed() {
            webView.goBack()
        }
    }

    /** 화면과 inset을 준비하고, 주소 설정이 유효하면 시작 주소를 불러오거나 저장된 상태를 복원한다. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // WebView는 자체 padding을 두지 않으므로 edge-to-edge에서 상위 컨테이너에 시스템 바·키보드 inset을 준다.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        webView = findViewById(R.id.main_web_view)
        messageText = findViewById(R.id.main_message)
        activation = ViewModelProvider(this, ActivationViewModelFactory)[VirtualCardActivationViewModel::class.java]
        // 화면 재생성 뒤에도 결과를 받도록 매번 새로 만든다. 결과를 반영한 뒤 웹에 상태를 보낸다.
        authenticator = BiometricDeviceAuthenticator(this) { outcome ->
            activation.onAuthOutcome(outcome)
            bridge?.pushState()
        }

        val policy = WebNavigationPolicy(BuildConfig.WEB_START_URL)
        val startUrl = policy.startUrl
        if (startUrl == null) {
            showMessage(R.string.web_not_configured)
            return
        }

        configureWebView(policy)
        attachBridge(policy)
        onBackPressedDispatcher.addCallback(this, webBackCallback)
        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(startUrl)
        }
        webBackCallback.isEnabled = webView.canGoBack()
    }

    /** React 화면 실행에 JavaScript가 필요하다. 메인 프레임 탐색은 신뢰 origin으로 제한한다. */
    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(policy: WebNavigationPolicy) {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        webView.webViewClient = RestrictedWebViewClient(policy, object : RestrictedWebViewClient.Listener {
            /** 차단 안내 문구를 표시한다. */
            override fun onNavigationBlocked() = showMessage(R.string.web_navigation_blocked)

            /** 신뢰 페이지가 시작되면 안내 문구를 숨긴다. */
            override fun onTrustedPageStarted() {
                messageText.visibility = View.GONE
            }

            /** 웹 기록에 맞춰 뒤로 가기 처리를 켜거나 끈다. */
            override fun onHistoryChanged(canGoBack: Boolean) {
                webBackCallback.isEnabled = canGoBack
            }
        })
    }

    /** 페이지를 불러오기 전에 브리지를 붙인다. 지원하지 않는 WebView면 붙이지 않고 페이지만 표시한다(계약 밖, 미정). */
    private fun attachBridge(policy: WebNavigationPolicy) {
        val handler = BridgeMessageHandler(BridgeOrigins.trusted(policy), ActivationBridgePort(activation, authenticator))
        val candidate = WebMessageBridge(handler)
        if (candidate.attach(webView, BridgeContract.JS_OBJECT_NAME, BridgeOrigins.rules(policy))) {
            bridge = candidate
        }
    }

    /** 앱 이탈이면 활성 상태를 끄고 진행 중인 인증 취소를 요청한 뒤 웹에 상태를 보낸다. 화면 구성 변경은 제외한다. */
    override fun onStop() {
        super.onStop()
        activation.onScreenStopped(isChangingConfigurations, authenticator)
        if (!isChangingConfigurations) bridge?.pushState()
    }

    /** WebView 위에 안내 문구를 표시한다. */
    private fun showMessage(messageRes: Int) {
        messageText.setText(messageRes)
        messageText.visibility = View.VISIBLE
    }

    /** 화면 재생성에 대비해 WebView 상태를 저장한다. */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    /** 화면에서 분리한 뒤 WebView를 해제한다. */
    override fun onDestroy() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    /**
     * 응답 대기 시간은 AuthResponseTimeouts 기본값(인증 120초·정리 10초)을 잠정으로 쓴다.
     * 근거 없는 제안값이며 실기기 관측 전 미확정이다.
     */
    private object ActivationViewModelFactory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(VirtualCardActivationViewModel::class.java))
            return VirtualCardActivationViewModel(SystemClock::elapsedRealtime, AuthResponseTimeouts()) as T
        }
    }
}
