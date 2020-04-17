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


    private val TRENDING = 0
    private val SRC_URL = 1

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

    override fun getTrendingList(completion: () -> Unit) {

        this.completion = completion

        val webView = playerService.provideHiddenWebView()
        webView?.addJavascriptInterface(JSTrendingInterface(), DTubeJSInterface.JSTrendingInterface.iName)
        webView?.webViewClient = object : WebViewClient() {

                    private var interfaceCalled = false

                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        val host = request?.url?.host

                        if (host != null && (host.contains("ipfs.io") || host.contains("snap1.d.tube")) && !interfaceCalled) {
                            L.d { "shouldInterceptRequest(): call to gateway for images" }
                            interfaceCalled = true
                            trendingListHtml = "javascript:JSTrendingInterface.getTrendingList" + "('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');"
                        }

                        return super.shouldInterceptRequest(view, request)
                    }
                }

        playerService.updateNotification("Fetching dTube trending list...", true)

        startTimer(60000, TRENDING)
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
            //time to cleanup
            //Thread.sleep(2000)
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

//                            val isGatewayRequest = host.contains("ipfs.io") || host.contains("video.dtube.top")
//                            if (isGatewayRequest) {
//                                currentFallbackSrcUrl = request.url.toString()
//                            }
//                            val isHEADRequest = host.contains("video.dtube.top") && request.method.contains("HEAD")
//
//                            L.d { "isHeadRequest = $isHEADRequest, isGatewayRequest = $isGatewayRequest, interfaceCalled = $interfaceCalled" }
//
//                            if (!isHEADRequest && isGatewayRequest && !interfaceCalled) {
//                                interfaceCalled = true
//                                srcUrlHtml = "javascript:JSInterface.analyseEmbedPlayerHtml('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>');"
//
//                                //return WebResourceResponse(null, null, null)
//                            }

                            return super.shouldInterceptRequest(view, request)
                        }
                    }

            val embUrl = "https://emb.d.tube/#!/${webUrl.substring(20)}"
            webView?.let {
                L.d { "WebView Loading $embUrl" }
                it.loadUrl(embUrl)
            }
        })
        startTimer(10000, SRC_URL)
    }

    private inner class JSTrendingInterface {
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
            tempWebUrlsList.shuffled().subList(0, maxVideoCount).forEach {dtWebUrl ->
                webUrlsList.add(dtWebUrl)
            }

            //after getting video urls from trending, start getting src urls of the videos
            playerService.updateNotification("Fetching dTube source urls...", true)
            getVideoUrlByIndex(0, completion)
        }
    }

    private inner class JSInterface {
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

enum class DTubeJSInterface(val iName: String) {
    JSInterface("JSInterface"), JSTrendingInterface("JSTrendingInterface")
}