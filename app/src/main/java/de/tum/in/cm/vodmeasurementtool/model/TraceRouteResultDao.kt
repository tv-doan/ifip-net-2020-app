package de.tum.`in`.cm.vodmeasurementtool.model

import android.arch.persistence.room.Dao
import android.arch.persistence.room.Insert
import android.arch.persistence.room.Query

@Dao
interface TraceRouteResultDao {

    @Insert
    fun insert(traceRouteResult: TraceRouteResult)

    @Query("SELECT * FROM traceroutes ORDER BY id DESC")
    fun getAllTraceRouteResults(): List<TraceRouteResult>

    @Query("SELECT * FROM traceroutes WHERE :platform ORDER BY id DESC")
    fun filterByPlatform(platform: String): List<TraceRouteResult>

    @Query("DELETE FROM traceroutes")
    fun deleteAllTraceRouteResults()
}