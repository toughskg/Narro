package com.narro.app.feature

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportScreenTest {
    @Test
    fun onlyHttpsKoFiLinksAreTrusted() {
        assertTrue(isTrustedKoFiUrl("https://ko-fi.com/narro"))
        assertTrue(isTrustedKoFiUrl("https://www.ko-fi.com/narro"))
        assertFalse(isTrustedKoFiUrl("http://ko-fi.com/narro"))
        assertFalse(isTrustedKoFiUrl("https://example.com/ko-fi.com/narro"))
        assertFalse(isTrustedKoFiUrl(""))
    }

    @Test
    fun koreanSupportMessagesHaveStableIds() {
        val messageDocument = projectFile("message_ko.md").readText()

        assertTrue(messageDocument.contains("msg_056=응원 페이지를 준비 중입니다."))
        assertTrue(messageDocument.contains("alt_041=$%1\$d로 Narro를 응원하시겠습니까?"))
        assertTrue(messageDocument.contains("alt_042=계속하면 Ko-fi 결제 페이지가 열립니다."))
        assertTrue(messageDocument.contains("err_061=응원 페이지를 열 수 없습니다."))
    }

    private fun projectFile(relativePath: String): File {
        val workingDirectory = File(System.getProperty("user.dir") ?: error("user.dir is unavailable"))
        return sequenceOf(
            File(workingDirectory, relativePath),
            File(workingDirectory.parentFile, relativePath),
        ).first { it.isFile }
    }
}
