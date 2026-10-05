package moe.comico.reader

import kotlinx.coroutines.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resumeWithException

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { response.close() }
        }
    })
}

/** Cancel the HTTP call even when cancellation happens during a blocked body read. */
suspend fun <T> Call.withResponse(block: (Response) -> T): T = coroutineScope {
    val cancellation = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
        try { awaitCancellation() } finally { this@withResponse.cancel() }
    }
    try {
        awaitResponse().use(block)
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        throw e
    } finally {
        cancellation.cancel()
    }
}
