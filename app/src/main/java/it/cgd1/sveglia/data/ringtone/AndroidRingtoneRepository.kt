package it.cgd1.sveglia.data.ringtone

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import it.cgd1.sveglia.audio.PcmProcessing
import it.cgd1.sveglia.di.DefaultDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RingtoneRepository"

@Singleton
class AndroidRingtoneRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
) : RingtoneRepository {

    override val builtIns: List<BuiltInRingtone> = BUILT_IN_RINGTONES

    override suspend fun loadPcm(ringtone: BuiltInRingtone): ByteArray = withContext(dispatcher) {
        context.resources.openRawResource(ringtone.rawRes).use { it.readBytes() }
    }

    override suspend fun convert(uri: String): Result<CustomRingtone> = withContext(dispatcher) {
        val parsed = uri.toUri()
        try {
            val decoded = decodeAudio(context, parsed)
            if (decoded.samples.isEmpty()) return@withContext Result.failure(RingtoneError.Empty())
            val pcm = PcmProcessing.toCgd1Pcm(decoded.samples, decoded.sampleRate)
            Result.success(CustomRingtone(displayName(parsed), pcm))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "audio conversion failed", e)
            Result.failure(RingtoneError.Unsupported(e))
        }
    }

    private fun displayName(uri: Uri): String {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        return (name ?: uri.lastPathSegment.orEmpty()).substringBeforeLast('.')
    }
}
