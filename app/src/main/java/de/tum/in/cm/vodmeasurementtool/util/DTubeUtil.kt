package de.tum.`in`.cm.vodmeasurementtool.util

import android.os.CountDownTimer
import android.webkit.*
import de.tum.`in`.cm.vodmeasurementtool.MediaPlayerService
import de.tum.`in`.cm.vodmeasurementtool.model.Platform
import org.jsoup.Jsoup

class DTubeUtil(playerService: MediaPlayerService) : VideoPlatformUtil(playerService) {
    private lateinit var completion: () -> Unit

    private var trendingListHtml: String? = null
    private var srcUrlHtml: String? = null


    private val TRENDING = 0    // flag for `CustomCountDownTimer`, to mark that the timer is to limit the `get trending
                                // videos` action
    private val SRC_URL = 1     // flag for `CustomCountDownTimer`, to mark that the timer is to limit the `get direct
                                // video url` action

    /**
     * Timer to limit actions executed for DTube platform, i.e. fetching list of trending videos and fetching
     * corresponding direct urls. As DTube does not provide any API (at time of this app's implementation), we try to
     * acquire and parse needed data from the DTube web pages' source htmls. As we only intercept the web pages'
     * content, there is no such thing as server response, so we are unable to tell when the actions have succeeded,
     * in order to tell them to finish. This Timer class serves as a stopwatch, finishing the actions when the time
     * limit has been reached. We set the limit long enough so that in most cases the actions would have succeeded
     * until then.
     *
     * @param   period  the time limit to allow a certain action the run, in ms
     * @param   purpose either TRENDING or SRC_URL
     */
    private inner class CustomCountDownTimer(period: Long, private val purpose: Int) : CountDownTimer(period, 1000) {

        override fun onFinish() {
            if (purpose == TRENDING) {
                if (trendingListHtml != null) {
                    playerService.runOnServiceThread(Runnable {
                        playerService.getTempWebView()?.loadUrl(trendingListHtml)
                    })
                } else {
                    completion.invoke()
                }
            } else {
                if (srcUrlHtml != null) {
                    playerService.runOnServiceThread(Runnable {
                        L.d { "loadind srcUrlHtml after CustomCountDownTimer.finish()" }
                        playerService.getTempWebView()?.loadUrl(srcUrlHtml)
                    })
                } else {
                    L.d { "currentCompletion invoked by CustomCountDownTimer" }
                    if (currentFallbackSrcUrl_1 != null) {
                        L.d { "currentFallbackSrcUrl_1 available" }
                        trendingList.add(BasicVideoData(Platform.DTUBE, currentWebUrl, currentFallbackSrcUrl_1!!, currentTimeToLoadWebUrlPage))
                    } else if (currentFallbackSrcUrl_2 != null) {
                        L.d { "currentFallbackSrcUrl_2 available" }
                        trendingList.add(BasicVideoData(Platform.DTUBE, currentWebUrl, currentFallbackSrcUrl_2!!, currentTimeToLoadWebUrlPage))
                    }
                    currentCompletion.invoke()
                }
            }
        }

        override fun onTick(millisUntilFinished: Long) {
        }
    }

    private fun startTimer(period: Long, purpose: Int) {
        CustomCountDownTimer(period, purpose).start()
    }

    /**
     * Fetch videos (their web urls) on DTube's trending list. As DTube does not have any API (at time of this app's
     * implementation), we try to acquire the urls by loading DTube's trending web page
     * (https://d.tube/#!/trendingvideos) and parsing the videos' web urls from the page's source html.
     * Take in consideration that DTube's source code - as the platform was still under development at the time of this
     * app's implementation - may have been changed since then, so it is recommended to review the behavior of DTube's
     * source html in case the following app's logic appears to not be working, or check if DTube has come up with
     * some sort of API respectively.
     */
    override fun getTrendingList(completion: () -> Unit) {

        this.completion = completion

        val webView = playerService.provideHiddenWebView()
        webView?.addJavascriptInterface(JSTrendingInterface(), DTubeJSInterface.JSTrendingInterface.iName)
        webView?.webViewClient = object : WebViewClient() {

                    private var interfaceCalled = false

                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        val host = request?.url?.host

                        // intercept specific redirect calls after which the web urls of trending videos should have
                        // been included in the html source page
                        if (host != null && (host.contains("ipfs.io") || host.contains("snap1.d.tube")) && !interfaceCalled) {
                            L.d { "shouldInterceptRequest(): call to gateway for images" }
                            interfaceCalled = true
                            // call `JSTrendingInterface.getTrendingList()` to parse the web urls of trending videos
                            // from the html source page
                            trendingListHtml = "javascript:JSTrendingInterface.getTrendingList" + "('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');"
                        }

                        return super.shouldInterceptRequest(view, request)
                    }
                }

        playerService.updateNotification("Fetching dTube trending list...", true)

        startTimer(60000, TRENDING)
        // open the DTube's trending list web page after intercepting interface and client has been set
        webView?.loadUrl("https://d.tube/#!/trendingvideos")
    }

    override fun loadWebUrlPage(webUrl: String, completion: (timeToLoadPageMs: Long) -> Unit) {
        //todo check settings
        WebLoader(webUrl, Platform.DTUBE, playerService, completion).loadWebUrlPage()
    }

    private var currentWebUrl: String = ""
    private var currentTimeToLoadWebUrlPage: Long = -1L
    private lateinit var currentCompletion: () -> Unit
    private var currentFallbackSrcUrl_1: String? = null
    private var currentFallbackSrcUrl_2: String? = null

    override fun getSrcUrl(webUrl: String, timeToLoadWebUrlPage: Long, completion: () -> Unit) {
        L.d { "Getting dtube srcUrl" }
        srcUrlHtml = null
        currentFallbackSrcUrl_1 = null
        currentFallbackSrcUrl_2 = null
        currentWebUrl = webUrl
        currentTimeToLoadWebUrlPage = timeToLoadWebUrlPage
        currentCompletion = completion

        playerService.runOnServiceThread(Runnable {
            val webView = playerService.provideHiddenWebView()
            webView?.addJavascriptInterface(JSInterface(), DTubeJSInterface.JSInterface.iName)
            webView?.webViewClient = object : WebViewClient() {

                        private var interfaceCalled = false

                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val host = request?.url?.host ?: return super.shouldInterceptRequest(view, request)
                            L.d { "shouldInterceptRequest(): url = ${request.url}" }
                            if (host.contains("video.dtube.top")) {
                                currentFallbackSrcUrl_1 = request.url.toString()
                            } else if (host.contains("ipfs.io")) {
                                currentFallbackSrcUrl_2 = request.url.toString()
                            }

/*
 * Following was the original logic, which stopped working in middle of implementation. DTube might have changed their
 * pages' structure at that point. New observations revealed that the video's direct url can be intercepted as redirect
 * urls to `video.dtube.top` or `ipfs.io`, hence the solution above.
 *
                            val isGatewayRequest = host.contains("ipfs.io") || host.contains("video.dtube.top")
                            val isHEADRequest = host.contains("video.dtube.top") && request.method.contains("HEAD")
                            if (!isHEADRequest && isGatewayRequest && !interfaceCalled) {
                                interfaceCalled = true
                                srcUrlHtml = "javascript:JSInterface.analyseEmbedPlayerHtml('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');"
                            }
*/
                            return super.shouldInterceptRequest(view, request)
                        }
                    }

            // Construct link to the video's direct url that corresponds to the given `webUrl`. This was the observed
            // logic at the time of this app's implementation.
            val embUrl = "https://emb.d.tube/#!/${webUrl.substring(20)}"
            // open the DTube video's web page after intercepting interface and client has been set
            webView?.let {
                L.d { "WebView Loading $embUrl" }
                it.loadUrl(embUrl)
            }
        })
        startTimer(10000, SRC_URL)
    }

    /**
     * JavaScript interface to intercept web urls of DTube's trending videos from DTube's `trending` web page.
     */
    private inner class JSTrendingInterface {

        /**
         * Parse the web urls of trending videos from the html source of DTube's `trending` web page.
         * The parsing logic is based on the structure of the DTube's `trending` web page's html source observed at the
         * time of this app's implementation. Take in consideration that DTube was still under development at that time
         * and the page structure may have been changed since then, so it is recommended to review the behavior of
         * DTube's source html in case the following app's logic appears to not be working.
         *
         * @param   html    the String representation of the `trending` web page's html source
         */
        @JavascriptInterface
        @SuppressWarnings("unused")
        fun getTrendingList(html: String) {
            val doc = Jsoup.parse(html)
            val videoSnaps = doc.body().getElementsByClass("verticalvideosnaptitle")

            val tempWebUrlsList = mutableListOf<String>()
            for (i in 0 until videoSnaps.size) {
                val videoUrl = "https://d.tube/#!" + videoSnaps[i].children().first().attr("href")
                L.d { "dtVideoUrl = $videoUrl" }
                tempWebUrlsList.add(videoUrl)
            }

            // store the extracted videos' web urls in `webUrlsList`
            tempWebUrlsList.shuffled().subList(0, maxVideoCount).forEach {dtWebUrl ->
                webUrlsList.add(dtWebUrl)
            }

            // start fetching direct video urls that correspond to the extracted web urls
            playerService.updateNotification("Fetching dTube source urls...", true)
            getVideoUrlByIndex(0, completion)
        }
    }

    /**
     * JavaScript interface to intercept direct url corresponding to DTube's video's web url.
     */
    private inner class JSInterface {

        /**
         * Parse the corresponding direct video url from the DTube video's web page's html source.
         * The parsing logic is based on the structure of the DTube's video web page's html source observed at the
         * time of this app's implementation. Take in consideration that DTube was still under development at that time
         * and the page structure may have been changed since then, so it is recommended to review the behavior of
         * DTube's source html in case the following app's logic appears to not be working.
         *
         * @param   html    the String representation of the DTube video's web page's html source
         */
        @JavascriptInterface
        @SuppressWarnings("unused")
        fun analyseEmbedPlayerHtml(html: String) {
            L.d { "JSInterface.analyseEmbedPlayerHtml() called" }
            if (html.contains("vjs-tech")) {
                val doc = Jsoup.parse(html)
                val sourceUrl: String? = doc.body().getElementsByClass("vjs-tech").first().attr("src")
                sourceUrl?.let {
                    trendingList.add(BasicVideoData(Platform.DTUBE, currentWebUrl, it, currentTimeToLoadWebUrlPage))
                }
            } else {
                L.e { "embedPlayer: js not loaded in time" }
            }
            L.d { "currentCompletion invoked by JSInterface" }
            currentCompletion.invoke()
        }
    }
}

/**
 * Enum to match the defined JS interfaces, which purpose is to hold the identifying name of the corresponding interface
 *
 * @param   iName   identifying name of the corresponding JS interface
 */
enum class DTubeJSInterface(val iName: String) {
    /** Interface to intercept direct url corresponding to DTube's video's web url */
    JSInterface("JSInterface"),
    /** Interface to intercept web urls of DTube's trending videos */
    JSTrendingInterface("JSTrendingInterface")
}