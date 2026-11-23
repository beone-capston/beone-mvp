package com.beone.app.bridge

import com.beone.app.web.WebNavigationPolicy
import com.beone.app.web.WebOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 브리지 허용 origin 규칙은 탐색 정책의 신뢰 origin 하나를 와일드카드 없이 정확히 적는다. */
class BridgeOriginsTest {

    @Test
    fun ruleOmitsDefaultPortOnly() {
        assertEquals("https://beonepay.site", BridgeOrigins.rule(WebOrigin("https", "beonepay.site", 443)))
        assertEquals("http://localhost:5173", BridgeOrigins.rule(WebOrigin("http", "localhost", 5173)))
        assertEquals("http://localhost", BridgeOrigins.rule(WebOrigin("http", "localhost", 80)))
        assertEquals("https://example.test:8443", BridgeOrigins.rule(WebOrigin("https", "example.test", 8443)))
    }

    @Test
    fun rulesComeFromStartUrlOrigin() {
        assertEquals(setOf("https://beonepay.site"), BridgeOrigins.rules(WebNavigationPolicy("https://beonepay.site/")))
        assertEquals(setOf("https://beonepay.site"), BridgeOrigins.rules(WebNavigationPolicy("https://beonepay.site:443/app?x=1")))
    }

    @Test
    fun missingOrInvalidStartUrlGivesNoRules() {
        assertTrue(BridgeOrigins.rules(WebNavigationPolicy("")).isEmpty())
        assertTrue(BridgeOrigins.rules(WebNavigationPolicy("file:///android_asset/index.html")).isEmpty())
        assertTrue(BridgeOrigins.trusted(WebNavigationPolicy("not a url")).isEmpty())
    }
}
