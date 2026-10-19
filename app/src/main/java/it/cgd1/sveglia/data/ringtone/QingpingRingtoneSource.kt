package it.cgd1.sveglia.data.ringtone

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import it.cgd1.sveglia.protocol.AudioUpload
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val LIST_URL = "https://qingplus.cleargrass.com/raw/rings"
private const val TIMEOUT_MS = 15_000
private const val ALLOWED_HOST_SUFFIX = ".cleargrass.com"
private const val MAX_LIST_BYTES = 64 * 1024
private val SIGNATURE = Regex("[0-9a-fA-F]{8}")

/**
 * Le suonerie originali, prese dallo stesso server da cui le scarica l'app Qingping. Non sono incluse
 * nell'app perché sono di Qingping: si scaricano solo quando l'utente le apre e restano in cache.
 * Due sole richieste GET: HttpURLConnection e org.json bastano, senza aggiungere dipendenze.
 */
@Singleton
class QingpingRingtoneSource @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val cacheDir get() = File(context.cacheDir, "qingping-rings").apply { mkdirs() }

    fun list(): List<OriginalRingtone> {
        val json = JSONObject(String(get(LIST_URL, MAX_LIST_BYTES), Charsets.UTF_8))
        return json.keys().asSequence().mapNotNull { signature ->
            val entry = json.optJSONObject(signature) ?: return@mapNotNull null
            val pcm = entry.optString("pcm")
            if (!SIGNATURE.matches(signature) || !isTrustedRingtoneUrl(pcm)) return@mapNotNull null
            OriginalRingtone(signature.lowercase(), entry.optString("name", signature), pcm)
        }.sortedBy { it.name }.toList()
    }

    fun pcm(ringtone: OriginalRingtone): ByteArray {
        val cached = File(cacheDir, "${ringtone.signatureHex}.pcm")
        if (cached.isFile && cached.length() > 0) return cached.readBytes()
        require(isTrustedRingtoneUrl(ringtone.pcmUrl)) { "URL non attendibile" }
        val data = get(ringtone.pcmUrl, AudioUpload.MAX_AUDIO_BYTES)
        if (data.isEmpty()) throw IOException("suoneria vuota")
        // Scrittura atomica: un file troncato da un'interruzione resterebbe in cache per sempre.
        val partial = File(cacheDir, "${ringtone.signatureHex}.part")
        partial.writeBytes(data)
        if (!partial.renameTo(cached)) throw IOException("cache non scrivibile")
        return data
    }

    private fun get(url: String, maxBytes: Int): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            // Un redirect porterebbe fuori dai server Qingping senza ripassare dal controllo dell'host.
            connection.instanceFollowRedirects = false
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            if (connection.contentLengthLong > maxBytes) throw IOException("risposta di ${connection.contentLengthLong} byte")
            return connection.inputStream.use { it.readAtMost(maxBytes) }
        } finally {
            connection.disconnect()
        }
    }
}

/** L'elenco arriva dalla rete: si scaricano file solo in HTTPS e solo dai server Qingping. */
internal fun isTrustedRingtoneUrl(url: String): Boolean = runCatching {
    val parsed = URL(url)
    parsed.protocol == "https" && parsed.host.endsWith(ALLOWED_HOST_SUFFIX)
}.getOrDefault(false)

/** Legge tutto lo stream ma rifiuta di superare [maxBytes], anche se il server non dichiara la lunghezza. */
internal fun InputStream.readAtMost(maxBytes: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val read = read(buffer)
        if (read < 0) return out.toByteArray()
        if (out.size() + read > maxBytes) throw IOException("risposta oltre $maxBytes byte")
        out.write(buffer, 0, read)
    }
}
