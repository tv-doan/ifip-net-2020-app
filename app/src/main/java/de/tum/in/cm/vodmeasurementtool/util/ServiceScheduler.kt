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

/**
 * Scheduler class for scheduling periodic measurement sessions. In the app's settings, user has the option to enable
 * `scheduling`, which would let the app to automatically trigger a measurement session every few hours (the specific
 * value is denoted by the `measuring interval` option). This scheduler class is responsible scheduling of an upcoming
 * measurement session in the defined time interval. It schedules the next upcoming measurement session by setting
 * an alarm at specific point of time that is derived from the `measuring interval` and sending a custom defined intent
 * that will be intercepted by this application (see `VoDAlarmReceiver`); once the intent is intercepted, the app
 * automatically triggers a measurement session.
 *
 * Note: using alarm for scheduling has proven unreliable, as we registered many time the app stopped scheduling new
 * measurement sessions, but with the limited time scope this issue was not addressed. Consider using Android's
 * WorkManager for scheduling instead.
 */
class ServiceScheduler(private val context: Context) {
    private val alarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager?

    /**
     * Set an alarm that will set off after `timeTillNextScheduled` ms has passed since this function was called, and
     * fire a custom intent at that point.
     *
     * @param   timeTillNextScheduled   time in ms after which (counting from the time this function is called) an
     *                                  alarm will be fired
     */
    fun setNextAlarm(timeTillNextScheduled: Long) {

        val alarmIntent = Intent(context, VoDAlarmReceiver::class.java).let { intent ->
            // the custom intent to be fired once the alarm is set off
            // consider extracting the intent's value to a constant
            intent.action = "phamtdat.intent.action.START_MEASURING_SERVICE_V2"
            PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        // schedule the alarm to set off `timeTillNextScheduled` ms from now
        val nextScheduled = Calendar.getInstance().timeInMillis + timeTillNextScheduled
        alarmManager?.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextScheduled,
                alarmIntent)

        // store the time of the upcoming alarm internally, so that the app will be able to display this information
        // on the MainActivity
        PreferencesUtil(context).setNextScheduledMeasurement(nextScheduled)
    }
}

/**
 * Custom BroadcastReceiver to receive the custom intent fired by a set-offed alarm scheduled by `ServiceScheduler`.
 * This BroadcastReceiver is registered in the app's manifest to specifically receive the custom intent.
 * Upon receiving the intent, automatically start a measurement session.
 */
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

            // let `ServiceScheduler` schedule the next measurement sessions
            ServiceScheduler(it).setNextAlarm(timeTillNextScheduled)

            if (!prefsUtil.isSchedulingEnabled) {
                // check the setting option first, as user may have disabled the option since the last periodic
                // measurement session
                L.d { "Scheduling not enabled, not firing service." }
            } else {
                // then, start the measurement session if the option is enabled
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.startForegroundService(serviceIntent)
                } else {
                    it.startService(serviceIntent)
                }
            }
        }
    }

    companion object {
        const val ALARM_REQUEST_CODE = 1999
    }
}

/**
 * The scheduling can be lost and discarded if the device was rebooted, therefore we register a BroadcastReceiver
 * that receives the event of device rebooting, and restart the scheduling if necessary.
 */
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

