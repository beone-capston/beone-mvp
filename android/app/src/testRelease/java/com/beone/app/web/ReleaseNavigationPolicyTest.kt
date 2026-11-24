package com.beone.app.web

import com.beone.app.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * release 시작 주소로 만든 탐색 정책이 웹 스킴이 아닌 주소를 거부하는지 고정한다(MVP Android 갭 AG-12, U6).
 * 공용 WebNavigationPolicyTest의 about:blank·blob: 검사와 대칭이다. 실제 WebView 동작은 확인하지 않는다.
 */
class ReleaseNavigationPolicyTest {

    private val policy = WebNavigationPolicy(BuildConfig.WEB_START_URL)

    /** 신뢰 origin 문자열을 담고 있어도 http·https가 아니면 origin이 없으므로 허용하지 않는다. */
    private val nonWebUrls = listOf(
        "about:blank",
        "blob:https://beonepay.site/0b6c0f8e-1d2a-4c5e-9f00-000000000000",
        "intent://beonepay.site/#Intent;scheme=https;package=com.beone.app;end",
        "market://details?id=com.beone.app",
    )

    /** 거부가 정책 설정 실패 때문이 아님을 먼저 확인한다. */
    @Test
    fun releaseStartUrlItselfIsAllowed() {
        assertTrue(policy.isAllowed(BuildConfig.WEB_START_URL))
    }

    @Test
    fun releaseRejectsBlankBlobIntentAndMarketUrls() {
        nonWebUrls.forEach { url ->
            assertNull(url, WebOrigin.parse(url))
            assertFalse(url, policy.isAllowed(url))
        }
    }

    @Test
    fun releaseBlocksBlankBlobIntentAndMarketMainFrameRequests() {
        nonWebUrls.forEach { url -> assertTrue(url, policy.shouldBlockRequest(url, isForMainFrame = true)) }
    }
}
