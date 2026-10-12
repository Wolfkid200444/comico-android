package moe.comico.reader

import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class ChapterAlertService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var check: Job? = null
    private var active: JobParameters? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val owner = params.extras.getString("owner") ?: return false
        active = params
        check = scope.launch {
            try {
                withTimeout(2 * 60 * 1000L) { ChapterAlerts.check(this@ChapterAlertService, owner) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.w("ChapterAlerts", "Chapter check failed; will retry on the next periodic check", error)
            } finally {
                if (active === params) {
                    active = null
                    jobFinished(params, false)
                }
            }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean {
        active = null
        check?.cancel()
        check = null
        return true
    }
    override fun onDestroy() {
        active = null
        scope.cancel()
        super.onDestroy()
    }
}
