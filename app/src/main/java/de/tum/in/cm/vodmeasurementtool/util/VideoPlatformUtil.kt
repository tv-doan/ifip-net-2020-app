package de.tum.`in`.cm.vodmeasurementtool.util

import de.tum.`in`.cm.vodmeasurementtool.MediaPlayerService

/**
 * This abstract class denotes the common interface for each video service that the app will perform measurements on
 * (currently Youtube and DTube), each video service then implements the denoted interface using implementation that is
 * specific to that service (see {@link de.tum.`in`.cm.vodmeasurementtool.util.YoutubeUtil} and
 * {@link de.tum.`in`.cm.vodmeasurementtool.util.DTubeUtil}). To extend the app by another video service, extend and use
 * signatures of this class and implement the functions according to the specifics of that video service.
 *
 * @param   playerService   Reference to the foreground service that runs all playbacks and measurements
 */
abstract class VideoPlatformUtil(protected val playerService: MediaPlayerService) {
    /**
     * In-memory container of acquired videos' direct urls that we will play and measure.
     * As most video services have a Trending section. We assumed that videos from that section are the most frequented,
     * so it makes sense to use them for measurements, hence the specific naming.
     *
     * issue: consider renaming this property
     */
    val trendingList = mutableListOf<BasicVideoData>()

    /**
     * List of web urls (not direct video urls) of the (trending) videos the app will fetch for playback and measure.
     */
    protected val webUrlsList = mutableListOf<String>()

    /**
     * Maximum number of videos to fetch for playback and measure. This setting can be set in the app's settings page.
     */
    protected val maxVideoCount = playerService.prefsUtil.defaultVideoCountToMeasure

    /**
     * Implement this function to fetch web urls of the videos that the app will play and measure. The implementation
     * should ensure that the fetched web urls are then stored into `webUrlsList`.
     *
     * @param   completion  Additional action to be executed after the fetching finishes
     */
    abstract fun getTrendingList(completion: () -> Unit)

    /**
     * Implement this function to fetch the direct url of the video corresponding to the web url denoted by the param
     * `webUrl`. The implementation should ensure that the fetched direct url is wrapped in `BasicVideoData` and then
     * stored into `trendingList`.
     *
     * @param   webUrl                  The video's web url for which to get the direct video url for
     * @param   timeToLoadWebUrlPage    Time that was needed to load the `webUrl` web page. This property is required
     *                                  to instantiate `BasicVideoData` instance
     * @param   completion              Additional action to be executed after the fetching finishes
     */
    abstract fun getSrcUrl(webUrl: String, timeToLoadWebUrlPage: Long, completion: () -> Unit)

    /**
     * Implement this function to manually load the video's web page denoted by the `webUrl` link, and by doing that
     * acquiring the time needed to load the web page (in ms).
     *
     * @param   webUrl                  The video's web url
     * @param   completion              Additional action to be executed after finishing loading the page. Pass forward
     *                                  the measured load-time to be processed.
     */
    abstract fun loadWebUrlPage(webUrl: String, completion: (timeToLoadPageMs: Long) -> Unit)

    /**
     * Get direct urls for each video (web url) stored in `webUrlList`. Start with calling
     * `getVideoUrlByIndex(0) { ... }`
     *
     * @param   index       Index of the video web url stored in `webUrlList` for which to fetch the direct url for
     * @param   completion  Additional action to be executed after the fetching finishes
     */
    protected fun getVideoUrlByIndex(index: Int, completion: () -> Unit) {
        if (index >= webUrlsList.size) {
            return completion.invoke()
        }

        val message = if (this is YoutubeUtil) {
            "Fetching youtube src: ${index + 1}/${webUrlsList.size}"
        } else {
            "Fetching dtube src: ${index + 1}/${webUrlsList.size}"
        }

        playerService.updateNotification(message, true)

        // First, load the web page in order to acquired loading time
        loadWebUrlPage(webUrlsList[index]) { timeToLoadPageMs ->
            // Then, get the direct url and store it into `trendingList` by calling `getSrcUrl(...)`
            getSrcUrl(webUrlsList[index], timeToLoadPageMs) {
                // Repeat the logic for the next video web url stored in `webUrlList`
                getVideoUrlByIndex(index + 1, completion)
            }
        }
    }
}