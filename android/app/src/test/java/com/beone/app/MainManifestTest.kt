package com.beone.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * main 소스의 Manifest 구성 요소를 고정한다(MVP Android 갭 AG-10·AG-13, U2).
 * 화면 진입점은 MainActivity 하나이고 외부 링크로 앱을 여는 딥링크가 없으며, debug 전용 점검 화면이 main에 없어야 한다.
 * 병합된 Manifest나 빌드 산출물은 확인하지 않는다. 단위 테스트는 app 모듈 폴더에서 실행되므로 상대 경로로 읽는다.
 */
class MainManifestTest {

    private val manifestFile = File("src/main/AndroidManifest.xml")

    private val manifest: Document by lazy {
        assertTrue("파일 없음: $manifestFile", manifestFile.isFile)
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(manifestFile)
    }

    /** 태그 이름이 같은 요소를 목록으로 돌려준다. */
    private fun elements(tag: String): List<Element> {
        val nodes = manifest.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.androidName(): String = getAttributeNS(ANDROID_NS, "name")

    @Test
    fun mainDeclaresOnlyMainActivity() {
        assertEquals(listOf(".MainActivity"), elements("activity").map { it.androidName() })
        assertTrue(elements("activity-alias").isEmpty())
    }

    @Test
    fun mainHasNoViewOrBrowsableIntentFilter() {
        val actions = elements("action").map { it.androidName() }
        val categories = elements("category").map { it.androidName() }

        assertFalse(actions.contains("android.intent.action.VIEW"))
        assertFalse(categories.contains("android.intent.category.BROWSABLE"))
        assertTrue("딥링크용 data 요소 없음", elements("data").isEmpty())
    }

    @Test
    fun mainDoesNotMentionDebugAuthCheckActivity() {
        assertFalse(manifestFile.readText().contains("DeviceAuthCheckActivity"))
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
