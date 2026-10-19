package it.cgd1.sveglia.data.ringtone

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import it.cgd1.sveglia.audio.PcmProcessing
import it.cgd1.sveglia.di.DefaultDispatcher
import it.cgd1.sveglia.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RingtoneRepository"

@Singleton
class AndroidRingtoneRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val qingping: QingpingRingtoneSource,
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : RingtoneRepository {

    override val builtIns: List<BuiltInRingtone> = BUILT_IN_RINGTONES

    override suspend fun originals(): Result<List<OriginalRingtone>> = network { qingping.list() }

    override suspend fun loadPcm(ringtone: RingtoneOption): Result<ByteArray> = when (ringtone) {
        is BuiltInRingtone -> withContext(io) {
            Result.success(context.resources.openRawResource(ringtone.rawRes).use { it.readBytes() })
        }
        is OriginalRingtone -> network { qingping.pcm(ringtone) }
    }

    private suspend fun <T> network(block: () -> T): Result<T> = withContext(io) {
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "ringtone download failed", e)
            Result.failure(RingtoneError.Network(e))
        }
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
