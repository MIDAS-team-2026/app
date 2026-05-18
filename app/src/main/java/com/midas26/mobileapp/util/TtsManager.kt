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
    private val mainHandler = Handler(Looper.getMainLooper())

    private val tts = TextToSpeech(context.applicationContext) { status ->
        initialized = status == TextToSpeech.SUCCESS
        if (initialized) {
            tts.language = Locale.KOREAN
            pendingSpeech?.let { (text, onDone) ->
                pendingSpeech = null
                speakInternal(text, onDone)
            }
        }
    }

    init {
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
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

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
