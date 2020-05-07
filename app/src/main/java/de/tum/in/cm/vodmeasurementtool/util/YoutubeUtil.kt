package de.tum.`in`.cm.vodmeasurementtool.util

import android.util.SparseArray
import at.huber.youtubeExtractor.VideoMeta
import at.huber.youtubeExtractor.YouTubeExtractor
import at.huber.youtubeExtractor.YtFile
import de.tum.`in`.cm.vodmeasurementtool.MediaPlayerService
import de.tum.`in`.cm.vodmeasurementtool.model.Platform
import de.tum.`in`.cm.vodmeasurementtool.util.youtubeservice.YoutubeService
import de.tum.`in`.cm.vodmeasurementtool.util.youtubeservice.YtResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class YoutubeUtil(playerService: MediaPlayerService) : VideoPlatformUtil(playerService) {

    // Retrofit instance to access youtube's REST API
    private val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/youtube/v3/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    private val ytService: YoutubeService

    /**
     * List of preferred itags. Each youtube video is available in multiple resolutions, which are denoted by itags.
     * See https://gist.github.com/sidneys/7095afe4da4ae58694d128b1034e01e2 to match itags with resolutions.
     * keySet contains most common itags, with the most preferred being at index 0. If the video does not provide the
     * corresponding resolution, the next-in-order itag should apply.
     * Itag 35 was most preferred because of comparability, as it corresponds to the 480p resolution, which was most
     * expected from DTube videos. Reorder itags according to your own need if necessary.
     */
    private val keySet = listOf(35, 22, 18, 6, 5)

    init {
        // Instantiate the service that provides specific (youtube) API calls
        ytService = retrofit.create(YoutubeService::class.java)
    }

    override fun getTrendingList(completion: () -> Unit) {

        playerService.updateNotification("Fetching youtube trending list...", true)

        // Retrieve trending videos by calling the youtube's REST API
        ytService.listYtTrending().enqueue(object : Callback<YtResponse> {
            override fun onFailure(call: Call<YtResponse>?, t: Throwable?) {
                L.e { "getYoutubeTrendingList() failed" }
                completion.invoke()
            }

            override fun onResponse(call: Call<YtResponse>?, response: Response<YtResponse>?) {
                response?.body()?.let { body ->
                    body.items.shuffled().subList(0, maxVideoCount).forEach { item ->
                        // The ids of the retrieved objects denote url to the video on youtube web page.
                        // Store these urls in `webUrlsList`, so that we can later - for each of them - fetch the
                        // corresponding direct urls.
                        val srcUrl = "https://www.youtube.com/watch?v=${item.id}"
                        webUrlsList.add(srcUrl)
                    }

                    playerService.updateNotification("Fetching youtube source urls...", true)
                    // For each of the retrieved web urls, fetch the corresponding source (direct video) url
                    getVideoUrlByIndex(0, completion)
                }
            }
        })
    }

    override fun getSrcUrl(webUrl: String, timeToLoadWebUrlPage: Long, completion: () -> Unit) {

        // Use youtubeExtractor to fetch the direct video url that corresponds to the `webUrl` parameter.
        // See https://github.com/HaarigerHarald/android-youtubeExtractor for detailed logic.
        object : YouTubeExtractor(playerService) {
            override fun onExtractionComplete(ytFile: SparseArray<YtFile>?, meta: VideoMeta?) {

                var sourceUrl: String? = null

                ytFile?.let {
                    for (i in 0 until keySet.size) {
                        val itag = keySet[i]
                        if (it.get(itag) != null) {
                            sourceUrl = it.get(itag).url
                            break
                        }
                    }
                }
                sourceUrl?.let {
                    trendingList.add(BasicVideoData(Platform.YOUTUBE, webUrl, it, timeToLoadWebUrlPage))
                }
                completion.invoke()
            }
        }.extract(webUrl, false, false)
    }

    override fun loadWebUrlPage(webUrl: String, completion: (timeToLoadPageMs: Long) -> Unit) {
        WebLoader(webUrl, Platform.YOUTUBE, playerService, completion).loadWebUrlPage()
    }
}