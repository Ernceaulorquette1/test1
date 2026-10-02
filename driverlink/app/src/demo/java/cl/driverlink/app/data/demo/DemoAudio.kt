package cl.driverlink.app.data.demo

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Genera audios de demostración (tonos WAV) para poder probar la Radio sin micrófono
 * ni Storage. Solo existe en el flavor demo.
 */
object DemoAudio {
    private const val SAMPLE_RATE = 8_000

    /** Crea (si no existe) un WAV de [seconds] segundos y devuelve su ruta. */
    fun ensureTone(dir: File, name: String, seconds: Int, frequency: Double): String {
        dir.mkdirs()
        val file = File(dir, "$name.wav")
        if (file.exists()) return file.absolutePath
        val samples = SAMPLE_RATE * seconds
        val data = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until samples) {
            // Pulsos cortos tipo "bip" con envolvente para evitar chasquidos.
            val t = i.toDouble() / SAMPLE_RATE
            val gate = if ((t * 4).toInt() % 2 == 0) 1.0 else 0.0
            val fade = min(1.0, min(i, samples - i) / 400.0)
            val value = sin(2 * PI * frequency * t) * 0.35 * gate * fade
            data.putShort((value * Short.MAX_VALUE).toInt().toShort())
        }
        FileOutputStream(file).use { out ->
            out.write(wavHeader(samples * 2))
            out.write(data.array())
        }
        return file.absolutePath
    }

    private fun wavHeader(dataLength: Int): ByteArray = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(36 + dataLength); put("WAVE".toByteArray())
        put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
        putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2); putShort(2); putShort(16)
        put("data".toByteArray()); putInt(dataLength)
    }.array()
}
