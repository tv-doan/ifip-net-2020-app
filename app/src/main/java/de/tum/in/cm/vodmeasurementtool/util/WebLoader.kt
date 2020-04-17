package de.tum.`in`.cm.vodmeasurementtool.util

import android.os.CountDownTimer
import android.webkit.WebView
import android.webkit.WebViewClient
import de.tum.`in`.cm.vodmeasurementtool.MediaPlayerService
import de.tum.`in`.cm.vodmeasurementtool.model.Platform

class WebLoader(private val webUrl: String,
                private val platform: Platform,
                private val playerService: MediaPlayerService,
                private val completion: (Long) -> Unit) {

    private var startLoadingTimeStamp: Long = System.currentTimeMillis()
    private var timeToLoadWebUrlPage: Long = 0L

    private inner class WebLoaderCountDownTimer(default: Long? = null)
        : CountDownTimer(default ?: if (platform == Platform.YOUTUBE) 7000L else 10000L, 1000) {

        init {
            startLoadingTimeStamp = System.currentTimeMillis()
        }

        override fun onFinish() {
            if (timeToLoadWebUrlPage <= 0L) {
                timeToLoadWebUrlPage = -1L
            }
            L.d { "WebLoader: timeToLoadWebUrlPage = $timeToLoadWebUrlPage" }
            completion.invoke(timeToLoadWebUrlPage)
        }

        override fun onTick(millisUntilFinished: Long) {
        }
    }

    fun loadWebUrlPage() {
        playerService.runOnServiceThread(Runnable {
            val webView = playerService.provideHiddenWebView()

            if (PreferencesUtil(playerService).skipLoadingWebPages) {
                //without this the dtubeUtil does not seem to work
                webView?.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        L.d { "WebLoader: mock page finished" }
                        super.onPageFinished(view, url)
                    }
                }
                WebLoaderCountDownTimer(3000).start()
                webView?.loadUrl("about:blank")
            } else {
                webView?.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        L.d { "WebLoader: onPageFinished(), time = ${System.currentTimeMillis() - startLoadingTimeStamp}" }
                        timeToLoadWebUrlPage = System.currentTimeMillis() - startLoadingTimeStamp
                        super.onPageFinished(view, url)
                    }
                }

                WebLoaderCountDownTimer().start()
                webView?.loadUrl(webUrl)
            }
        })
    }
}