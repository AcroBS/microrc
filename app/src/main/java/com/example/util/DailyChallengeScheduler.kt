package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.example.data.DailyChallengeCatalog
import com.example.data.FirestoreDatabaseResolver
import com.example.data.PreferencesRepository
import com.example.model.LeaderboardNotificationDoc
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

object DailyChallengeScheduler {
    private const val ACTION_DAILY_CHECK = "com.example.microrc.ACTION_DAILY_CHALLENGE_CHECK"
    private const val ACTION_LEADERBOARD_SYNC = "com.example.microrc.ACTION_LEADERBOARD_SYNC"
    private const val REQUEST_CODE_ALARM = 4401
    private const val REQUEST_CODE_LB_SYNC = 4402

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
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                cal.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pi
            )
        }
    }

    fun scheduleLeaderboardSync(context: Context) {
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
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtElapsed,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                pi
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
        val appCtx = context.applicationContext ?: context
        if (!SessionNotificationHelper.hasNotificationPermission(appCtx)) return 0
        if (!GoogleAuthManager.ensureFirebaseInitialized(appCtx)) return 0
        val uid = runCatching {
            if (FirebaseApp.getApps(appCtx).isNotEmpty()) Firebase.auth.currentUser?.uid else null
        }.getOrNull() ?: return 0

        val dbId = FirestoreDatabaseResolver.resolveDatabaseId(appCtx)
        val db = FirebaseFirestore.getInstance(dbId)

        val snap = withTimeoutOrNull(8_000L) {
            runCatching {
                db.collection("leaderboardNotifications")
                    .whereEqualTo("recipientUid", uid)
                    .get(Source.SERVER)
                    .await()
            }.getOrNull() ?: runCatching {
                db.collection("leaderboardNotifications")
                    .whereEqualTo("recipientUid", uid)
                    .get()
                    .await()
            }.getOrNull()
        } ?: return 0

        val nowSec = System.currentTimeMillis() / 1000L
        var postedCount = 0
        snap.documents.mapNotNull { doc ->
            doc.toObject(
                LeaderboardNotificationDoc::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        }.filter { notif ->
            val createdSec = notif.createdAt?.seconds ?: nowSec
            notif.status == "UNREAD" && (nowSec - createdSec) <= 172_800L
        }.forEach { notif ->
            if (SessionNotificationHelper.showFriendLeaderboardRecordNotification(appCtx, notif)) {
                postedCount++
            }
        }
        return postedCount
    }
}

class DailyChallengeNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        val appCtx = context.applicationContext ?: context
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DailyChallengeScheduler.ensureDailyNotificationPostedIfNewDay(appCtx)
                DailyChallengeScheduler.syncPendingFriendLeaderboardNotifications(appCtx)
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
                DailyChallengeScheduler.syncPendingFriendLeaderboardNotifications(appCtx)
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
                DailyChallengeScheduler.syncPendingFriendLeaderboardNotifications(appCtx)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
