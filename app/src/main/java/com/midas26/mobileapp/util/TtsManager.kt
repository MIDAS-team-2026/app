package com.midas26.mobileapp.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class TtsManager(context: Context) {

    private var initialized = false
    private var pendingSpeech: Pair<String, (() -> Unit)?>? = null
    private var doneCallback: (() -> Unit)? = null
    private var speed: Float = 1.0f
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            initialized = status == TextToSpeech.SUCCESS
            if (initialized) {
                tts.language = Locale.KOREAN
                pendingSpeech?.let { (text, onDone) ->
                    pendingSpeech = null
                    speakInternal(text, onDone)
                }
            }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                mainHandler.post { doneCallback?.invoke() }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post { doneCallback?.invoke() }
            }
        })
    }

    fun setSpeed(speed: Float) {
        this.speed = speed
        tts.setSpeechRate(speed)
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (initialized) {
            speakInternal(text, onDone)
        } else {
            pendingSpeech = text to onDone
        }
    }

    private fun speakInternal(text: String, onDone: (() -> Unit)?) {
        doneCallback = onDone
        tts.setSpeechRate(speed)
        tts.speak(stripEmojis(text), TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    private fun stripEmojis(text: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (!isEmoji(cp)) sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return sb.toString().trim()
    }

    private fun isEmoji(cp: Int): Boolean =
        cp in 0x1F000..0x1FFFF ||  // 이모지 보충 다국어 평면
        cp in 0x2600..0x27BF  ||   // 기타 기호·딩뱃
        cp in 0x2300..0x23FF  ||   // 기술 기호
        cp in 0xFE00..0xFE0F  ||   // 변형 선택자
        cp == 0x200D               // 폭 없는 접합자

    fun stop() {
        doneCallback = null
        pendingSpeech = null
        if (initialized) tts.stop()
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    companion object {
        private const val UTTERANCE_ID = "midas_voicechat"
    }
}
