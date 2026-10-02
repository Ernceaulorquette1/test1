package cl.driverlink.app.services.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import cl.driverlink.app.domain.model.AudioClip
import java.io.File

/**
 * Grabador walkie-talkie. Usa AAC mono a 16 kHz / 32 kbps: audio de voz comprimido
 * (~4 KB por segundo) para controlar costos de Storage y datos móviles.
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt: Long = 0

    val isRecording: Boolean get() = recorder != null

    /** Comienza a grabar. [maxDurationMs] detiene la grabación automáticamente. */
    fun start(maxDurationMs: Int, onMaxReached: () -> Unit) {
        stopInternal()
        val dir = File(context.cacheDir, "radio").apply { mkdirs() }
        val file = File(dir, "clip_${System.currentTimeMillis()}.m4a")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioChannels(1)
            setAudioSamplingRate(16_000)
            setAudioEncodingBitRate(32_000)
            setMaxDuration(maxDurationMs)
            setOutputFile(file.absolutePath)
            setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onMaxReached()
            }
            prepare()
            start()
        }
        recorder = mediaRecorder
        outputFile = file
        startedAt = SystemClock.elapsedRealtime()
    }

    /** Finaliza la grabación y devuelve el clip, o null si falló. */
    fun stop(): AudioClip? {
        val file = outputFile
        val duration = SystemClock.elapsedRealtime() - startedAt
        val ok = stopInternal()
        if (!ok || file == null || !file.exists()) return null
        return AudioClip(file.absolutePath, duration)
    }

    /** Descarta la grabación en curso sin publicarla. */
    fun cancel() {
        val file = outputFile
        stopInternal()
        file?.delete()
    }

    private fun stopInternal(): Boolean {
        val current = recorder ?: return false
        recorder = null
        return try {
            current.stop()
            true
        } catch (e: RuntimeException) {
            // stop() falla si la grabación fue demasiado corta.
            outputFile?.delete()
            false
        } finally {
            current.release()
        }
    }
}
