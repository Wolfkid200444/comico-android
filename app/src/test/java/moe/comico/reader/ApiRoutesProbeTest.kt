package moe.comico.reader

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/** Manual, read-only inspection of the public website's API usage. */
@org.junit.Ignore("Manual inspection of live public API contracts; never runs in ordinary tests.")
class ApiRoutesProbeTest {
    @Test fun inspectKnownSources() {
        val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
        val report = StringBuilder()
        for (path in listOf("/api/comments?chapterId=05d97139-be28-4862-8827-fb9bec576133&sort=newest&limit=100")) {
            val body = client.newCall(Request.Builder().url(BASE_URL + path)
                .header("User-Agent", "ComicoAndroid/contract-check (https://github.com/Wolfkid200444/comico-android)").build())
                .execute().use { it.body!!.string() }
            report.appendLine("FILE=$path\n$body")
        }
        File("/tmp/comico-comment-actions-v2.txt").writeText(report.toString())
    }
    @Test fun inspectCollectionAndReactionContract() {
        val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
        fun fetch(url: String) = client.newCall(Request.Builder().url(url)
            .header("User-Agent", "ComicoAndroid/contract-check (https://github.com/Wolfkid200444/comico-android)").build())
            .execute().use { it.body!!.string() }
        val html = fetch(BASE_URL + "/library") + fetch(BASE_URL)
        val assets = Regex("""/_nuxt/[^"\s<>]+\.js""").findAll(html).map { it.value }.distinct().toMutableList()
        val report = StringBuilder("HTML=" + html.take(500) + "\nASSETS=" + assets.joinToString() + "\n")
        val visited = mutableSetOf<String>()
        var index = 0
        while (index < assets.size && visited.size < 260) {
            val path = assets[index++]
            if (!visited.add(path)) continue
            val js = fetch(BASE_URL + path)
            report.appendLine("SIZE=$path " + js.length + " " + js.take(80))
            Regex(""".{0,250}(?:/api/collections|collectionId|votes|vote:|name:p\(\)\.trim\(\)\.min\(1\)\.max\(120\)).{0,900}""")
                .findAll(js).forEach { report.appendLine("FILE=$path " + it.value) }
            if (js.contains("__name:`Scalar") || js.contains("x-scalar-environments")) continue
            Regex("""[`"'](?:\./|/_nuxt/)?([^`"'/]+\.js)[`"']""").findAll(js).forEach {
                val imported = "/_nuxt/" + it.groupValues[1]
                if (imported !in assets) assets += imported
            }
        }
        File("/tmp/comico-api-contract-v3.txt").writeText(report.toString())
        check(report.contains("/api/collections") || report.contains("/reaction")) { report.take(2000) }
    }
}
