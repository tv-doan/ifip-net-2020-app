package de.tum.`in`.cm.vodmeasurementtool

import android.arch.persistence.room.Database
import android.arch.persistence.room.RoomDatabase
import de.tum.`in`.cm.vodmeasurementtool.model.Measurement
import de.tum.`in`.cm.vodmeasurementtool.model.MeasurementDao
import de.tum.`in`.cm.vodmeasurementtool.model.TraceRouteResult
import de.tum.`in`.cm.vodmeasurementtool.model.TraceRouteResultDao

@Database(entities = [Measurement::class, TraceRouteResult::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao
    abstract fun traceRouteResultDao(): TraceRouteResultDao
}