package de.tum.`in`.cm.vodmeasurementtool.model

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.Query

@Dao
interface MeasurementDao {

    @Insert
    fun insert(measurement: Measurement)

    @Query("SELECT * FROM measurements ORDER BY id DESC")
    fun getAllMeasurements(): List<Measurement>

    @Query("SELECT * FROM measurements WHERE :platform ORDER BY id DESC")
    fun filterByPlatform(platform: String): List<Measurement>

    @Query("DELETE FROM measurements")
    fun deleteAllMeasurements()
}