package de.tum.`in`.cm.vodmeasurementtool

import android.app.*
import android.arch.persistence.room.Room
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.*
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.LinearLayout
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.qiniu.android.netdiag.TcpPing
import com.qiniu.android.netdiag.TraceRoute
import de.tum.`in`.cm.vodmeasurementtool.model.*
import de.tum.`in`.cm.vodmeasurementtool.util.*
import kotlinx.android.synthetic.main.activity_main.*

/**
 * Foreground service that manages the video playbacks and measurements. This service lives only for the duration of one
 * single measurement session; for every new measurement session, a new instance of this service is created.
 */
class MediaPlayerService : Service() {

    /**
     * Reference to the video player
     */
    var exoPlayer: SimpleExoPlayer? = null

    /**
     * User preferences, editable by user in the app's settings
     */
    lateinit var prefsUtil: PreferencesUtil

    /**
     * WakeLock in order to start and run this service even if the app is in locked/sleep mode.
     */
    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * WifiLock to turn on wifi if it was turn off by the device in locked/sleep mode and battery saver mode.
     */
    private var wifiLock: WifiManager.WifiLock? = null

    /**
     * Listener of device lock/unlock action, in order to acquire or release wake/wifiLocks.
     */
    private val receiver = ScreenOnOffReceiver()

    /**
     * In-memory dTube-video-measurement ids needed in order to match a video measurement result to its traceRoute
     * result.
     */
    private val dtMeasurementIds = mutableListOf<Long>()

    /**
     * In-memory youtube-video-measurement ids needed in order to match a video measurement result to its traceRoute
     * result.
     */
    private val ytMeasurementIds = mutableListOf<Long>()

    /**
     * Util to fetch youtube video urls to be measured
     */
    private lateinit var ytUtil: YoutubeUtil

    /**
     * Util to fetch dTube video urls to be measured
     */
    private lateinit var dtUtil: DTubeUtil

    var isServiceRunning = false
    private var isMeasurementsRunning = false

    /**
     * Flag to mark the platform from which the currently played/measured video is from.
     */
    private var currentPlatform = Platform.UNKNOWN

    /**
     * Number of videos to play/measure during the current measurement session.
     */
    private val videosToPlay: Int
        get() {
            return ytUtil.trendingList.size + dtUtil.trendingList.size
        }

    /**
     * Return the current network type.
     */
    private val currentNetworkType: NetworkType
        get() {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?

            cm?.let {
                val network = it.activeNetwork
                val capabilities = it.getNetworkCapabilities(network)
                // if no network is available networkInfo will be null
                // otherwise check if we are connected
                if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return NetworkType.WIFI
                }
                if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    return NetworkType.CELLULAR
                }
            }

            return NetworkType.UNKNOWN
        }

    /**
     * Number of videos that have been played and measured during current measurement session.
     */
    private var videosPlayed: Int = 0

    /**
     * Number of videos for which treaceroute has been performed during current measurement session.
     */
    private var videosTraced: Int = 0

    private var collectedMeasurements: Int = 0

    /**
     * Number of currently running write-to-database background tasks. If this flag is greater than 0, this foreground
     * service would wait an extra time (1 minute) for tasks to finish before the service finishes itself.
     */
    private var tasksRunning = 0

    /**
     * A foreground service needs to show a notification that is visible during the entire lifecycle of the service.
     * The notification itself needs a unique id which the device uses to identify it.
     */
    private val ONGOING_NOTIFICATION_ID = 2309

    /**
     * A notification needs to belong to some channel, in our case we will create our own notification channel, which
     * needs a unique channel id.
     */
    private val CHANNEL_ID = "23091991"

    /**
     * The channel through which the service's notification will be managed.
     */
    private lateinit var channel: NotificationChannel

    /**
     * Reference to the local sqlite database where the collected measurement data will be stored.
     */
    private var appDb: AppDatabase? = null

    /**
     * A helper webView used to load the videos' web url pages.
     */
    var hiddenWebView: WebView? = null

    /**
     * A binder interface to bind this service to the app's main screen (MainActivity), if the app is on foreground.
     * Through this binding, the main screen can then access information about the measurement session's current
     * state, e.g. its progress, or display the currently played video.
     */
    private val binder = MediaPlayerServiceBinder()
    var mainActivity: MainActivity? = null

    private var serviceMode = ServiceMode.IDLE

    private val isDeviceLocked: Boolean
        get() {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager?
            return km?.isDeviceLocked != false
        }

    /**
     * A binder interface to bind this service to the app's main screen (MainActivity), if the app is on foreground.
     * Through this binding, the main screen can then access information about the measurement session's current
     * state, e.g. its progress, or display the currently played video.
     */
    inner class MediaPlayerServiceBinder : Binder() {
        val service: MediaPlayerService
            get() = this@MediaPlayerService
    }

    /**
     * Modes denoting the service's current activity.
     */
    private enum class ServiceMode {
        TRACING, PLAYING, IDLE
    }

    override fun onCreate() {
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) {
            L.d { "wakelock already on" }
            return
        }

        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vodme:playerservicewakelock")
            wakeLock?.acquire(7200000L)
            L.d { "wakeLock called, wakeLock = ${wakeLock?.toString()}" }
        } catch (e: Exception) {
            L.e(e) { "Failed to acquire wakeLock" }
        }
    }

    private fun acquireWifiLock() {
        if (wifiLock != null) {
            L.d { "wifilock already on" }
            return
        }

        try {
            val ws = getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = ws.createWifiLock(WifiManager.WIFI_MODE_FULL, "vodme:playerservicewifilock")
            wifiLock?.acquire()
            L.d { "wifilock called, wifilock = ${wifiLock?.toString()}" }
        } catch (e: java.lang.Exception) {
            L.e(e) { "Failed to acquire wifiLock" }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
            L.d { "wakelock released" }
        } ?: L.d { "wakeLock is null" }
        wakeLock = null
    }

    private fun releaseWifiLock() {
        wifiLock?.let {
            if (it.isHeld) {
                it.release()
            }
            L.d { "wifilock released" }
        } ?: L.d { "wifilock is null" }
        wifiLock = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        prefsUtil = PreferencesUtil(this)
        // We store the timestamp of the end of the last measurement session, and start a new session only if 1 minute
        // has already passed. This logic is to avoid leaks in case some of the background tasks of the previous session
        // has not finished in time.
        if (prefsUtil.lastServiceFinishTime != -1L && System.currentTimeMillis() - prefsUtil.lastServiceFinishTime <= 60000L) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (isDeviceLocked) {
            L.d { "Device is locked, try wakeLock" }
            acquireWakeLock()
            acquireWifiLock()
        } else {
            L.d { "Device is not locked" }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }

        registerReceiver(receiver, filter)

        prefsUtil = PreferencesUtil(this)
        prefsUtil.setAppId()

        ytUtil = YoutubeUtil(this)
        dtUtil = DTubeUtil(this)

        val name = getString(R.string.channel_name)
        val descriptionText = getString(R.string.channel_description)

        // For API 26 or newer, we need to create a channel that will manage the service's notification
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            channel = NotificationChannel(CHANNEL_ID, name, importance)
            channel.description = descriptionText
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
            notificationManager?.createNotificationChannel(channel)
        }

        // Fire a notification that denotes that this service is running. Also, this notification is further used to
        // notify user about the measurement session's current progress.
        val notification = getNotificationBuilder()
                .setContentTitle("Starting Media Player Service")
                .setProgress(0, 0, true)
                .build()
        startForeground(ONGOING_NOTIFICATION_ID, notification)

        isServiceRunning = true
        runOnServiceThread(Runnable {
            // Update main screen's UI if the app is in foreground
            mainActivity?.let {
                it.textView_app_status.text = "Media Player Service running"
            }
            startMeasurements()
        }, 10000)

        return Service.START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        mainActivity = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        clearHiddenWebViewContent()
        fireFinishedNotification()
        prefsUtil.setLastServiceFinishTimeToNow()
        hiddenWebView = null
        mainActivity = null
        releaseWakeLock()
        releaseWifiLock()
        unregisterReceiver(receiver)
        isServiceRunning = false
    }

    /**
     * Set up an invisible webView in order to load the videos' web url pages, if this option was enabled in settings.
     * The webView could not be part of the app's native UI, because if the app itself is in background (not to be
     * mistaken with this service which is always treated as on foreground once started), there would be no way to
     * access such webView. Therefore a workaround was applied, which forces a creation of an invisible webView on top
     * of all current screens, not requiring the app to be on foreground. Note that this workaround needs the
     * `SYSTEM_ALERT_WINDOW` permission, and has been marked dangerous, as it let our app draws something (the webView)
     * on top of all currently running apps. The permission has been therefore recently deprecated, consider a rework
     * using Android Bubbles.
     */
    private fun setupHiddenWebView(): WebView {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager?

        val LAYOUT_FLAG = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                LAYOUT_FLAG,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT)

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 0
        params.y = 0

        val webView = WebView(this)
        webView.visibility = View.GONE
        webView.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        windowManager?.addView(webView, params)
        return webView
    }

    fun provideHiddenWebView(): WebView? {
        if (hiddenWebView == null) {
            hiddenWebView = setupHiddenWebView()
        }

        clearHiddenWebViewContent()
        hiddenWebView?.settings?.javaScriptEnabled = true
        hiddenWebView?.settings?.javaScriptCanOpenWindowsAutomatically = true
        hiddenWebView?.settings?.domStorageEnabled = true

        return hiddenWebView
    }

    private fun clearHiddenWebViewContent() {
        hiddenWebView?.let {
            it.stopLoading()
            it.removeJavascriptInterface(DTubeJSInterface.JSInterface.iName)
            it.removeJavascriptInterface(DTubeJSInterface.JSTrendingInterface.iName)
            it.clearFocus()
            it.clearHistory()
            it.clearCache(true)
        }
    }

    fun getTempWebView(): WebView? {
        return hiddenWebView
    }

    private fun startMeasurements() {
        clearHiddenWebViewContent()
        hiddenWebView = setupHiddenWebView()

        if (appDb == null) {
            appDb = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "vod_measurements_database").build()
        }

        // need to call this to open connection with db
        appDb?.openHelper?.writableDatabase

        appDb?.let {
            // The following does not affect the measurement process anyhow, it is just for debugging.
            // Before the start of the measurement session, log all currently stored measurements.
            // Remove or comment out if you don't need this.
            DBLogger().execute()
        }

        if (!isMeasurementsRunning) {
            isMeasurementsRunning = true

            // fetch dTube's trending videos
            dtUtil.getTrendingList {
                // and then, fetch youTube's trending videos
                ytUtil.getTrendingList {
                    dtUtil.trendingList.forEach {
                        L.d { "dtData = $it" }
                    }

                    ytUtil.trendingList.forEach {
                        L.d { "ytData = $it" }
                    }

                    currentPlatform = Platform.YOUTUBE

                    // and then, start playing (and measuring) all the fetched youtube videos
                    playListByIndex(ytUtil.trendingList, 0) {

                        currentPlatform = Platform.DTUBE

                        // and then, start playing (and measuring) all the fetched dTube videos
                        playListByIndex(dtUtil.trendingList, 0) {
                            runOnServiceThread(Runnable {
                                resetPlayer()
                            })
                            runOnServiceThread(Runnable {
                                currentPlatform = Platform.YOUTUBE

                                // and then, perform traceRoute for all the fetched youtube videos
                                traceRouteListByIndex(ytUtil.trendingList, 0) {
                                    currentPlatform = Platform.DTUBE
                                    // and then, perform traceRoute for all the fetched dTube videos
                                    traceRouteListByIndex(dtUtil.trendingList, 0) {
                                        currentPlatform = Platform.UNKNOWN
                                        // and then, stop this foreground service
                                        delayedStopSelf(3000)
                                    }
                                }
                            }, 2000)
                        }
                    }

                }
            }
        }
        // Note the deep nesting level, yet there are only 2 platforms. Consider rewriting the chaining logic using
        // rxJava, which is highly chainable. I did not master the technology at the time of this app's implementation.
    }

    /**
     * Stop this foreground service, with some delay in order for all background tasks to finish.
     *
     * @param   delay   The desired delay, in ms. Recommended is at least 3000 ms.
     */
    private fun delayedStopSelf(delay: Long) {
        updateNotification("Finishing tasks...", true)
        runOnServiceThread(Runnable {
            stopMeasurements()
        })
        runOnServiceThread(Runnable {
            L.d { "Stopping self after delay of $delay s" }
            stopSelf()
        }, delay)
    }

    private fun stopMeasurements() {
        if (tasksRunning > 0) {
            L.e { "Some tasks have not finished yet. Waiting 1 min ..." }
            Thread.sleep(60000)
        }

        // export the local database, along with newly collected measurements, to server.
        exportDb {
            appDb?.let {
                if (it.isOpen) {
                    it.openHelper.close()
                }
            }
            appDb = null
            isMeasurementsRunning = false
        }
        releaseWakeLock()
        releaseWifiLock()
    }

    /**
     * Write a single measurement measured from one video to the local database.
     *
     * @param   measurement     the Measurement instance to write
     */
    private fun writeMeasurementToDb(measurement: Measurement) {
        tasksRunning++
        WriteMeasurementToDbTask(measurement).execute()
    }

    /**
     * Write a single traceRoute result measured from one video to the local database.
     *
     * @param   traceRouteResult     the TraceRouteResult instance to write
     */
    private fun writeTraceRouteResultToDb(traceRouteResult: TraceRouteResult) {
        tasksRunning++
        WriteTraceRouteResultToDbTask(traceRouteResult).execute()
    }

    /**
     * Export the local database and send its copy to remote server.
     *
     * @param   completion      Additional action to be executed when the export task finishes
     */
    private fun exportDb(completion: () -> Unit) {
        DbExportTask(this, completion).execute()
    }

    fun runOnServiceThread(runnable: Runnable, delay: Long = 0L) {
        val handler = Handler(mainLooper)
        if (delay == 0L) {
            handler.post(runnable)
        } else if (delay > 0L) {
            handler.postDelayed(runnable, delay)
        }
    }

    /**
     * Helper function to create a builder to build the notification that denotes that this service is running.
     * The notification is further used to notify user about the measurement session's current progress.
     */
    private fun getNotificationBuilder(): Notification.Builder {
        val activityIntent = Intent(this, MainActivity::class.java)
        val activityPendingIntent = PendingIntent.getActivity(this, 0, activityIntent, 0)

        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return notificationBuilder
                .setSmallIcon(R.drawable.ic_play_button)
                .setContentIntent(activityPendingIntent)
    }

    /**
     * Helper function to update the running notification.
     *
     * @param   contentText             The text to be shown on the notification
     * @param   indeterminateProgress   If set to true, an indeterminate progressBar is shown on the notification.
     */
    fun updateNotification(contentText: String, indeterminateProgress: Boolean = false) {
        val notification = getNotificationBuilder()
                .setContentTitle(contentText)
                .setStyle(Notification.BigTextStyle().bigText(contentText))
                .setProgress(0, 0, indeterminateProgress)
                .build()
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
        notificationManager?.notify(ONGOING_NOTIFICATION_ID, notification)
    }

    /**
     * Helper function to update the running notification.
     */
    private fun updateProgressNotification() {
        val contentText = when (serviceMode) {
            MediaPlayerService.ServiceMode.TRACING ->
                "Tracing: $videosTraced/$videosToPlay"

            MediaPlayerService.ServiceMode.PLAYING ->
                "Playing: $videosPlayed/$videosToPlay"

            MediaPlayerService.ServiceMode.IDLE ->
                getText(R.string.notification_message).toString()
        }

        val progress = when (serviceMode) {
            ServiceMode.IDLE ->
                0

            ServiceMode.PLAYING ->
                videosPlayed

            ServiceMode.TRACING ->
                videosTraced
        }

        val notification = getNotificationBuilder()
                .setContentTitle(contentText)
                .setStyle(Notification.BigTextStyle().bigText(contentText))
                .setProgress(videosToPlay, progress, false)
                .build()
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
        notificationManager?.notify(ONGOING_NOTIFICATION_ID, notification)
    }

    /**
     * Helper function to update the running notification at the end of the entire measurement session.
     */
    private fun fireFinishedNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
        val channel_id = "19910923"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channel_id, "service_finished_channel", NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "channel to notify that background tasks have finished"
            notificationManager?.createNotificationChannel(channel)
        }

        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channel_id)
        } else {
            Notification.Builder(this)
        }
        val notification = notificationBuilder
                .setContentTitle("Measurements finished.")
                .setSmallIcon(R.drawable.ic_play_button)
                .setStyle(Notification.BigTextStyle().bigText(
                        "Measurements have finished. Collected $collectedMeasurements measurements."
                ))
                .setProgress(0, 0, false)
                .build()
        notificationManager?.notify(1991, notification)
    }

    private fun resetPlayer() {
        try {
            exoPlayer?.stop()
        } catch (e: Exception) {
            L.e(e) { "Failed to stop player" }
        }
        exoPlayer?.release()
        exoPlayer = ExoPlayerFactory.newSimpleInstance(this, DefaultTrackSelector())
        if (prefsUtil.muteVideos) {
            exoPlayer?.volume = 0f

        }
        mainActivity?.let {
            it.runOnUiThread {
                it.playerView.player = exoPlayer
            }
        }
    }

    /**
     * Helper function to play a list of videos one by one (in current implementation, either the fetched dTube or
     * youtube trending videos), and collect corresponding measurements.
     *
     * @param   list        The list of videos to play and start a measurement for
     * @param   index       Current index of the video in the list to measure. Pass in '0' to play the entire video list
     * @param   completion  Additional action to be executed after the entire list has been played and measured
     */
    private fun playListByIndex(list: List<BasicVideoData>, index: Int, completion: () -> Unit) {
        if (index >= list.size) {
            serviceMode = ServiceMode.IDLE
            // execute the additional action once all videos have been played
            completion.invoke()
            return
        }

        serviceMode = ServiceMode.PLAYING
        videosPlayed++
        updateProgressNotification()

        val videoContent = VideoContent(list[index])

        // start a single measurement for the video from the current list's index
        startSingleMeasurement(videoContent) {
            // after this single measurement has finished, play and measure the video at next index
            playListByIndex(list, index + 1, completion)
        }
    }

    /**
     * Start a single measurement for the video described by the `videoContent` parameter. The single measurement
     * includes a tcpPing to the video's source url, in order to acquire the tcp connection time, as well as playing
     * the video and collecting its playback data.
     *
     * @param   videoContent    Parameter describing the video to play, see the `VideoContent` class
     * @param   completion      Additional action to be executed once this single measurement has finished
     */
    private fun startSingleMeasurement(videoContent: VideoContent, completion: () -> Unit) {
        // Run tcpPing to the video's source url first, to get the tcp connection time
        tcpPing(videoContent.basicVideoData.srcUrl) { tcpConnectionTimeMs -> // the collected tcp connection time
            val tempMeasurement = TempMeasurement(videoContent.basicVideoData, videoContent.platform, currentNetworkType)
            tempMeasurement.tcpConnectionTimeMs = tcpConnectionTimeMs

            // Then, start the video's playback and collect playback data
            this@MediaPlayerService.runOnServiceThread(Runnable {
                resetPlayer()
                preparePlayer(tempMeasurement) { resultMeasurement ->
                    L.d { "resultMeasurement: $resultMeasurement" }
                    // At the end of the playback, store the measurement's id to match them later with corresponding
                    // traceRoute results.
                    when (currentPlatform) {
                        Platform.YOUTUBE -> {
                            resultMeasurement?.let {
                                ytMeasurementIds.add(resultMeasurement.id)
                            } ?: ytMeasurementIds.add(-1)
                        }

                        Platform.DTUBE -> {
                            resultMeasurement?.let {
                                dtMeasurementIds.add(resultMeasurement.id)
                            } ?: dtMeasurementIds.add(-1)
                        }

                        else ->
                            Unit

                    }

                    resultMeasurement?.let {
                        writeMeasurementToDb(it)
                    }

                    if (isServiceRunning) {
                        completion.invoke()
                    }
                }
            })
        }
    }

    private fun createDefaultMediaSource(url: String): MediaSource {
        val dataSourceFactory = DefaultDataSourceFactory(this, Util.getUserAgent(this, "VoDMeasurmentTool"))
        val uri = Uri.parse(url)
        return ExtractorMediaSource.Factory(dataSourceFactory).createMediaSource(uri)
    }

    /**
     * Setup the video player to play the video described in the `tempMeasurement` parameter.
     *
     * @param   tempMeasurement     A temporary measurement instance where the video's basic data (urls) are stored.
     *                              It will also be used to store data that will be collected during the playback.
     * @param   onPlayerEnded       Additional action to be executed once the playback finishes.
     */
    private fun preparePlayer(tempMeasurement: TempMeasurement, onPlayerEnded: (measurement: Measurement?) -> Unit) {
        exoPlayer?.let {
            val mediaSource = createDefaultMediaSource(tempMeasurement.sourceUrl)
            it.playWhenReady = true
            it.addAnalyticsListener(VideoAnalyticsListener(it, this, tempMeasurement, onPlayerEnded))
            it.prepare(mediaSource)
        }
    }

    /**
     * Function to acquire the tcp connection time to a video's source url. For this, the TcpPing library is used,
     * see https://github.com/qiniu/android-netdiag for detailed implementation
     *
     * @param   url         The video's source url for which to acquire the tcp connection time
     * @param   completion  Additional action to be executed once the tcp connection time is acquired. The collected
     *                      tcp connection time is passed through this action for further processing
     */
    private fun tcpPing(url: String, completion: (tcpConnectionTime: Int) -> Unit) {
        val dest = url.getUrlHost()
        TcpPing.start(dest, 80, 3,
                { line ->
                    L.d { "TcpPing: $line" }
                },
                { result ->
                    L.d { "min=${result.minTime}, max=${result.maxTime}, avg=${result.avgTime}" }
                    if (isServiceRunning) {
                        completion.invoke(result.minTime)
                    }
                })
    }

    /**
     * Helper function to run traceRoute for list of videos one by one (in current implementation, either the fetched
     * dTube or youtube trending videos).
     *
     * @param   list        The list of videos to run traceRoute for
     * @param   index       Current index of the video in the list to run traceRoute for. Pass in '0' to run traceRoute
     *                      for the entire video list (one by one)
     * @param   completion  Additional action to be executed after traceRoute has been run for the entire video list
     */
    private fun traceRouteListByIndex(list: List<BasicVideoData>, index: Int, completion: () -> Unit) {
        if (index >= list.size) {
            serviceMode = ServiceMode.IDLE
            // execute the additional action once traceRoute has been run for all videos from the list
            completion.invoke()
            return
        }

        serviceMode = ServiceMode.TRACING
        videosTraced++
        updateProgressNotification()

        val videoContent = VideoContent(list[index])

        // run a single traceRoute for the video from the current list's index
        traceRoute(videoContent.basicVideoData) { traceRouteResult -> // the collected traceRoute data
            // match this traceRoute result with the already collected measurement data of the corresponding video
            when (currentPlatform) {
                Platform.YOUTUBE ->
                    traceRouteResult?.measurementId = ytMeasurementIds[index]

                Platform.DTUBE ->
                    traceRouteResult?.measurementId = dtMeasurementIds[index]

                else ->
                    Unit
            }

            traceRouteResult?.let {
                writeTraceRouteResultToDb(TraceRouteResult(it))
            }

            // after this single traceRoute has finished, run traceRoute for the video at next index
            traceRouteListByIndex(list, index + 1, completion)
        }
    }

    private var currentTracerouteResult: TempTraceRouteResult? = null
    private var currentTracedUrl: String? = null

    /**
     * Function to run traceRoute for the video's source url. For this, the https://github.com/qiniu/android-netdiag
     * library was used, visit for detailed implementation.
     */
    private fun traceRoute(basicVideoData: BasicVideoData, completion: (traceRouteResult: TempTraceRouteResult?) -> Unit) {
        L.d { "Tracing ${basicVideoData.srcUrl} ..." }
        var hopCount = 0
        currentTracerouteResult = null
        currentTracedUrl = basicVideoData.srcUrl
        TraceRoute.start(basicVideoData.srcUrl.getUrlHost(), { hopCount++ },
                { result ->
                    try {
                        Log.e("TraceRoute:-- ", result.content())
                        Log.e("TraceRoute:-- ", "ip = ${result.ip}")
                        val content = result.content().trim().split(Regex("\\s+"))
                        val contentSize = content.size
                        val source = content.get(1)
                        val destination = content.get(contentSize - 3)
                        val rttMs: Long = content.get(contentSize - 2).toFloat().toLong() * 2

                        if (basicVideoData.srcUrl == currentTracedUrl) {
                            currentTracerouteResult = TempTraceRouteResult(source, destination, hopCount, rttMs, basicVideoData.platform)
                        }
                        L.d { "TraceRoute:-- : res = $currentTracerouteResult" }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                })
        runOnServiceThread(Runnable {
            if (currentTracerouteResult == null) {
                runOnServiceThread(Runnable {
                    L.d { "TracerouteResult handed after 90s" }
                    if (isServiceRunning) {
                        completion.invoke(currentTracerouteResult)
                    } else {
                        L.d { "TracerouteResult: service not running" }
                        completion.invoke(null)
                    }
                }, 30000)
            } else {
                L.d { "TracerouteResult handed after 60s" }
                if (isServiceRunning) {
                    completion.invoke(currentTracerouteResult)
                } else {
                    L.d { "TracerouteResult: service not running" }
                    completion.invoke(null)
                }
            }
        }, 60000)
    }

    private inner class WriteMeasurementToDbTask(private val measurement: Measurement) : AsyncTask<Unit, Unit, Unit>() {

        override fun doInBackground(vararg params: Unit?) {
            try {
                appDb?.let {
                    if (it.isOpen) {
                        it.measurementDao().insert(measurement)
                        L.d { "writeDB: measurement written" }
                    }
                }
                collectedMeasurements++
            } catch (e: java.lang.Exception) {
                L.e(e) { "Unable to write measurement to DB." }
            }
        }

        override fun onPostExecute(result: Unit?) {
            tasksRunning--
            super.onPostExecute(result)
        }
    }

    private inner class WriteTraceRouteResultToDbTask(private val traceRouteResult: TraceRouteResult)
        : AsyncTask<Unit, Unit, Unit>() {

        override fun doInBackground(vararg params: Unit?) {
            try {
                appDb?.let {
                    if (it.isOpen) {
                        it.traceRouteResultDao().insert(traceRouteResult)
                        L.d { "writeDB: traceroute written" }
                    }
                }
            } catch (e: java.lang.Exception) {
                L.e(e) { "Unable to write tracerouteResult to DB." }
            }
        }

        override fun onPostExecute(result: Unit?) {
            tasksRunning--
            super.onPostExecute(result)
        }
    }

    private inner class DBLogger : AsyncTask<Unit, Unit, Unit>() {
        override fun doInBackground(vararg params: Unit?) {
            try {
                appDb?.let {
                    if (it.isOpen) {
                        it.measurementDao().getAllMeasurements().forEach {
                            L.d { "fromDB: $it" }
                        }

                        it.traceRouteResultDao().getAllTraceRouteResults().forEach {
                            L.d { "fromDB: $it" }
                        }
                    }
                }
            } catch (e: java.lang.Exception) {
                L.e(e) { "Unable to read from DB." }
            }
        }
    }

    private inner class ScreenOnOffReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                when (it.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        L.d { "Device woke up" }
                        releaseWakeLock()
                        releaseWifiLock()
                    }

                    Intent.ACTION_SCREEN_OFF -> {
                        L.d { "Device went to sleep" }
                        acquireWakeLock()
                        acquireWifiLock()
                    }
                }
            }
        }
    }
}