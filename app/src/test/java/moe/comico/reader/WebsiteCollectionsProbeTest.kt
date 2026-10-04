package moe.comico.reader
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test
import org.junit.Ignore
import java.util.concurrent.TimeUnit
@Ignore("Manual inspection of the live website collection contract.")
class WebsiteCollectionsProbeTest {
    @Test fun inspectCollectionRoutes() {
        val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
        fun fetch(path: String) = client.newCall(Request.Builder().url("https://comico.moe/_nuxt/$path").build()).execute().use { it.body!!.string() }
        val js = fetch("NY3e7e4R2.js")
        val report = StringBuilder(js.take(4500) + "\n")
        Regex(""".{0,500}(?:collection|/api/).{0,1400}""", RegexOption.IGNORE_CASE).findAll(js).forEach { report.appendLine(it.value) }
        Regex("""from"\./([^"]+\.js)"|import"\./([^"]+\.js)"""").findAll(js).map { it.groupValues[1].ifBlank { it.groupValues[2] } }.distinct().forEach { path ->
            val imported = fetch(path)
            Regex(""".{0,500}(?:/api/collections|collectionId|CollectionCreate).{0,1400}""").findAll(imported).forEach { report.appendLine("FILE=$path " + it.value) }
        }
        java.io.File("/tmp/comico-manga-collections.txt").writeText(report.toString())
    }
}
