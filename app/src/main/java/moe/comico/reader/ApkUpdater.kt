package moe.comico.reader

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

private const val MAX_APK_SIZE = 200 * 1024 * 1024L
fun matchesUpdateHash(expected: String?, bytes: ByteArray): Boolean =
    expected == null || expected.equals(bytes.joinToString("") { "%02x".format(it) }, ignoreCase = true)

suspend fun downloadUpdate(context: Context, release: AppRelease, progress: (Float?) -> Unit): File = withContext(Dispatchers.IO) {
    val directory = File(context.cacheDir, "updates").apply { mkdirs() }
    val partial = File(directory, "update.part")
    val finished = File(directory, "update.apk")
    try {
        require(release.size in 0..MAX_APK_SIZE) { "This update is too large to download." }
        val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.MINUTES).readTimeout(30, TimeUnit.SECONDS).build()
        client.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { response ->
            if(!response.isSuccessful) throw IOException("Download failed (HTTP ${response.code}).")
            val body = response.body ?: throw IOException("The update download was empty.")
            val length = body.contentLength().takeIf { it > 0 } ?: release.size
            require(length <= MAX_APK_SIZE) { "This update is too large to download." }
            val hash = MessageDigest.getInstance("SHA-256")
            var count = 0L
            var previousPercent = -1
            body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while(true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if(read < 0) break
                        count += read
                        require(count <= MAX_APK_SIZE) { "This update is too large to download." }
                        output.write(buffer, 0, read)
                        hash.update(buffer, 0, read)
                        if(length > 0) {
                            val percent = ((count * 100) / length).coerceIn(0, 100).toInt()
                            if(percent != previousPercent) { progress(percent / 100f); previousPercent = percent }
                        }
                    }
                }
            }
            require(count > 0 && (release.size == 0L || count == release.size) && (length <= 0 || count == length)) { "The update download is incomplete. Try again." }
            require(matchesUpdateHash(release.sha256, hash.digest())) { "The update checksum did not match. Try again." }
        }
        val info = context.packageManager.getPackageArchiveInfo(partial.absolutePath, 0)
            ?: throw IOException("The downloaded file is not a valid APK.")
        require(info.packageName == context.packageName && info.versionName == release.version) { "The downloaded APK does not match this update." }
        require(androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info) > BuildConfig.VERSION_CODE) { "This APK is not a newer build." }
        finished.delete()
        require(partial.renameTo(finished)) { "Could not save the downloaded update." }
        finished
    } finally {
        partial.delete()
    }
}
fun launchUpdateInstaller(context: Context, file: File) {
    require(file.isFile) { "The downloaded update is no longer available. Download it again." }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
}
