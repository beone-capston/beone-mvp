package com.beone.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.beone.app.web.RestrictedWebViewClient
import com.beone.app.web.WebNavigationPolicy

/** React 화면을 표시할 WebView 셸. 웹과의 브리지는 계약이 정해지기 전까지 노출하지 않는다. */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var messageText: TextView

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

        val policy = WebNavigationPolicy(BuildConfig.WEB_START_URL)
        val startUrl = policy.startUrl
        if (startUrl == null) {
            showMessage(R.string.web_not_configured)
            return
        }

        configureWebView(policy)
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
}
