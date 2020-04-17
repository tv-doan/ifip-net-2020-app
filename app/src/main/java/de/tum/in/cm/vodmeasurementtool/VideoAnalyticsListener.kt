package de.tum.`in`.cm.vodmeasurementtool

import android.os.*
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.metadata.Metadata
import com.google.android.exoplayer2.source.MediaSourceEventListener
import de.tum.`in`.cm.vodmeasurementtool.model.Measurement
import de.tum.`in`.cm.vodmeasurementtool.model.Quality
import de.tum.`in`.cm.vodmeasurementtool.model.TempMeasurement
import de.tum.`in`.cm.vodmeasurementtool.util.L
import java.io.IOException

class VideoAnalyticsListener(private val player: ExoPlayer, private val playerService: MediaPlayerService,
                             private val tempMeasurement: TempMeasurement, private val completion: (measurement: Measurement?) -> Unit)
    : AnalyticsListener {

    private var mediaStartedPlaying = false

    private var loadingStartedTimestamp = -1L
    private var firstBufferingStartedTimestamp = -1L

    private val countDownTimer = CustomCountDownTimer(60000)

    private var measurementHandled = false

    private inner class CustomCountDownTimer(period: Long) : CountDownTimer(period, 1000) {

        private var playbackError: Exception? = null

        override fun onTick(millisUntilFinished: Long) {
        }

        override fun onFinish() {

            if (measurementHandled) return

            measurementHandled = true

            if (playbackError != null) {
                return completion.invoke(null)
            }

            try {
                L.d { "player: buffered=${player.bufferedPercentage}%, bufferedMilis=${player.bufferedPosition}" }
                tempMeasurement.totalPlayedDurationMs = player.currentPosition
                tempMeasurement.stallDurationMs = if (tempMeasurement.contentDurationMs > 60000) {
                    60000 - player.currentPosition
                } else {
                    tempMeasurement.contentDurationMs - tempMeasurement.totalPlayedDurationMs
                }
                if (tempMeasurement.stallDurationMs < 0) {
                    tempMeasurement.stallDurationMs = 0
                }

                if (tempMeasurement.videoWidth <= 0) {
                    tempMeasurement.videoWidth = (player as SimpleExoPlayer).videoFormat.width
                }
                if (tempMeasurement.videoHeight <= 0) {
                    tempMeasurement.videoHeight = (player as SimpleExoPlayer).videoFormat.height
                }
                player.playWhenReady = false
                player.stop()

                playerService.runOnServiceThread(Runnable {

                    completion.invoke(Measurement(tempMeasurement))
                }, 5000)
            } catch (e: Exception) {
                completion.invoke(null)
            }
        }

        fun finishWithError(e: Exception) {
            playbackError = e
            this.cancel()
            this.onFinish()
        }

        fun finish() {
            this.cancel()
            this.onFinish()
        }
    }

    override fun onPlaybackParametersChanged(eventTime: AnalyticsListener.EventTime?, playbackParameters: PlaybackParameters?) {
        L.d { "onPlaybackParametersChanged" }
    }

    override fun onPlayerError(eventTime: AnalyticsListener.EventTime?, error: ExoPlaybackException?) {
        L.e(error) { "onPlayerError" }
        countDownTimer.finishWithError(error ?: ExoPlaybackException.createForSource(IOException()))
    }

    override fun onLoadingChanged(eventTime: AnalyticsListener.EventTime?, isLoading: Boolean) {
        L.d {
            "onLoadingChanged(): isLoading=$isLoading, currentPlayback=${eventTime?.currentPlaybackPositionMs
                    ?: -1} ms, " +
                    "buffered=${eventTime?.totalBufferedDurationMs ?: -1} ms"
        }
    }

    override fun onBandwidthEstimate(eventTime: AnalyticsListener.EventTime?, totalLoadTimeMs: Int,
                                     totalBytesLoaded: Long, bitrateEstimate: Long) {
        //measurement.bandwidthEstimateBps = bitrateEstimate
        tempMeasurement.loadDurationMs = totalLoadTimeMs.toLong()
        tempMeasurement.bytesLoaded = totalBytesLoaded

        if (tempMeasurement.loadDurationMs > 0 && tempMeasurement.bytesLoaded > 0) {
            tempMeasurement.bandwidthEstimateBps = ((tempMeasurement.bytesLoaded.toDouble() / tempMeasurement.loadDurationMs.toDouble()) * 8000f).toLong() //1 Bps = 8 bps
        }
        L.d { "onBandwidthEstimate(): estimate=$bitrateEstimate bps, loadTime = $totalLoadTimeMs, bytesLoaded=$totalBytesLoaded" }
    }

    override fun onPlayerStateChanged(eventTime: AnalyticsListener.EventTime?, playWhenReady: Boolean, playbackState: Int) {
        val now = System.currentTimeMillis()
        L.d { "onPlayerStateChanged(): state=${getReadableState(playbackState, playWhenReady)}" }
        if (playbackState == Player.STATE_ENDED) {
            countDownTimer.finish()
        } else if (playbackState == Player.STATE_READY) {
            if (!mediaStartedPlaying && playWhenReady) {
                mediaStartedPlaying = true

                countDownTimer.start()
                tempMeasurement.startUpDelayMs = now - loadingStartedTimestamp
                val buffer = eventTime?.totalBufferedDurationMs ?: -1
                tempMeasurement.initialBufferSizeMs = buffer
                L.d { "onPlayerStateChanged(): buffer=$buffer ms" }
            }
            if (tempMeasurement.contentDurationMs < 0) {
                tempMeasurement.contentDurationMs = player.duration
            }
        } else if (playbackState == Player.STATE_BUFFERING && firstBufferingStartedTimestamp < 0) {
            firstBufferingStartedTimestamp = now
        }
    }

    override fun onLoadStarted(eventTime: AnalyticsListener.EventTime?,
                               loadEventInfo: MediaSourceEventListener.LoadEventInfo?,
                               mediaLoadData: MediaSourceEventListener.MediaLoadData?) {
        loadingStartedTimestamp = System.currentTimeMillis()
        if (firstBufferingStartedTimestamp != -1L) {
            tempMeasurement.initialExoProcessingTimeMs = loadingStartedTimestamp - firstBufferingStartedTimestamp
        }
        L.d {
            "onLoadStarted(): loadDuration=${loadEventInfo?.loadDurationMs
                    ?: -1} ms, bytesLoaded=${loadEventInfo?.bytesLoaded ?: -1}, " +
                    "uri = ${loadEventInfo?.uri.toString()}, dataSpec = ${loadEventInfo?.dataSpec}, " +
                    "startMs = ${mediaLoadData?.mediaStartTimeMs}, endMs = ${mediaLoadData?.mediaEndTimeMs}"
        }
    }

    override fun onLoadCanceled(eventTime: AnalyticsListener.EventTime?,
                                loadEventInfo: MediaSourceEventListener.LoadEventInfo?,
                                mediaLoadData: MediaSourceEventListener.MediaLoadData?) {
        L.d {
            "onLoadCanceled(): loadDuration=${loadEventInfo?.loadDurationMs
                    ?: -1} ms, bytesLoaded=${loadEventInfo?.bytesLoaded ?: -1}"
        }
    }

    override fun onVideoSizeChanged(eventTime: AnalyticsListener.EventTime?,
                                    width: Int, height: Int,
                                    unappliedRotationDegrees: Int,
                                    pixelWidthHeightRatio: Float) {
        tempMeasurement.quality = Quality(width, height)
        L.d { "onVideoSizeChanged(): width=$width, height=$height, pixelRatio=$pixelWidthHeightRatio, buffered=${eventTime?.totalBufferedDurationMs}" }
    }

    override fun onLoadCompleted(eventTime: AnalyticsListener.EventTime?,
                                 loadEventInfo: MediaSourceEventListener.LoadEventInfo?,
                                 mediaLoadData: MediaSourceEventListener.MediaLoadData?) {

        tempMeasurement.loadDurationMs = loadEventInfo?.loadDurationMs ?: -1
        tempMeasurement.bytesLoaded = loadEventInfo?.bytesLoaded ?: -1
        L.d {
            "onLoadCompleted(): loadDuration=${loadEventInfo?.loadDurationMs
                    ?: -1} ms, bytesLoaded=${loadEventInfo?.bytesLoaded ?: -1}"
        }
    }

    override fun onLoadError(eventTime: AnalyticsListener.EventTime?, loadEventInfo: MediaSourceEventListener.LoadEventInfo?,
                             mediaLoadData: MediaSourceEventListener.MediaLoadData?, error: IOException?, wasCanceled: Boolean) {
        L.e(error) { "onLoadError()" }
        countDownTimer.finishWithError(error ?: IOException())
    }

    override fun onMetadata(eventTime: AnalyticsListener.EventTime?, metadata: Metadata?) {
        L.d { "onMetadata(): ${metadata?.length()}" }
    }

    override fun onReadingStarted(eventTime: AnalyticsListener.EventTime?) {
        tempMeasurement.timeToReachSourceMs = System.currentTimeMillis() - loadingStartedTimestamp
        L.d { "onReadingStarted(): buffered=${eventTime?.totalBufferedDurationMs}" }
    }

    private fun getReadableState(state: Int, playWhenReady: Boolean): String {
        return when (state) {
            Player.STATE_IDLE ->
                "idle"

            Player.STATE_BUFFERING ->
                "buffering"

            Player.STATE_READY -> {
                if (playWhenReady) {
                    "playing"
                } else {
                    "paused"
                }
            }

            Player.STATE_ENDED ->
                "ended"

            else ->
                "unknown"
        }
    }
}