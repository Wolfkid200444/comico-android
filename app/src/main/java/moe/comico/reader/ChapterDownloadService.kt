package moe.comico.reader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

class ChapterDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var downloads: ChapterDownloads
    private var worker: Job? = null
    override fun onCreate() {
        super.onCreate()
        downloads = ChapterDownloads.get(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("chapters", "Chapter downloads", NotificationManager.IMPORTANCE_LOW))
        startForeground(700, notification())
        scope.launch {
            downloads.tasks.collect { tasks ->
                val task = tasks.firstOrNull { it.active }
                val manager = getSystemService(NotificationManager::class.java)
                if (task == null) manager.cancel(700)
                else if (android.os.Build.VERSION.SDK_INT < 33 ||
                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    manager.notify(700, notification(task))
                }
            }
        }
    }
    private fun notification(task: MangaDownload? = null): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, "chapters")
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentIntent(open)
            .setContentTitle(task?.manga?.title ?: "Comico downloads")
            .setContentText(task?.let { "${it.status} · ${it.completed} chapters saved" } ?: "Preparing chapter downloads")
            .setOngoing(true).setOnlyAlertOnce(true)
            .setProgress(task?.chapters?.size?.plus(task.completed + task.skipped) ?: 0,
                task?.completed?.plus(task.skipped) ?: 0, task == null || task.all || task.status == "Waiting for internet")
        if (task != null) {
            val stop = PendingIntent.getService(this, task.id.hashCode(),
                Intent(this, ChapterDownloadService::class.java).putExtra("stop", task.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(Notification.Action.Builder(null, "Stop manga", stop).build())
        }
        return builder.build()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("stop")?.let(downloads::stop)
        startWorker()
        return START_STICKY
    }
    private fun startWorker() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            try { downloads.runQueue() }
            finally {
                worker = null
                if (scope.isActive && downloads.tasks.value.any { it.active }) startWorker()
                else { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
            }
        }
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        downloads.pauseAll()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
