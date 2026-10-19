package it.cgd1.sveglia.data.ringtone

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import it.cgd1.sveglia.protocol.AudioUpload
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val LIST_URL = "https://qingplus.cleargrass.com/raw/rings"
private const val TIMEOUT_MS = 15_000
private const val ALLOWED_HOST_SUFFIX = ".cleargrass.com"

/**
 * Le suonerie originali, prese dallo stesso server da cui le scarica l'app Qingping. Non sono incluse
 * nell'app perché sono di Qingping: si scaricano solo quando l'utente le apre e restano in cache.
 * Due sole richieste GET: HttpURLConnection e org.json bastano, senza aggiungere dipendenze.
 */
@Singleton
class QingpingRingtoneSource @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val cacheDir get() = File(context.cacheDir, "qingping-rings").apply { mkdirs() }

    fun list(): List<OriginalRingtone> {
        val json = JSONObject(String(get(LIST_URL), Charsets.UTF_8))
        return json.keys().asSequence().mapNotNull { signature ->
            val entry = json.optJSONObject(signature) ?: return@mapNotNull null
            val pcm = entry.optString("pcm")
            if (!SIGNATURE.matches(signature) || !isTrusted(pcm)) return@mapNotNull null
            OriginalRingtone(signature.lowercase(), entry.optString("name", signature), pcm)
        }.sortedBy { it.name }.toList()
    }

    fun pcm(ringtone: OriginalRingtone): ByteArray {
        val cached = File(cacheDir, "${ringtone.signatureHex}.pcm")
        if (cached.isFile && cached.length() > 0) return cached.readBytes()
        require(isTrusted(ringtone.pcmUrl)) { "URL non attendibile" }
        val data = get(ringtone.pcmUrl)
        if (data.isEmpty() || data.size > AudioUpload.MAX_AUDIO_BYTES) throw IOException("suoneria di ${data.size} byte")
        cached.writeBytes(data)
        return data
    }

    // L'elenco arriva dalla rete: si scaricano file solo in HTTPS e solo dai server Qingping.
    private fun isTrusted(url: String): Boolean = runCatching {
        val parsed = URL(url)
        parsed.protocol == "https" && parsed.host.endsWith(ALLOWED_HOST_SUFFIX)
    }.getOrDefault(false)

    private fun get(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        val SIGNATURE = Regex("[0-9a-fA-F]{8}")
    }
}
