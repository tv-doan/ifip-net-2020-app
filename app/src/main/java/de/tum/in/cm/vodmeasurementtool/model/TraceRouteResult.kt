package de.tum.`in`.cm.vodmeasurementtool.model

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey
import android.arch.persistence.room.PrimaryKey
import com.google.gson.Gson

class TempTraceRouteResult(val source: String, val destination: String, val hopCount: Int,
                           val rttMs: Long, platform: Platform, timeStamp: Long = System.currentTimeMillis()) {

    val id: Long = timeStamp
    var measurementId: Long = 0L
    val platform: String = platform.strValue
    val dateTime = timeStamp.formattedDateTime()

    override fun toString(): String {
        return Gson().toJson(this)
    }
}

@Entity(tableName = "traceroutes")
data class TraceRouteResult(@PrimaryKey(autoGenerate = false)
                            @ColumnInfo(name = "id") val id: Long,
                            @ForeignKey(entity = Measurement::class,
                                    parentColumns = ["id"],
                                    childColumns = ["measurement_id"],
                                    onDelete = ForeignKey.CASCADE)
                            val measurementId: Long,
                            @field:ColumnInfo(name = "source") val source: String,
                            @field:ColumnInfo(name = "destination") val destination: String,
                            @field:ColumnInfo(name = "hop_count") val hopCount: Int,
                            @field:ColumnInfo(name = "rttMs") val rttMs: Long,
                            @field:ColumnInfo(name = "platform") val platform: String,
                            @field:ColumnInfo(name = "date_time") val dateTime: String
) {

    constructor(temp: TempTraceRouteResult) : this(
            temp.id, temp.measurementId, temp.source, temp.destination, temp.hopCount,
            temp.rttMs, temp.platform, temp.dateTime
    )

    override fun toString(): String {
        return Gson().toJson(this)
    }
}