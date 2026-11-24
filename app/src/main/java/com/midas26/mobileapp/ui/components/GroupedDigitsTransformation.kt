package com.midas26.mobileapp.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** 숫자 원본은 그대로 두고 화면에만 구분 기호를 표시한다. */
internal class GroupedDigitsTransformation(
    private val separator: Char,
    private val boundaries: List<Int>
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val result = StringBuilder()
        val originalToDisplay = IntArray(raw.length + 1)
        val displayToOriginal = mutableListOf(0)

        for (i in raw.indices) {
            if (i in boundaries) {
                result.append(separator)
                displayToOriginal.add(i)
            }
            originalToDisplay[i] = result.length
            result.append(raw[i])
            displayToOriginal.add(i + 1)
        }
        originalToDisplay[raw.length] = result.length

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                originalToDisplay[offset.coerceIn(0, raw.length)]

            override fun transformedToOriginal(offset: Int): Int =
                displayToOriginal[offset.coerceIn(0, result.length)]
        }
        return TransformedText(AnnotatedString(result.toString()), mapping)
    }
}

private val phoneTransformation11 = GroupedDigitsTransformation('-', listOf(3, 7))
private val phoneTransformation10 = GroupedDigitsTransformation('-', listOf(3, 6))

/** 숫자만 담긴 전화번호를 자릿수(10자리·11자리)에 맞게 하이픈으로 끊어 보여준다. */
internal fun phoneNumberTransformation(digits: String): VisualTransformation =
    if (digits.length == 10) phoneTransformation10 else phoneTransformation11
