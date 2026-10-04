package moe.comico.reader
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test
import java.util.concurrent.TimeUnit
class DownloadContractProbeTest {
    @org.junit.Ignore("Manual read-only inspection of the website download contract.")
    @Test fun inspectDownloadContract() {
        val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
        val js = client.newCall(Request.Builder().url("https://comico.moe/_nuxt/B8Zh-cBf2.js").build()).execute().use { it.body!!.string() }
        java.io.File("/tmp/comico-download-functions.txt").writeText(js)
    }
    @org.junit.Ignore("Manual fixture generation for offline emulator verification.")
    @Test fun writeOfflineReaderFixture() {
        java.util.zip.ZipOutputStream(java.io.File("/tmp/comico-offline-fixture.zip").outputStream()).use { zip ->
            for (page in 1..3) {
                val png = java.io.ByteArrayOutputStream()
                val output = java.io.DataOutputStream(png)
                output.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
                fun chunk(type: String, data: ByteArray) {
                    output.writeInt(data.size); output.writeBytes(type); output.write(data)
                    val crc = java.util.zip.CRC32(); crc.update(type.toByteArray()); crc.update(data); output.writeInt(crc.value.toInt())
                }
                val header = java.io.ByteArrayOutputStream()
                java.io.DataOutputStream(header).use { it.writeInt(480); it.writeInt(720); it.write(byteArrayOf(8, 2, 0, 0, 0)) }
                chunk("IHDR", header.toByteArray())
                val data = java.io.ByteArrayOutputStream()
                java.util.zip.DeflaterOutputStream(data).use { compressed ->
                    for (row in 0 until 720) {
                        val pixels = ByteArray(1441)
                        for (x in 0 until 480) {
                            pixels[x * 3 + 1] = (30 + page * 40).toByte()
                            pixels[x * 3 + 2] = (if (row in 250..450) 180 else 50).toByte()
                            pixels[x * 3 + 3] = (100 + page * 30).toByte()
                        }
                        compressed.write(pixels)
                    }
                }
                chunk("IDAT", data.toByteArray()); chunk("IEND", byteArrayOf())
                zip.putNextEntry(java.util.zip.ZipEntry("$page.png"))
                zip.write(png.toByteArray())
                zip.closeEntry()
            }
        }
    }
}
