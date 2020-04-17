package de.tum.`in`.cm.vodmeasurementtool.model

import android.arch.persistence.room.*
import com.google.gson.Gson
import de.tum.`in`.cm.vodmeasurementtool.util.BasicVideoData
import de.tum.`in`.cm.vodmeasurementtool.util.L
import java.text.SimpleDateFormat
import java.util.*

class TempMeasurement(basicVideoData: BasicVideoData, platform: Platform,
                      networkType: NetworkType, timeStamp: Long = System.currentTimeMillis()) {

    val id: Long = timeStamp
    val platform: String = platform.strValue
    val sourceUrl = basicVideoData.srcUrl
    val webUrl = basicVideoData.webUrl
    val timeToLoadWebPageMs = basicVideoData.timeToLoadWebPageMs
    val dateTime = timeStamp.formattedDateTime()
    var timeToReachSourceMs: Long = -1 //ms
    var initialBufferSizeMs: Long = -1 //ms
    var bytesLoaded: Long = -1 //bytes
    var loadDurationMs: Long = -1 //ms
    var quality: Quality? = null
        set(value) {
            field = value
            value?.let {
                videoWidth = it.width
                videoHeight = it.height
            }
        }
    var videoWidth: Int = 0
    var videoHeight: Int = 0
    var contentDurationMs: Long = -1 //ms
    var totalPlayedDurationMs: Long = -1 //ms
    var startUpDelayMs: Long = -1 //ms
    var stallDurationMs: Long = -1//ms
    var bandwidthEstimateBps: Long = -1 //bps
    var initialExoProcessingTimeMs: Long = -1//time from initializing exoplayer until first request for media data is sent
    var tcpConnectionTimeMs: Int = -1//ms
    var host: String = basicVideoData.srcUrl.getUrlHost()
    var networkType: String = networkType.strValue

    override fun toString(): String {
        return Gson().toJson(this)
    }
}

@Entity(tableName = "measurements")
data class Measurement(@PrimaryKey(autoGenerate = false)
                       @ColumnInfo(name = "id") val id: Long,
                       @field:ColumnInfo(name = "platform") val platform: String,
                       @field:ColumnInfo(name = "source_url") val sourceUrl: String,
                       @field:ColumnInfo(name = "web_url") val webUrl: String,
                       @field:ColumnInfo(name = "time_to_load_web_page_ms") val timeToLoadWebPageMs: Long,
                       @field:ColumnInfo(name = "date_time") val dateTime: String,
                       @field:ColumnInfo(name = "time_to_reach_source_ms") val timeToReachSourceMs: Long,
                       @field:ColumnInfo(name = "initial_buffer_size_ms") val initialBufferSizeMs: Long,
                       @field:ColumnInfo(name = "bytes_loaded") val bytesLoaded: Long,
                       @field:ColumnInfo(name = "load_duration_ms") val loadDurationMs: Long,
                       @field:ColumnInfo(name = "video_width") val videoWidth: Int,
                       @field:ColumnInfo(name = "video_height") val videoHeight: Int,
                       @field:ColumnInfo(name = "content_duration_ms") val contentDurationMs: Long,
                       @field:ColumnInfo(name = "total_played_duration_ms") val totalPlayedDurationMs: Long,
                       @field:ColumnInfo(name = "startup_delay_ms") val startUpDelayMs: Long,
                       @field:ColumnInfo(name = "stall_duration_ms") val stallDurationMs: Long,
                       @field:ColumnInfo(name = "bandwidth_estimate_bps") val bandwidthEstimateBps: Long,
                       @field:ColumnInfo(name = "initial_exo_processing_time_ms") val initialExoProcessingTimeMs: Long,//time from initializing exoplayer until first request for media data is sent
                       @field:ColumnInfo(name = "tcp_connection_time_ms") val tcpConnectionTimeMs: Int,
                       @field:ColumnInfo(name = "host") val host: String,
                       @field:ColumnInfo(name = "network_type") val networkType: String) {

    constructor(tempMeasurement: TempMeasurement) : this(
            tempMeasurement.id, tempMeasurement.platform, tempMeasurement.sourceUrl, tempMeasurement.webUrl,
            tempMeasurement.timeToLoadWebPageMs, tempMeasurement.dateTime, tempMeasurement.timeToReachSourceMs,
            tempMeasurement.initialBufferSizeMs, tempMeasurement.bytesLoaded, tempMeasurement.loadDurationMs,
            tempMeasurement.videoWidth, tempMeasurement.videoHeight, tempMeasurement.contentDurationMs,
            tempMeasurement.totalPlayedDurationMs, tempMeasurement.startUpDelayMs, tempMeasurement.stallDurationMs,
            tempMeasurement.bandwidthEstimateBps, tempMeasurement.initialExoProcessingTimeMs,
            tempMeasurement.tcpConnectionTimeMs, tempMeasurement.host, tempMeasurement.networkType
    )

    override fun toString(): String {
        return "{\n" +
                "id = $id\nplatform = $platform\nsourceUrl = $sourceUrl\nwebUrl = $webUrl\n" +
                "timeToLoadWebPageMs = $timeToLoadWebPageMs\ndateTime = $dateTime\n" +
                "timeToReachSourceMs = $timeToReachSourceMs\ninitialBufferSizeMs = $initialBufferSizeMs\n" +
                "bytesLoaded = $bytesLoaded\nloadDurationMs = $loadDurationMs\nvideoWidth = $videoWidth\n" +
                "videoHeight = $videoHeight\ncontentDurationMs = $contentDurationMs\ntotalPlayedDurationMs = $totalPlayedDurationMs\n" +
                "startUpDelayMs = $startUpDelayMs\nstallDurationMs = $stallDurationMs\n" +
                "bandwidthEstimateBps = $bandwidthEstimateBps\ninitialExoProcessingTimeMs = $initialExoProcessingTimeMs\n" +
                "tcpConnectionTimeMs = $tcpConnectionTimeMs\nhost = $host\nnetworkType = $networkType\n" +
                "}"
    }
}

enum class Platform(val strValue: String) {
    YOUTUBE("youtube"), DTUBE("dtube"), UNKNOWN("unknown")
}

enum class NetworkType(val strValue: String) {
    WIFI("wifi"), CELLULAR("cellular"), UNKNOWN("unknown")
}

class Quality(val width: Int, val height: Int)

fun String.getUrlHost(): String {
    L.d { "unprocessedUrl=$this" }
    val firstSlashIdx = this.indexOf('/')
    val secondSlashIdx = this.indexOf('/', firstSlashIdx + 1)
    val thirdSlashIdx = this.indexOf('/', secondSlashIdx + 1)
    return this.substring(secondSlashIdx + 1, thirdSlashIdx)
}

fun Long.formattedDateTime(): String {
    val format = SimpleDateFormat("dd-MM-yyyy_HH:mm:ss")
    return format.format(Date(this))
}