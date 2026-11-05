package com.beone.app.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 주소 해석과 탐색 허용·차단 판정을 고정한다. 실제 WebView 동작은 확인하지 않는다. */
class WebNavigationPolicyTest {

    private val devPolicy = WebNavigationPolicy("http://localhost:5173/")
    private val httpsPolicy = WebNavigationPolicy("https://example.test/app/")

    /** 기본 포트와 명시 포트를 같은 origin으로 해석한다. */
    @Test
    fun parsesOriginWithDefaultAndExplicitPorts() {
        assertEquals(WebOrigin("https", "example.test", 443), WebOrigin.parse("https://example.test/a?b=c#d"))
        assertEquals(WebOrigin("https", "example.test", 443), WebOrigin.parse("https://example.test:443/"))
        assertEquals(WebOrigin("http", "localhost", 5173), WebOrigin.parse("http://localhost:5173"))
        assertEquals(WebOrigin.parse("http://localhost/"), WebOrigin.parse("http://localhost:80/"))
    }

    /** 스킴과 호스트의 대소문자를 구분하지 않는다. */
    @Test
    fun hostAndSchemeAreCaseInsensitive() {
        assertEquals(WebOrigin.parse("https://example.test/"), WebOrigin.parse("HTTPS://Example.TEST/"))
    }

    /** 웹 스킴이 아니거나 형식이 잘못된 주소를 거부한다. */
    @Test
    fun rejectsNonWebSchemesAndMalformedUrls() {
        listOf(
            "file:///android_asset/index.html",
            "content://com.beone.app/x",
            "javascript:alert(1)",
            "data:text/html,hi",
            "intent://scan#Intent;end",
            "/relative/path",
            "https://",
            "http://exa mple.test/",
            "",
        ).forEach { url -> assertNull(url, WebOrigin.parse(url)) }
    }

    /** 같은 origin이면 경로와 쿼리가 달라도 허용한다. */
    @Test
    fun allowsSameOriginPaths() {
        assertTrue(devPolicy.isAllowed("http://localhost:5173/"))
        assertTrue(devPolicy.isAllowed("http://localhost:5173/orders/1?x=1#top"))
        assertTrue(httpsPolicy.isAllowed("https://example.test/other"))
    }

    /** 포트·스킴·호스트 중 하나라도 다르면 거부한다. */
    @Test
    fun rejectsDifferentPortSchemeOrHost() {
        assertFalse(devPolicy.isAllowed("http://localhost:8080/"))
        assertFalse(devPolicy.isAllowed("https://localhost:5173/"))
        assertFalse(devPolicy.isAllowed("http://127.0.0.1:5173/"))
        assertFalse(httpsPolicy.isAllowed("http://example.test/"))
        assertFalse(httpsPolicy.isAllowed("https://example.test.evil.test/"))
        assertFalse(httpsPolicy.isAllowed("https://evil.test/?next=https://example.test/"))
    }

    /** 사용자 정보 부분으로 호스트를 속일 수 없다. */
    @Test
    fun userInfoDoesNotChangeTheHost() {
        assertFalse(httpsPolicy.isAllowed("https://example.test@evil.test/"))
    }

    /** 웹 스킴이 아닌 주소는 허용하지 않는다. */
    @Test
    fun rejectsNonWebSchemesEvenWhenConfigured() {
        assertFalse(httpsPolicy.isAllowed("javascript:alert(1)"))
        assertFalse(httpsPolicy.isAllowed("file:///android_asset/index.html"))
    }

    /** about:blank·blob: 주소는 신뢰 origin이 아니므로 메인 프레임 탐색에서 막는다. */
    @Test
    fun blankAndBlobUrlsAreNotTrusted() {
        assertFalse(devPolicy.isAllowed("about:blank"))
        assertFalse(devPolicy.isAllowed("blob:http://localhost:5173/0b6c0f8e-1d2a-4c5e-9f00-000000000000"))
    }

    /** 설정이 없으면 아무 주소도 허용하지 않는다. */
    @Test
    fun missingConfigurationAllowsNothing() {
        val policy = WebNavigationPolicy("")

        assertNull(policy.trustedOrigin)
        assertNull(policy.startUrl)
        assertFalse(policy.isAllowed("https://example.test/"))
    }

    /** 설정이 잘못되면 아무 주소도 허용하지 않는다. */
    @Test
    fun invalidConfigurationAllowsNothing() {
        listOf("not a url", "file:///android_asset/index.html", "ftp://example.test/", "http://localhost:65536/").forEach { configured ->
            val policy = WebNavigationPolicy(configured)
            assertNull(configured, policy.startUrl)
            assertFalse(configured, policy.isAllowed(configured))
        }
    }

    /** 포트 유효 범위의 양 끝은 받고 범위를 넘으면 거부한다. */
    @Test
    fun acceptsPortBoundsAndRejectsOutOfRange() {
        assertEquals(WebOrigin("http", "localhost", 65535), WebOrigin.parse("http://localhost:65535/"))
        assertEquals(WebOrigin("http", "localhost", 0), WebOrigin.parse("http://localhost:0/"))
        assertNull(WebOrigin.parse("http://localhost:65536/"))
        assertNull(WebOrigin.parse("https://example.test:65536/"))
    }

    /** 포트 범위 검사를 거쳐도 기본 포트 처리는 그대로다. */
    @Test
    fun omittedPortStillUsesSchemeDefault() {
        assertEquals(WebOrigin("http", "localhost", 80), WebOrigin.parse("http://localhost/"))
        assertEquals(WebOrigin("https", "example.test", 443), WebOrigin.parse("https://example.test/"))
    }

    /** 신뢰 origin 밖 메인 프레임 요청만 막고 신뢰 origin의 요청은 막지 않는다. */
    @Test
    fun blocksOnlyUntrustedMainFrameRequests() {
        assertTrue(devPolicy.shouldBlockRequest("http://localhost:8080/", isForMainFrame = true))
        assertTrue(devPolicy.shouldBlockRequest("https://evil.test/submit", isForMainFrame = true))
        assertFalse(devPolicy.shouldBlockRequest("http://localhost:5173/submit", isForMainFrame = true))
    }

    /** 하위 리소스·iframe 요청은 판정 대상이 아니므로 다른 origin의 API 호출을 막지 않는다. */
    @Test
    fun doesNotJudgeSubresourceOrFrameRequests() {
        assertFalse(devPolicy.shouldBlockRequest("http://localhost:8080/api", isForMainFrame = false))
        assertFalse(devPolicy.shouldBlockRequest("http://localhost:5173/a.js", isForMainFrame = false))
    }

    /** 주소 설정이 없으면 메인 프레임 요청을 모두 막는다. */
    @Test
    fun missingConfigurationBlocksMainFrameRequests() {
        assertTrue(WebNavigationPolicy("").shouldBlockRequest("https://example.test/", isForMainFrame = true))
    }

    /** 시작 주소의 경로를 유지한다. */
    @Test
    fun startUrlKeepsConfiguredPath() {
        assertEquals("https://example.test/app/", httpsPolicy.startUrl)
    }
}
