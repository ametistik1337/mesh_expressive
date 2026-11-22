package ru.mesh.expressive.service

import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ru.mesh.expressive.data.local.SessionManager
import ru.mesh.expressive.util.MarksNotificationManager

class MarksSyncJobService : JobService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters?): Boolean {
        val sessionManager = SessionManager(applicationContext)
        if (!sessionManager.marksNotifyEnabled || sessionManager.authToken.isNullOrBlank()) {
            return false
        }

        serviceScope.launch {
            try {
                MarksNotificationManager.pollNotificationsSearch(applicationContext, sessionManager)
            } catch (_: Exception) {
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        serviceScope.cancel()
        return true
    }
}
