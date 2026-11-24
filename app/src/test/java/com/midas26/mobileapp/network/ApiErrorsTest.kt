package com.midas26.mobileapp.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 서버 실패 응답 본문에서 안내 문구를 꺼내는 함수 검증.
 */
class ApiErrorsTest {

    @Test
    fun readsMessageFromServerErrorBody() {
        val body = """{"code":400,"message":"이미 연동된 환자입니다.","data":null}"""

        assertEquals("이미 연동된 환자입니다.", parseServerMessage(body))
    }

    @Test
    fun returnsNullWhenBodyIsMissing() {
        assertNull(parseServerMessage(null))
        assertNull(parseServerMessage(""))
        assertNull(parseServerMessage("   "))
    }

    @Test
    fun returnsNullWhenBodyIsNotJson() {
        assertNull(parseServerMessage("<html>502 Bad Gateway</html>"))
    }

    @Test
    fun returnsNullWhenMessageIsMissingOrBlank() {
        assertNull(parseServerMessage("""{"code":500,"data":null}"""))
        assertNull(parseServerMessage("""{"code":500,"message":"","data":null}"""))
    }
}
