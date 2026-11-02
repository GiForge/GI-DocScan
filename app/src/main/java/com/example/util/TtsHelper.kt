package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Native Android TextToSpeech helper for reading scanned and OCR extracted text.
 * Configured with Italian locale fallback to device default, with speed adjustment,
 * state flows for Compose UI reactivity, and clean lifecycle shutdown.
 */
class TtsHelper(context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _statusText = MutableStateFlow("Pronto per la lettura vocale")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    init {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isReady = true
                    val langResult = tts?.setLanguage(Locale.ITALIAN)
                    if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.language = Locale.getDefault()
                    }
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isPlaying.value = true
                            _statusText.value = "Lettura vocale in corso..."
                        }

                        override fun onDone(utteranceId: String?) {
                            _isPlaying.value = false
                            _statusText.value = "Lettura completata"
                        }

                        override fun onError(utteranceId: String?) {
                            _isPlaying.value = false
                            _statusText.value = "Lettura interrotta o errore"
                        }
                    })
                } else {
                    Log.w("TtsHelper", "TextToSpeech initialization failed with status: $status")
                    _statusText.value = "Motore vocale non disponibile"
                }
            }
        } catch (e: Exception) {
            Log.e("TtsHelper", "Error creating TextToSpeech", e)
            _statusText.value = "Impossibile avviare motore vocale"
        }
    }

    fun speak(text: String) {
        if (!isReady || text.isBlank()) {
            _statusText.value = if (text.isBlank()) "Nessun testo da leggere" else "Motore vocale in caricamento..."
            return
        }
        try {
            tts?.setSpeechRate(_speechRate.value)
            _isPlaying.value = true
            _statusText.value = "Lettura vocale in corso..."
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "DOC_SCAN_TTS")
        } catch (e: Exception) {
            Log.e("TtsHelper", "Error during speak", e)
            _isPlaying.value = false
            _statusText.value = "Errore durante la lettura"
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w("TtsHelper", "Error stopping TTS", e)
        }
        _isPlaying.value = false
        _statusText.value = "Lettura fermata"
    }

    fun setRate(rate: Float) {
        _speechRate.value = rate
        try {
            tts?.setSpeechRate(rate)
        } catch (e: Exception) {
            Log.w("TtsHelper", "Error setting speech rate", e)
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w("TtsHelper", "Error shutting down TTS", e)
        }
        tts = null
        isReady = false
        _isPlaying.value = false
    }
}
