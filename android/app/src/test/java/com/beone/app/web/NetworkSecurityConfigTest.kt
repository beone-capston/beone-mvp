package com.beone.app.web

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * 소스의 네트워크 보안 설정과 Manifest 연결을 고정한다. 빌드 산출물이나 기기 동작을 확인하지 않는다.
 * 단위 테스트는 app 모듈 폴더에서 실행되므로 상대 경로로 소스 파일을 읽는다.
 */
class NetworkSecurityConfigTest {

    /** 소스 XML 파일을 네임스페이스를 인식해 읽는다. */
    private fun parse(path: String): Document {
        val file = File(path)
        assertTrue("파일 없음: $path", file.isFile)
        return DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)
    }

    /** 태그 이름이 같은 요소를 목록으로 돌려준다. */
    private fun Document.elements(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    /** 기본 설정이 평문을 막고 시스템 인증서만 믿는지 확인한다. */
    private fun Document.assertBaseDeniesCleartextAndTrustsSystemOnly() {
        val base = elements("base-config").single()
        assertEquals("false", base.getAttribute("cleartextTrafficPermitted"))
        assertEquals(listOf("system"), elements("certificates").map { it.getAttribute("src") })
    }

    /** main 설정은 어떤 주소에도 평문 HTTP를 허용하지 않는다. */
    @Test
    fun mainConfigDeniesCleartextEverywhere() {
        val config = parse("src/main/res/xml/network_security_config.xml")

        config.assertBaseDeniesCleartextAndTrustsSystemOnly()
        assertTrue(config.elements("domain-config").isEmpty())
        assertTrue(config.elements("debug-overrides").isEmpty())
    }

    /** debug 설정은 로컬 개발 주소에만 평문 HTTP를 허용한다. */
    @Test
    fun debugConfigAllowsCleartextOnlyForLocalDevHosts() {
        val config = parse("src/debug/res/xml/network_security_config.xml")

        config.assertBaseDeniesCleartextAndTrustsSystemOnly()
        val domainConfig = config.elements("domain-config").single()
        assertEquals("true", domainConfig.getAttribute("cleartextTrafficPermitted"))
        val domains = config.elements("domain")
        assertEquals(setOf("localhost", "127.0.0.1", "10.0.2.2"), domains.map { it.textContent.trim() }.toSet())
        assertEquals(3, domains.size)
        domains.forEach { assertEquals("false", it.getAttribute("includeSubdomains")) }
    }

    /** Manifest는 설정 파일을 쓰고 전역 평문 허용·debuggable을 두지 않는다. */
    @Test
    fun manifestUsesNetworkSecurityConfigWithoutGlobalCleartext() {
        val application = parse("src/main/AndroidManifest.xml").elements("application").single()

        assertEquals(
            "@xml/network_security_config",
            application.getAttributeNS(ANDROID_NS, "networkSecurityConfig"),
        )
        assertFalse(application.hasAttributeNS(ANDROID_NS, "usesCleartextTraffic"))
        assertFalse(application.hasAttributeNS(ANDROID_NS, "debuggable"))
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
