package de.tum.`in`.cm.vodmeasurementtool.util

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager

class PreferencesUtil(private val context: Context) {

    private val sharedPreferences: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(context)

    val defaultVideoCountToMeasure: Int
        get() = sharedPreferences.getInt("defaultVideoCountToMeasure", 2)

    val skipLoadingWebPages: Boolean
        get() = sharedPreferences.getBoolean("skipLoadingWebPages", true)

    val muteVideos: Boolean
        get() = sharedPreferences.getBoolean("muteVideos", true)

    val appId: Long
        get() = sharedPreferences.getLong("appId", -1L)

    val dailyInterval: Int
        get() = sharedPreferences.getInt("dailyInterval", 12)

    val isSchedulingEnabled: Boolean
    get() = sharedPreferences.getBoolean("isSchedulingEnabled", false)

    val nextScheduledMeasurement: Long
        get() = sharedPreferences.getLong("nextScheduledMeasurement", -1L)

    val dbVersion: Int
        get() = sharedPreferences.getInt("dbVersion", 0)

    val lastDbVersionUpdate: Long
    get() = sharedPreferences.getLong("lastDbVersionUpdate", -1L)

    val lastServiceFinishTime: Long
    get() = sharedPreferences.getLong("lastServiceFinishTime", -1)

    fun setDefaultVideoCountToMeasure(value: Int) {
        with(sharedPreferences.edit()) {
            putInt("defaultVideoCountToMeasure", value)
            commit()
        }
    }

    fun setAppId() {
        val currentId = appId
        val newId = if (currentId == -1L) {
            System.currentTimeMillis()
        } else {
            currentId
        }

        if (newId == currentId) return

        with(sharedPreferences.edit()) {
            putLong("appId", newId)
            commit()
        }
    }

    fun setDailyInterval(value: Int) {
        with(sharedPreferences.edit()) {
            putInt("dailyInterval", value)
            commit()
        }
    }

    fun setSkipLoadingWebPages(value: Boolean) {
        with(sharedPreferences.edit()) {
            putBoolean("skipLoadingWebPages", value)
            commit()
        }
    }

    fun setMuteVideos(value: Boolean) {
        with(sharedPreferences.edit()) {
            putBoolean("muteVideos", value)
            commit()
        }
    }

    fun setNextScheduledMeasurement(value: Long) {
        with(sharedPreferences.edit()) {
            putLong("nextScheduledMeasurement", value)
            commit()
        }
    }

    fun updateDbVersion() {
        with(sharedPreferences.edit()) {
            putInt("dbVersion", dbVersion + 1)
            putLong("lastDbVersionUpdate", System.currentTimeMillis())
            commit()
        }
    }

    fun setIsSchedulingEnabled(value: Boolean) {
        with(sharedPreferences.edit()) {
            putBoolean("isSchedulingEnabled", value)
            commit()
        }
    }

    fun setLastServiceFinishTimeToNow() {
        with(sharedPreferences.edit()) {
            putLong("lastServiceFinishTime", System.currentTimeMillis())
            commit()
        }
    }
}