package com.example.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class VoiceController(context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val spokenClipIds = mutableSetOf<String>()

    init {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.US)
                    ready = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
                } else {
                    Log.w("VoiceController", "TTS init status=$status")
                }
            }
        } catch (e: Exception) {
            Log.w("VoiceController", "TTS unavailable: ${e.message}")
        }
    }

    fun resetPlaybackQueue() {
        spokenClipIds.clear()
        tts?.stop()
    }

    fun triggerClipDialogueIfNeeded(
        clipId: String,
        text: String,
        pitch: Float,
        rate: Float,
    ) {
        if (!ready || text.isBlank() || clipId in spokenClipIds) return
        spokenClipIds.add(clipId)
        speakNow(text = text, pitch = pitch, rate = rate, flush = false)
    }

    fun previewVoice(text: String, pitch: Float, rate: Float) {
        if (!ready) return
        val sample = text.ifBlank { "Hello! Ready for action on StageCraft." }
        speakNow(text = sample, pitch = pitch, rate = rate, flush = true)
    }

    private fun speakNow(text: String, pitch: Float, rate: Float, flush: Boolean) {
        val engine = tts ?: return
        try {
            engine.setPitch(pitch.coerceIn(0.5f, 2.0f))
            engine.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
            val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(text, mode, null, "stagecraft_${System.nanoTime()}")
        } catch (e: Exception) {
            Log.w("VoiceController", "TTS speak failed: ${e.message}")
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        ready = false
    }
}
