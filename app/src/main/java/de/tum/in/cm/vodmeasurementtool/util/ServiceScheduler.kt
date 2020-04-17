package de.tum.`in`.cm.vodmeasurementtool.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import de.tum.`in`.cm.vodmeasurementtool.MediaPlayerService
import de.tum.`in`.cm.vodmeasurementtool.model.formattedDateTime
import de.tum.`in`.cm.vodmeasurementtool.util.VoDAlarmReceiver.Companion.ALARM_REQUEST_CODE
import java.util.*

class ServiceScheduler(private val context: Context) {
    private val alarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager?

    fun setNextAlarm(timeTillNextScheduled: Long) {
        val alarmIntent = Intent(context, VoDAlarmReceiver::class.java).let { intent ->
            intent.action = "phamtdat.intent.action.START_MEASURING_SERVICE_V2"//PreferencesUtil(context).appId.toString()
            PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val nextScheduled = Calendar.getInstance().timeInMillis + timeTillNextScheduled
        alarmManager?.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextScheduled,
                alarmIntent)
        PreferencesUtil(context).setNextScheduledMeasurement(nextScheduled)
    }
}

class VoDAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {

        L.d { "Alarm intent = ${intent?.action.toString()}, context = $context"}
        context?.let {
            val prefsUtil = PreferencesUtil(it)

            L.d { "Alarm fired at ${System.currentTimeMillis().formattedDateTime()}" }
            val serviceIntent = Intent(context, MediaPlayerService::class.java)
            val timeTillNextScheduled = if (prefsUtil.dailyInterval == 0) {
                (60 * 60 * 1000) / 2
            } else {
                prefsUtil.dailyInterval.toLong() * 60 * 60 * 1000
            }
            ServiceScheduler(it).setNextAlarm(timeTillNextScheduled)

            if (!prefsUtil.isSchedulingEnabled) {
                L.d { "Scheduling not enabled, not firing service." }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.startForegroundService(serviceIntent)
                } else {
                    it.startService(serviceIntent)
                }
            }
        }
    }

    companion object {
        val ALARM_REQUEST_CODE = 1999
    }
}

class DeviceBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent != null && intent.action == "android.intent.action.BOOT_COMPLETED") {
            context?.let {
                val dailyInterval = PreferencesUtil(it).dailyInterval
                val timeTillNextScheduled = if (dailyInterval == 0) {
                    (60 * 60 * 1000) / 2
                } else {
                    dailyInterval.toLong() * 60 * 60 * 1000
                }
                ServiceScheduler(it).setNextAlarm(timeTillNextScheduled)
            }
        }
    }
}

