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

class MediaPlayerService : Service() {

    var exoPlayer: SimpleExoPlayer? = null
    lateinit var prefsUtil: PreferencesUtil
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val receiver = ScreenOnOffReceiver()

    private val dtMeasurementIds = mutableListOf<Long>()
    private val ytMeasurementIds = mutableListOf<Long>()

    private lateinit var ytUtil: YoutubeUtil
    private lateinit var dtUtil: DTubeUtil

    var isServiceRunning = false
    private var isMeasurementsRunning = false
    private var currentPlatform = Platform.UNKNOWN

    private val videosToPlay: Int
        get() {
            return ytUtil.trendingList.size + dtUtil.trendingList.size
        }

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

    private var videosPlayed: Int = 0
    private var videosTraced: Int = 0
    private var collectedMeasurements: Int = 0

    private var tasksRunning = 0

    private val ONGOING_NOTIFICATION_ID = 2309
    private val CHANNEL_ID = "23091991"

    private lateinit var channel: NotificationChannel

    private var appDb: AppDatabase? = null

    var hiddenWebView: WebView? = null

    private val binder = MediaPlayerServiceBinder()
    var mainActivity: MainActivity? = null
    private var serviceMode = ServiceMode.IDLE

    private val isDeviceLocked: Boolean
        get() {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager?
            return km?.isDeviceLocked != false
        }

    inner class MediaPlayerServiceBinder : Binder() {
        val service: MediaPlayerService
            get() = this@MediaPlayerService
    }

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

        //at least 1 minute cooldown between measurement cycles
        prefsUtil = PreferencesUtil(this)
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

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            channel = NotificationChannel(CHANNEL_ID, name, importance)
            channel.description = descriptionText
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
            notificationManager?.createNotificationChannel(channel)
        }

        val notification = getNotificationBuilder()
                .setContentTitle("Starting Media Player Service")
                .setProgress(0, 0, true)
                .build()
        startForeground(ONGOING_NOTIFICATION_ID, notification)

        isServiceRunning = true
        runOnServiceThread(Runnable {
            mainActivity?.let {
                it.textView_app_status.text = "Media Player Service running"
                //it.progressBar_load_lists.visibility = View.INVISIBLE
            }
            //startMeasurementsMock()
            startMeasurements()
        }, 10000)

        return Service.START_STICKY
    }

    private fun startMeasurementsMock() {
        runOnServiceThread(Runnable {
            stopSelf()
        }, 10000)
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
        //hiddenWebView?.settings?.cacheMode = WebSettings.LOAD_NO_CACHE
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

        //need to call this to open connection with db
        appDb?.openHelper?.writableDatabase

        appDb?.let {
            DBLogger().execute()
        }

        if (!isMeasurementsRunning) {
            isMeasurementsRunning = true

            dtUtil.getTrendingList {
                ytUtil.getTrendingList {

                    dtUtil.trendingList.forEach {
                        L.d { "dtData = $it" }
                    }

                    ytUtil.trendingList.forEach {
                        L.d { "ytData = $it" }
                    }

                    currentPlatform = Platform.YOUTUBE
                    playListByIndex(ytUtil.trendingList, 0) {
                        currentPlatform = Platform.DTUBE
                        playListByIndex(dtUtil.trendingList, 0) {
                            runOnServiceThread(Runnable {
                                resetPlayer()
                            })
                            runOnServiceThread(Runnable {
                                currentPlatform = Platform.YOUTUBE
                                traceRouteListByIndex(ytUtil.trendingList, 0) {
                                    currentPlatform = Platform.DTUBE
                                    traceRouteListByIndex(dtUtil.trendingList, 0) {
                                        currentPlatform = Platform.UNKNOWN
                                        delayedStopSelf(3000)
                                    }
                                }
                            }, 2000)
                        }
                    }

                }
            }
        }
    }

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

    private fun writeMeasurementToDb(measurement: Measurement) {
        tasksRunning++
        WriteMeasurementToDbTask(measurement).execute()
    }

    private fun writeTraceRouteResultToDb(traceRouteResult: TraceRouteResult) {
        tasksRunning++
        WriteTraceRouteResultToDbTask(traceRouteResult).execute()
    }

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
                //.setContentIntent(pendingIntent)
                .setContentIntent(activityPendingIntent)
    }

    fun updateNotification(contentText: String, indeterminateProgress: Boolean = false) {
        //L.d { "updateNotification(): $message" }
        val notification = getNotificationBuilder()
                //.setContentText(contentText)
                .setContentTitle(contentText)
                .setStyle(Notification.BigTextStyle().bigText(contentText))
                .setProgress(0, 0, indeterminateProgress)
                .build()
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
        notificationManager?.notify(ONGOING_NOTIFICATION_ID, notification)
    }

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
                //.setContentText(contentText)
                .setContentTitle(contentText)
                .setStyle(Notification.BigTextStyle().bigText(contentText))
                .setProgress(videosToPlay, progress, false)
                .build()
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager?
        notificationManager?.notify(ONGOING_NOTIFICATION_ID, notification)
    }

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
                //.setContentText("Measurements have finished. Collected $collectedMeasurements measurements.")
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

    private fun playListByIndex(list: List<BasicVideoData>, index: Int, completion: () -> Unit) {
        if (index >= list.size) {
            serviceMode = ServiceMode.IDLE
            completion.invoke()
            return
        }

        serviceMode = ServiceMode.PLAYING
        videosPlayed++
        updateProgressNotification()

        val videoContent = VideoContent(list[index])
        startSingleMeasurement(videoContent) {
            playListByIndex(list, index + 1, completion)
        }
    }

    private fun startSingleMeasurement(videoContent: VideoContent, completion: () -> Unit) {
        tcpPing(videoContent.basicVideoData.srcUrl) { tcpConnectionTimeMs ->
            val tempMeasurement = TempMeasurement(videoContent.basicVideoData, videoContent.platform, currentNetworkType)
            tempMeasurement.tcpConnectionTimeMs = tcpConnectionTimeMs

            this@MediaPlayerService.runOnServiceThread(Runnable {
                resetPlayer()
                preparePlayer(tempMeasurement) { resultMeasurement ->
                    L.d { "resultMeasurement: $resultMeasurement" }
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

    private fun preparePlayer(tempMeasurement: TempMeasurement, onPlayerEnded: (measurement: Measurement?) -> Unit) {
        exoPlayer?.let {
            val mediaSource = createDefaultMediaSource(tempMeasurement.sourceUrl)
            //val clippedMediaSource = ClippingMediaSource(mediaSource, 0, 60000000)
            it.playWhenReady = true
            it.addAnalyticsListener(VideoAnalyticsListener(it, this, tempMeasurement, onPlayerEnded))
            it.prepare(mediaSource)
        }
    }

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

    private fun traceRouteListByIndex(list: List<BasicVideoData>, index: Int, completion: () -> Unit) {
        if (index >= list.size) {
            serviceMode = ServiceMode.IDLE
            completion.invoke()
            return
        }

        serviceMode = ServiceMode.TRACING
        videosTraced++
        updateProgressNotification()

        val videoContent = VideoContent(list[index])
        traceRoute(videoContent.basicVideoData) { traceRouteResult ->
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

            traceRouteListByIndex(list, index + 1, completion)
        }
    }

    private var currentTracerouteResult: TempTraceRouteResult? = null
    private var currentTracedUrl: String? = null

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