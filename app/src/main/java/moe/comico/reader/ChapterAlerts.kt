package moe.comico.reader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class ChapterAlert(val id: String, val title: String, val detail: String, val createdAt: Long, val read: Boolean = false)

fun JSONObject.chapterAlert(): ChapterAlert? {
    val id = optString("id").takeUnless { it.isBlank() || it == "null" } ?: return null
    val title = optString("title").takeUnless { it.isBlank() || it == "null" } ?: return null
    val time = runCatching { Instant.parse(getString("createdAt")).toEpochMilli() }.getOrNull() ?: return null
    return ChapterAlert(id, title, optString("detail").takeUnless { it == "null" }.orEmpty(), time, optBoolean("read"))
}

fun newChapterAlerts(items: List<ChapterAlert>, since: Long, seen: Set<String>): List<ChapterAlert> =
    items.distinctBy { it.id }.filter { !it.read && it.createdAt >= since && it.id !in seen }.sortedBy { it.createdAt }

object ChapterAlerts {
    const val ACTION = "moe.comico.reader.CHAPTER_UPDATES"
    private const val JOB = 701
    private const val CHANNEL = "new_chapters"
    private fun prefs(context: Context) = context.getSharedPreferences("chapter_alerts", Context.MODE_PRIVATE)
    fun owner(context: Context): String? = context.getSharedPreferences("reader", Context.MODE_PRIVATE)
        .getString("localLibraryOwner", null)
    fun enabled(context: Context) = prefs(context).getBoolean("enabled", true)
    fun allowed(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun setEnabled(context: Context, value: Boolean) {
        val preferences = prefs(context)
        preferences.edit().putBoolean("enabled", value).apply()
        if (value) owner(context)?.let { preferences.edit().putLong("since:$it", System.currentTimeMillis()).apply() }
        sync(context)
    }
    fun sync(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "New chapters", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "New chapter updates for your comico.moe account library"
        })
        val owner = owner(context)
        if (owner == null || !enabled(context) || !allowed(context)) {
            scheduler.cancel(JOB)
            manager.cancel(JOB)
            return
        }
        val preferences = prefs(context)
        if (!preferences.contains("since:$owner")) preferences.edit().putLong("since:$owner", System.currentTimeMillis()).apply()
        if (scheduler.getPendingJob(JOB)?.extras?.getString("owner") == owner) return
        manager.cancel(JOB)
        val extras = PersistableBundle().apply { putString("owner", owner) }
        scheduler.schedule(JobInfo.Builder(JOB, ComponentName(context, ChapterAlertService::class.java))
            .setExtras(extras).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(15 * 60 * 1000L).setPersisted(true).build())
    }
    suspend fun check(context: Context, expectedOwner: String): String {
        if (owner(context) != expectedOwner) return "Skipped: account changed."
        if (!enabled(context)) return "Skipped: chapter alerts are disabled."
        if (!allowed(context)) return "Skipped: notifications are blocked by Android."
        if (!hasInternet(context)) return "Skipped: no internet connection."
        val api = ComicoApi(AccountCookieJar(context), networkAvailable = { hasInternet(context) })
        if (api.account()?.id != expectedOwner) return "Skipped: sign in again to restore the account session."
        val items = api.chapterNotifications()
        val preferences = prefs(context)
        val since = preferences.getLong("since:$expectedOwner", System.currentTimeMillis())
        val seen = runCatching {
            val array = JSONArray(preferences.getString("seen:$expectedOwner", "[]"))
            (0 until array.length()).map { array.getString(it) }.toSet()
        }.getOrDefault(emptySet())
        val fresh = newChapterAlerts(items, since, seen)
        // A settings/account change while fetching must not publish another account's alerts.
        if (owner(context) != expectedOwner || !enabled(context) || !allowed(context) ||
            preferences.getLong("since:$expectedOwner", -1) != since) return "Skipped: account or notification settings changed."
        if (fresh.isNotEmpty()) post(context, expectedOwner, fresh, JOB)
        val ids = (seen.toList() + items.map { it.id }).distinct().takeLast(1000)
        preferences.edit().putString("seen:$expectedOwner", JSONArray(ids).toString())
            .putLong("checked:$expectedOwner", System.currentTimeMillis()).apply()
        return "Checked ${items.size} comic updates; ${fresh.size} new alerts."
    }
    fun sample(context: Context): String {
        sync(context)
        if (!allowed(context)) return "Notifications are blocked. Allow them in Settings → Notifications."
        post(context, owner(context), listOf(ChapterAlert("sample", "Comico test chapter", "Chapter 12 · Sample notification", System.currentTimeMillis())), 702)
        return "Sample sent. Open the notification shade; tap it to test navigation."
    }
    private fun post(context: Context, expectedOwner: String?, fresh: List<ChapterAlert>, notificationId: Int) {
            val intent = Intent(context, MainActivity::class.java)
                .apply { if (expectedOwner != null) setAction(ACTION).putExtra("owner", expectedOwner) }
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending = PendingIntent.getActivity(context, notificationId, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val lines = fresh.takeLast(5).map { it.title + if (it.detail.isNotBlank()) " · ${it.detail}" else "" }
            val notification = Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(if (fresh.size == 1) fresh.single().title else "${fresh.size} new chapter updates")
                .setContentText(if (fresh.size == 1) fresh.single().detail.ifBlank { "A new chapter is available" } else "New chapters in your library")
                .setStyle(Notification.InboxStyle().also { style -> lines.forEach { style.addLine(it) } })
                .setContentIntent(pending).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE).build()
            context.getSystemService(NotificationManager::class.java).notify(notificationId, notification)
    }
}
