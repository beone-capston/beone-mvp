package com.beone.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * debug 전용 코드와 로그 출력이 main 소스에 들어오지 않았는지 파일 단위로 고정한다(MVP Android 갭 AG-10, U3).
 * release APK 내용은 확인하지 않는다. 단위 테스트는 app 모듈 폴더에서 실행되므로 상대 경로로 읽는다.
 */
class MainSourceSetGuardTest {

    private val mainDir = File("src/main")
    private val debugDir = File("src/debug")

    private fun sourceFiles(dir: File): List<File> =
        dir.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java") }.toList()

    /** 경로 해석이 틀려 빈 목록으로 통과하는 일을 막기 위해 debug 쪽 파일이 실제로 있는지 먼저 본다. */
    @Test
    fun debugOnlyAuthFilesExistOnlyUnderDebug() {
        val debugNames = sourceFiles(debugDir).map { it.nameWithoutExtension }.toSet()
        assertTrue(debugNames.containsAll(DEBUG_ONLY_NAMES))

        val inMain = sourceFiles(mainDir).filter { it.nameWithoutExtension in DEBUG_ONLY_NAMES }
        assertEquals(emptyList<File>(), inMain)
    }

    @Test
    fun mainSourcesDoNotImportAndroidLog() {
        val sources = sourceFiles(mainDir)
        assertTrue("main 소스 파일 없음", sources.isNotEmpty())

        val offenders = sources.filter { file -> file.readLines().any { LOG_IMPORT.containsMatchIn(it) } }
        assertEquals(emptyList<File>(), offenders)
    }

    private companion object {
        val DEBUG_ONLY_NAMES = setOf("DebugAuthTrace", "DeviceAuthCheckActivity")
        val LOG_IMPORT = Regex("""^\s*import\s+android\.util\.Log\b""")
    }
}
