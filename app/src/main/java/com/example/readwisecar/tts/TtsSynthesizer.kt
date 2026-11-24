package com.example.readwisecar.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

interface TtsSynthesizer {
    suspend fun synthesize(text: String, outFile: File): File
    fun release()
}

class AndroidTtsSynthesizer(context: Context) : TtsSynthesizer {
    private val ready = CompletableDeferred<TextToSpeech>()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<File>>()
    private var engine: TextToSpeech? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.US
                ready.complete(engine!!)
            } else {
                ready.completeExceptionally(IllegalStateException("TTS init failed: $status"))
            }
        }.apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    utteranceId?.let { pending.remove(it)?.complete(File(it)) }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    utteranceId?.let {
                        pending.remove(it)?.completeExceptionally(RuntimeException("TTS error on $it"))
                    }
                }
            })
        }
    }

    override suspend fun synthesize(text: String, outFile: File): File {
        if (outFile.exists() && outFile.length() > 128) return outFile
        val tts = ready.await()
        val id = outFile.absolutePath
        val deferred = CompletableDeferred<File>()
        pending[id] = deferred
        val res = tts.synthesizeToFile(text, null, outFile, id)
        if (res != TextToSpeech.SUCCESS) {
            pending.remove(id)
            error("synthesizeToFile returned $res")
        }
        return deferred.await()
    }

    override fun release() {
        engine?.stop()
        engine?.shutdown()
    }
}
