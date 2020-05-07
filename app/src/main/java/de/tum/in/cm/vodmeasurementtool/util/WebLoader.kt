package de.tum.`in`.cm.vodmeasurementtool.util

import android.os.CountDownTimer
import android.webkit.WebView
import android.webkit.WebViewClient
import de.tum.`in`.cm.vodmeasurementtool.MediaPlayerService
import de.tum.`in`.cm.vodmeasurementtool.model.Platform

/**
 * Helper class to load a video's web page, in order to collect the page's loading time.
 *
 * @param   webUrl          the video's web url, for which this class will load its content via a webView
 * @param   platform        the platform from which the video is from
 * @param   playerService   reference to the foreground service that runs all measurements
 * @param   completion      additional action to be performed once the webView finishes loading the webUrl's content,
 *                          with the loading time passed in for further processing
 */
class WebLoader(private val webUrl: String,
                private val platform: Platform,
                private val playerService: MediaPlayerService,
                private val completion: (Long) -> Unit) {

    private var startLoadingTimeStamp: Long = System.currentTimeMillis()
    private var timeToLoadWebUrlPage: Long = 0L

    /**
     * As there are no callbacks from webView calls, we are unable to react precisely at the point it finishes loading
     * the passed-in webUrl. We use this class to set a time limit to the loading, and once the limit is reach, we
     * retrieve the measured loading time. The time limits are chosen so that in most cases, they should be enough for
     * the webView to finish loading the web page, in the opposite case there were probably some connection issues,
     * for which we simply return an invalid loading time (-1).
     */
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
            // get instance of the webView which will load the webUrl's content
            val webView = playerService.provideHiddenWebView()

            if (PreferencesUtil(playerService).skipLoadingWebPages) {
                // if the loading webUrls setting is set to be skipped by user, just load a dummy blank page
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

                    // intercept the event when the webView finishes loading the web page
                    override fun onPageFinished(view: WebView?, url: String?) {
                        L.d { "WebLoader: onPageFinished(), time = ${System.currentTimeMillis() - startLoadingTimeStamp}" }
                        timeToLoadWebUrlPage = System.currentTimeMillis() - startLoadingTimeStamp
                        super.onPageFinished(view, url)
                    }
                }

                // start the timer right before starting loading the webUrl
                WebLoaderCountDownTimer().start()
                webView?.loadUrl(webUrl)
            }
        })
    }
}