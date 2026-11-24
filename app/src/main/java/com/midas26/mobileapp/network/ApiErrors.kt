package com.midas26.mobileapp.network

import com.google.gson.Gson

/**
 * 서버가 실패 응답 본문({ "code": ..., "message": "...", "data": null })에 담아 준 안내 문구를 꺼낸다.
 *
 * Retrofit 은 4xx/5xx 응답의 본문을 body() 가 아니라 errorBody() 로 준다.
 * body()?.message 로 읽으면 항상 null 이라 서버가 알려 준 실패 사유를 화면에 보여줄 수 없다.
 *
 * @return 서버 문구. 본문이 없거나 형식이 다르면 null.
 */
fun parseServerMessage(errorBodyJson: String?): String? {
    if (errorBodyJson.isNullOrBlank()) return null
    return try {
        Gson().fromJson(errorBodyJson, ApiResponse::class.java)?.message?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }
}
