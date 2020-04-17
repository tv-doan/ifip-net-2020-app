package de.tum.`in`.cm.vodmeasurementtool

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import de.tum.`in`.cm.vodmeasurementtool.util.PreferencesUtil
import de.tum.`in`.cm.vodmeasurementtool.util.ServiceScheduler
import kotlinx.android.synthetic.main.activity_settings.*

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefsUtil: PreferencesUtil

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefsUtil = PreferencesUtil(this)

        val defaultVideoCountToMeasure = prefsUtil.defaultVideoCountToMeasure
        val skipLoadingWebPages = prefsUtil.skipLoadingWebPages
        val dailyInterval = prefsUtil.dailyInterval
        val muteVideos = prefsUtil.muteVideos
        val isSchedulingEnabled = prefsUtil.isSchedulingEnabled

        when (defaultVideoCountToMeasure) {
            5 ->
                spinner_videos_per_cycle.setSelection(1)

            10 ->
                spinner_videos_per_cycle.setSelection(2)

            20 ->
                spinner_videos_per_cycle.setSelection(3)

            else ->
                spinner_videos_per_cycle.setSelection(0)
        }

        when (dailyInterval) {
            8 ->
                spinner_measuring_interval.setSelection(1)

            6 ->
                spinner_measuring_interval.setSelection(2)

            3 ->
                spinner_measuring_interval.setSelection(3)

            1 ->
                spinner_measuring_interval.setSelection(4)

            0 ->
                spinner_measuring_interval.setSelection(5)

            else ->
                spinner_measuring_interval.setSelection(0)
        }

        switch_load_web_pages.isChecked = !skipLoadingWebPages

        switch_mute_videos.isChecked = muteVideos

        switch_enable_scheduling.isChecked = isSchedulingEnabled

        button_save_settings.setOnClickListener {
            saveSettings()
            Toast.makeText(this@SettingsActivity, "Settings saved", Toast.LENGTH_SHORT).show()
            this@SettingsActivity.finish()
        }
    }

    private fun saveSettings() {
        when (spinner_videos_per_cycle.selectedItemPosition) {
            0 ->
                prefsUtil.setDefaultVideoCountToMeasure(2)

            1 ->
                prefsUtil.setDefaultVideoCountToMeasure(5)

            2 ->
                prefsUtil.setDefaultVideoCountToMeasure(10)

            3 ->
                prefsUtil.setDefaultVideoCountToMeasure(20)
        }

        when (spinner_measuring_interval.selectedItemPosition) {
            0 ->
                prefsUtil.setDailyInterval(12)

            1 ->
                prefsUtil.setDailyInterval(8)

            2 ->
                prefsUtil.setDailyInterval(6)

            3 ->
                prefsUtil.setDailyInterval(3)

            4 ->
                prefsUtil.setDailyInterval(1)

            5 ->
                prefsUtil.setDailyInterval(0)
        }

        prefsUtil.setSkipLoadingWebPages(!switch_load_web_pages.isChecked)
        prefsUtil.setMuteVideos(switch_mute_videos.isChecked)
        prefsUtil.setIsSchedulingEnabled(switch_enable_scheduling.isChecked)

        val nextScheduledMeasurement = prefsUtil.nextScheduledMeasurement
        if (nextScheduledMeasurement == -1L) {
            val timeTillNextScheduled = if (prefsUtil.dailyInterval == 0) {
                (60 * 60 * 1000) / 2
            } else {
                prefsUtil.dailyInterval.toLong() * 60 * 60 * 1000
            }
            ServiceScheduler(this).setNextAlarm(
                    timeTillNextScheduled//prefsUtil.dailyInterval.toLong() * 60 * 60 * 1000 //hrs in ms
            )
        }
    }
}