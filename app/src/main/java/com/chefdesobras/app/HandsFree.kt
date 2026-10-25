package com.chefdesobras.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

enum class VoiceCommand { NEXT, PREVIOUS, REPEAT, PAUSE, START, WHY, SUBSTITUTE }

/**
 * Modo manos libres: lee los pasos en voz alta y escucha órdenes como «siguiente» o «repite».
 * Mientras habla deja de escuchar, para no oírse a sí mismo. Todo ocurre en el hilo principal.
 */
class HandsFreeController(context: Context) {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    /** Se asignan en cada recomposición para que siempre usen el estado más reciente. */
    var onCommand: (VoiceCommand) -> Unit = {}
    var onProblem: (String) -> Unit = {}

    var active by mutableStateOf(false)
        private set
    var listening by mutableStateOf(false)
        private set
    var lastHeard by mutableStateOf<String?>(null)
        private set

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speaking = false
    private var pendingSpeech: String? = null
    private var recognizer: SpeechRecognizer? = null
    private var errorStreak = 0

    private val listenRunnable = Runnable { listen() }
    private val clearHeardRunnable = Runnable { lastHeard = null }

    /** Devuelve false si el teléfono no tiene reconocimiento de voz. */
    fun start(): Boolean {
        if (active) return true
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) return false
        active = true
        errorStreak = 0
        if (tts == null) {
            tts = TextToSpeech(appContext) { status -> handler.post { onTtsReady(status) } }
        }
        if (recognizer == null) {
            recognizer = try {
                SpeechRecognizer.createSpeechRecognizer(appContext).also { it.setRecognitionListener(listener) }
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo crear el reconocedor", e)
                null
            }
        }
        if (recognizer == null) {
            active = false
            return false
        }
        listen()
        return true
    }

    fun stop() {
        active = false
        listening = false
        speaking = false
        pendingSpeech = null
        handler.removeCallbacks(listenRunnable)
        recognizer?.cancel()
        tts?.stop()
    }

    fun release() {
        stop()
        recognizer?.destroy()
        recognizer = null
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    fun speak(text: String) {
        if (!active || text.isBlank()) return
        val engine = tts
        if (engine == null || !ttsReady) {
            pendingSpeech = text
            return
        }
        speaking = true
        handler.removeCallbacks(listenRunnable)
        recognizer?.cancel()
        listening = false
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    private fun onTtsReady(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            onProblem("No pude activar la voz del teléfono; igual te escucho.")
            return
        }
        val preferred = Locale.getDefault().takeIf { it.language == "es" } ?: Locale("es", "ES")
        val result = engine.setLanguage(preferred)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale("es"))
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                handler.post { onSpeechFinished() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                handler.post { onSpeechFinished() }
            }
        })
        ttsReady = true
        val pending = pendingSpeech
        pendingSpeech = null
        if (pending != null) speak(pending)
    }

    private fun onSpeechFinished() {
        speaking = false
        if (active) scheduleListen(250)
    }

    private fun languageTag(): String =
        Locale.getDefault().takeIf { it.language == "es" }?.toLanguageTag() ?: "es-ES"

    private fun listen() {
        if (!active || speaking) return
        val rec = recognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        }
        try {
            rec.startListening(intent)
            listening = true
        } catch (e: Exception) {
            Log.w(TAG, "startListening falló", e)
            listening = false
            scheduleListen(1500)
        }
    }

    private fun scheduleListen(delayMs: Long) {
        handler.removeCallbacks(listenRunnable)
        handler.postDelayed(listenRunnable, delayMs)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            listening = true
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onError(error: Int) {
            listening = false
            if (!active || speaking) return
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    stop()
                    onProblem("Necesito permiso de micrófono para escucharte.")
                }
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    // Silencio o algo que no entendió: es lo normal, se vuelve a escuchar.
                    errorStreak = 0
                    scheduleListen(200)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    recognizer?.cancel()
                    scheduleListen(700)
                }
                else -> {
                    errorStreak++
                    if (errorStreak >= MAX_ERRORS) {
                        stop()
                        onProblem("El reconocimiento de voz no responde. Revisa tu conexión e inténtalo de nuevo.")
                    } else {
                        scheduleListen((600L * errorStreak).coerceAtMost(5000L))
                    }
                }
            }
        }

        override fun onResults(results: Bundle?) {
            listening = false
            errorStreak = 0
            val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            if (heard.isNotEmpty()) {
                lastHeard = heard.first()
                handler.removeCallbacks(clearHeardRunnable)
                handler.postDelayed(clearHeardRunnable, 3500)
            }
            val command = heard.firstNotNullOfOrNull { parseVoiceCommand(it) }
            if (command != null) onCommand(command)
            if (active && !speaking) scheduleListen(150)
        }
    }

    private companion object {
        const val TAG = "HandsFree"
        const val UTTERANCE_ID = "chefcito"
        const val MAX_ERRORS = 8
    }
}

/** Traduce lo que dijo el usuario a una orden; null si no es ninguna. */
fun parseVoiceCommand(text: String): VoiceCommand? {
    val normalized = RecipeParser.normalize(text)
    val words = normalized.split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
    fun has(vararg options: String) = options.any { it in words }
    return when {
        "por que" in normalized || "porque" in words -> VoiceCommand.WHY
        has("sustituir", "sustituye", "sustituto", "reemplazar", "reemplaza", "reemplazo", "cambiar") ||
            "no tengo" in normalized -> VoiceCommand.SUBSTITUTE
        has("repite", "repetir", "repiteme", "lee", "leer", "leelo") || "otra vez" in normalized ||
            "de nuevo" in normalized -> VoiceCommand.REPEAT
        has("siguiente", "proximo", "avanza", "avanzar", "continua", "continuar", "listo", "lista", "sigue") -> VoiceCommand.NEXT
        has("anterior", "atras", "regresa", "regresar", "vuelve", "volver") -> VoiceCommand.PREVIOUS
        has("pausa", "pausar", "pausalo", "deten", "detener", "alto", "espera") || words == listOf("para") -> VoiceCommand.PAUSE
        has(
            "inicia", "iniciar", "empieza", "empezar", "comienza", "comenzar", "reanuda", "reanudar",
            "temporizador", "cronometro", "arranca", "dale", "start",
        ) -> VoiceCommand.START
        else -> null
    }
}
