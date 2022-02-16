package com.example.alarmmanager.app

import android.app.*
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.os.Build
import androidx.work.*
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import java.net.SocketException
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import android.content.Intent
import com.example.alarmmanager.MainActivity
import com.example.alarmmanager.R

class ReminderWorkerWithWorkManager(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {

        private const val REMINDER_WORK_NAME = "reminder"
        private const val PARAM_NAME = "name" // optional - send parameter to worker

        private var isSuccess: Boolean = false
        private var hours: Int = 0
        private var minutes: Int = 0

        // private const val RESULT_ID = "id"
        private lateinit var workRequest: OneTimeWorkRequest

        fun runAt(hour: Int, minute: Int) {
            val workManager = WorkManager.getInstance()

            hours = hour
            minutes = minute

            // trigger at 8:30am
            val alarmTime = LocalTime.of(17, 43)
            var now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
            val nowTime = now.toLocalTime()
            // if same time, schedule for next day as well
            // if today's time had passed, schedule for next day
            if (nowTime == alarmTime || nowTime.isAfter(alarmTime)) {
                now = now.plusDays(1)
            }
            now = now.withHour(alarmTime.hour)
                .withMinute(alarmTime.minute) // .withSecond(alarmTime.second).withNano(alarmTime.nano)
            val duration = Duration.between(LocalDateTime.now(), now)

            Timber.d("runAt=${duration.seconds}s")

            // optional constraints
            /*
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
             */

            // optional data
            val data = workDataOf(PARAM_NAME to "Timer 01")

            workRequest = OneTimeWorkRequestBuilder<ReminderWorkerWithWorkManager>()
                .setInitialDelay(duration.seconds, TimeUnit.SECONDS)
                // .setConstraints(constraints)
                .setInputData(data) // optional
                .build()

            workManager.enqueueUniqueWork(
                REMINDER_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        fun cancel() {
            Timber.d("cancel")
            val workManager = WorkManager.getInstance()
            workManager.cancelUniqueWork(REMINDER_WORK_NAME)
        }
    }

    override suspend fun doWork(): Result = coroutineScope {
        val worker = this@ReminderWorkerWithWorkManager
        val context = applicationContext

        //val name = inputData.getString(PARAM_NAME)
        // Timber.d("doWork=$name")

        var isScheduleNext = true
        try {
            // do something

            // possible to return result
            // val data = workDataOf(RESULT_ID to 1)
            // Result.success(data)
//            Log.d("App", "Successfully Task Completed")
//
//            if (ProcessLifecycleOwner.get().lifecycle.currentState == Lifecycle.State.CREATED) {
//                Log.d("App  State", "Background")
//            } else if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
//                Log.d("App  State", "Foreground")
//            }
            showNotification()
            isSuccess = true
            Result.success()
        } catch (e: Exception) {
            // only retry 3 times
            if (runAttemptCount > 3) {
                Timber.d("runAttemptCount=$runAttemptCount, stop retry")
                return@coroutineScope Result.success()
            }

            // retry if network failure, else considered failed
            when (e.cause) {
                is SocketException -> {
                    Timber.e(e.toString(), e.message)
                    isScheduleNext = false
                    Result.retry()
                }
                else -> {
                    Timber.e(e)
                    isSuccess = false
                    Result.failure()
                }
            }
        } finally {
            // only schedule next day if not retry, else it will overwrite the retry attempt
            // - because we use uniqueName with ExistingWorkPolicy.REPLACE
            if (isScheduleNext) {
                val handler = Handler(Looper.getMainLooper())
                val runnable = Runnable {
                    runAt(hours, minutes)
                    isSuccess = false
                }
                handler.postDelayed(runnable, 5000)
            }
        }
    }

    private fun showNotification() {

        isSuccess
        val notificationManager =
            applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val notificationId = 1
        val channelId = "channel_cheque_warning"
        val channelName = "Cheque Warning"
        val importance = NotificationManager.IMPORTANCE_HIGH

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mChannel = NotificationChannel(
                channelId, channelName, importance
            )
            notificationManager.createNotificationChannel(mChannel)
        }

        val mBuilder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Please upload the Cheque")
            .setContentText("Please Upload the Cheque as forrun is not responsible for any blunder")
        val intent = Intent(applicationContext, MainActivity::class.java)

        val stackBuilder = TaskStackBuilder.create(applicationContext)
        stackBuilder.addNextIntent(intent)
        val resultPendingIntent = stackBuilder.getPendingIntent(
            0,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        mBuilder.setContentIntent(resultPendingIntent)
        notificationManager.notify(notificationId, mBuilder.build())

    }
}