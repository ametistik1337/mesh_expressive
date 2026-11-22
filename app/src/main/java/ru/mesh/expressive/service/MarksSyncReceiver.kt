package ru.mesh.expressive.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.mesh.expressive.data.local.SessionManager
import ru.mesh.expressive.util.MarksNotificationManager

class MarksSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val sessionManager = SessionManager(context.applicationContext)
        if (!sessionManager.marksNotifyEnabled) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MarksNotificationManager.pollNotificationsSearch(context.applicationContext, sessionManager)
                MarksNotificationManager.schedulePeriodicSync(context.applicationContext, sessionManager)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
