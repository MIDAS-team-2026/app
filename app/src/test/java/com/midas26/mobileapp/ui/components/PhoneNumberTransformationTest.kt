package com.midas26.mobileapp.ui.components

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

/** 전화번호 하이픈 표시와 커서 위치 변환 검증. */
class PhoneNumberTransformationTest {

    private fun transform(digits: String) =
        phoneNumberTransformation(digits).filter(AnnotatedString(digits))

    @Test
    fun showsHyphensByLength() {
        assertEquals("010", transform("010").text.text)
        assertEquals("010-1", transform("0101").text.text)
        assertEquals("010-1234-5678", transform("01012345678").text.text)
        assertEquals("011-234-5678", transform("0112345678").text.text)
    }

    @Test
    fun cursorStaysAtEndWhileTyping() {
        val number = "01012345678"
        for (length in 0..number.length) {
            val result = transform(number.take(length))
            assertEquals(result.text.length, result.offsetMapping.originalToTransformed(length))
            assertEquals(length, result.offsetMapping.transformedToOriginal(result.text.length))
        }
    }

    @Test
    fun everyCursorPositionMapsBackToItself() {
        for (number in listOf("01012345678", "0112345678")) {
            val mapping = transform(number).offsetMapping
            for (offset in 0..number.length) {
                assertEquals(offset, mapping.transformedToOriginal(mapping.originalToTransformed(offset)))
            }
        }
    }
}
