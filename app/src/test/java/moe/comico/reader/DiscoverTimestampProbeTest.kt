package moe.comico.reader

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.junit.Test
import java.util.concurrent.TimeUnit

@org.junit.Ignore("Manual inspection of the live Discover feed contract.")
class DiscoverTimestampProbeTest {
    @Test fun inspectFeedTimestampFields() {
        val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
        client.newCall(Request.Builder().url("https://comico.moe/api/stats/titles?sort=latest&limit=1").build()).execute().use { response ->
            val body = response.body!!.string()
            check(response.isSuccessful) { "HTTP ${response.code}: ${body.take(300)}" }
            val item = JSONArray(body).getJSONObject(0)
            item.remove("title")
            item.remove("coverUrl")
            item.remove("description")
            java.io.File("/tmp/comico-discover-timestamps.json").writeText(item.toString(2))
        }
    }
}
