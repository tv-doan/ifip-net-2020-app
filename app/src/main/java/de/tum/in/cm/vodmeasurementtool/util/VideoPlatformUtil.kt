package de.tum.`in`.cm.vodmeasurementtool.util

import de.tum.`in`.cm.vodmeasurementtool.MediaPlayerService

abstract class VideoPlatformUtil(protected val playerService: MediaPlayerService) {
    val trendingList = mutableListOf<BasicVideoData>()
    protected val webUrlsList = mutableListOf<String>()
    protected val maxVideoCount = playerService.prefsUtil.defaultVideoCountToMeasure

    abstract fun getTrendingList(completion: () -> Unit)
    abstract fun getSrcUrl(webUrl: String, timeToLoadWebUrlPage: Long, completion: () -> Unit)
    abstract fun loadWebUrlPage(webUrl: String, completion: (timeToLoadPageMs: Long) -> Unit)

    protected fun getVideoUrlByIndex(index: Int, completion: () -> Unit) {
        if (index >= webUrlsList.size) {
            return completion.invoke()
        }

        val message = if (this is YoutubeUtil) {
            "Fetching youtube src: ${index+1}/${webUrlsList.size}"
        } else {
            "Fetching dtube src: ${index+1}/${webUrlsList.size}"
        }

        playerService.updateNotification(message, true)

        loadWebUrlPage(webUrlsList[index]) { timeToLoadPageMs ->
            getSrcUrl(webUrlsList[index], timeToLoadPageMs) {
                getVideoUrlByIndex(index + 1, completion)
            }
        }
    }
}