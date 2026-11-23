package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.DailyChallengeCatalog
import com.example.data.FirestoreDatabaseResolver
import com.example.data.PreferencesRepository
import com.example.model.LeaderboardNotificationDoc
import com.example.model.SessionInviteDoc
import com.example.ui.auth.GoogleAuthManager
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import java.util.concurrent.TimeUnit

internal data class BackgroundSyncOutcome(
    val leaderboardNotificationsPosted: Int,
    val sessionInvitesPosted: Int,
    val serverReachable: Boolean
) {
    val totalPosted: Int
        get() = leaderboardNotificationsPosted + sessionInvitesPosted
}

class LeaderboardNotificationSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        DailyChallengeScheduler.ensureDailyNotificationPostedIfNewDay(applicationContext)
        val outcome = DailyChallengeScheduler.syncPendingFriendNotifications(applicationContext)
        return if (!outcome.serverReachable && runAttemptCount < 3) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}

object DailyChallengeScheduler {
    private const val ACTION_DAILY_CHECK = "com.example.microrc.ACTION_DAILY_CHALLENGE_CHECK"
    private const val ACTION_LEADERBOARD_SYNC = "com.example.microrc.ACTION_LEADERBOARD_SYNC"
    private const val REQUEST_CODE_ALARM = 4401
    private const val REQUEST_CODE_LB_SYNC = 4402

    internal const val PERIODIC_SYNC_WORK_NAME = "microrc_friend_notification_sync"
    internal const val IMMEDIATE_SYNC_WORK_NAME = "microrc_friend_notification_sync_now"

    fun scheduleDailyReminder(context: Context) {
        val appCtx = context.applicationContext ?: context
        val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(appCtx, DailyChallengeNotificationReceiver::class.java).apply {
            action = ACTION_DAILY_CHECK
        }
        val pi = PendingIntent.getBroadcast(
            appCtx,
            REQUEST_CODE_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        runCatching {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                cal.timeInMillis,
                pi
            )
        }
    }

    fun scheduleLeaderboardSync(context: Context) {
        val appCtx = context.applicationContext ?: context

        // 1. Primary resilient background sync via WorkManager (survives process death & Doze windows,
        // waits for NetworkType.CONNECTED instead of firing offline and dropping notifications).
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val workRequest = PeriodicWorkRequestBuilder<LeaderboardNotificationSyncWorker>(
                15L, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2L, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(appCtx).enqueueUniquePeriodicWork(
                PERIODIC_SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        // 2. Non-exact Doze-tolerant AlarmManager backup (no SCHEDULE_EXACT_ALARM needed).
        scheduleNextAlarmFallback(appCtx)
    }

    internal fun scheduleNextAlarmFallback(context: Context) {
        val appCtx = context.applicationContext ?: context
        val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(appCtx, LeaderboardSyncReceiver::class.java).apply {
            action = ACTION_LEADERBOARD_SYNC
        }
        val pi = PendingIntent.getBroadcast(
            appCtx,
            REQUEST_CODE_LB_SYNC,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAtElapsed = SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES
        runCatching {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtElapsed,
                pi
            )
        }
    }

    fun triggerImmediateBackgroundSync(context: Context) {
        val appCtx = context.applicationContext ?: context
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val oneTime = OneTimeWorkRequestBuilder<LeaderboardNotificationSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30L, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(appCtx).enqueueUniqueWork(
                IMMEDIATE_SYNC_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                oneTime
            )
        }
    }

    suspend fun ensureDailyNotificationPostedIfNewDay(context: Context): Boolean {
        val appCtx = context.applicationContext ?: context
        if (!SessionNotificationHelper.hasNotificationPermission(appCtx)) return false
        val prefs = PreferencesRepository(appCtx)
        if (!prefs.isDailyNotificationsEnabled()) return false

        val todayKey = DailyChallengeCatalog.todayDateKey()
        val lastNotified = prefs.getLastNotifiedDailyKey()
        if (lastNotified == todayKey) return false

        val dailyTrack = DailyChallengeCatalog.dailyCustomTrack(todayKey)
        SessionNotificationHelper.showDailyChallengeAvailableNotification(
            context = appCtx,
            dateKey = todayKey,
            trackName = dailyTrack.name
        )
        prefs.markDailyChallengeNotified(todayKey)
        return true
    }

    suspend fun syncPendingFriendLeaderboardNotifications(context: Context): Int {
        return syncPendingFriendNotifications(context).leaderboardNotificationsPosted
    }

    internal suspend fun syncPendingFriendNotifications(context: Context): BackgroundSyncOutcome {
        val appCtx = context.applicationContext ?: context
        if (!SessionNotificationHelper.hasNotificationPermission(appCtx)) {
            return BackgroundSyncOutcome(0, 0, serverReachable = true)
        }
        if (!GoogleAuthManager.ensureFirebaseInitialized(appCtx)) {
            return BackgroundSyncOutcome(0, 0, serverReachable = true)
        }
        val uid = runCatching {
            if (FirebaseApp.getApps(appCtx).isNotEmpty()) Firebase.auth.currentUser?.uid else null
        }.getOrNull() ?: return BackgroundSyncOutcome(0, 0, serverReachable = true)

        val dbId = FirestoreDatabaseResolver.resolveDatabaseId(appCtx)
        val db = FirebaseFirestore.getInstance(dbId)
        val nowSec = System.currentTimeMillis() / 1000L

        var serverReachable = true

        val lbSnap = withTimeoutOrNull(10_000L) {
            runCatching {
                db.collection("leaderboardNotifications")
                    .whereEqualTo("recipientUid", uid)
                    .get(Source.SERVER)
                    .await()
            }.onFailure {
                serverReachable = false
            }.getOrNull()
        } ?: run {
            serverReachable = false
            withTimeoutOrNull(4_000L) {
                runCatching {
                    db.collection("leaderboardNotifications")
                        .whereEqualTo("recipientUid", uid)
                        .get()
                        .await()
                }.getOrNull()
            }
        }

        var lbPosted = 0
        if (lbSnap != null) {
            lbSnap.documents.mapNotNull { doc ->
                doc.toObject(
                    LeaderboardNotificationDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }.filter { notif ->
                val createdSec = notif.createdAt?.seconds ?: nowSec
                notif.status == "UNREAD" && (nowSec - createdSec) <= 172_800L
            }.forEach { notif ->
                if (SessionNotificationHelper.showFriendLeaderboardRecordNotification(appCtx, notif)) {
                    lbPosted++
                }
            }
        }

        val inviteSnap = withTimeoutOrNull(8_000L) {
            runCatching {
                db.collection("sessionInvites")
                    .whereEqualTo("recipientUid", uid)
                    .get(Source.SERVER)
                    .await()
            }.getOrNull()
        }

        var invitePosted = 0
        if (inviteSnap != null) {
            inviteSnap.documents.mapNotNull { doc ->
                doc.toObject(
                    SessionInviteDoc::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }.filter { inv ->
                val createdSec = inv.createdAt?.seconds ?: nowSec
                inv.status == "PENDING" && (nowSec - createdSec) <= 900L
            }.forEach { inv ->
                if (SessionNotificationHelper.showSessionInviteNotification(appCtx, inv)) {
                    invitePosted++
                }
            }
        }

        return BackgroundSyncOutcome(
            leaderboardNotificationsPosted = lbPosted,
            sessionInvitesPosted = invitePosted,
            serverReachable = serverReachable
        )
    }
}

class DailyChallengeNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        val appCtx = context.applicationContext ?: context
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DailyChallengeScheduler.scheduleDailyReminder(appCtx)
                DailyChallengeScheduler.ensureDailyNotificationPostedIfNewDay(appCtx)
                DailyChallengeScheduler.syncPendingFriendNotifications(appCtx)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class LeaderboardSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        val appCtx = context.applicationContext ?: context
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DailyChallengeScheduler.scheduleNextAlarmFallback(appCtx)
                DailyChallengeScheduler.ensureDailyNotificationPostedIfNewDay(appCtx)
                val outcome = DailyChallengeScheduler.syncPendingFriendNotifications(appCtx)
                if (!outcome.serverReachable) {
                    DailyChallengeScheduler.triggerImmediateBackgroundSync(appCtx)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class DailyChallengeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        val appCtx = context.applicationContext ?: context
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DailyChallengeScheduler.scheduleDailyReminder(appCtx)
                DailyChallengeScheduler.scheduleLeaderboardSync(appCtx)
                DailyChallengeScheduler.ensureDailyNotificationPostedIfNewDay(appCtx)
                val outcome = DailyChallengeScheduler.syncPendingFriendNotifications(appCtx)
                if (!outcome.serverReachable) {
                    DailyChallengeScheduler.triggerImmediateBackgroundSync(appCtx)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
