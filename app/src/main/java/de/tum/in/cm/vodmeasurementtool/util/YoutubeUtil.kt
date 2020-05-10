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
    private val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/youtube/v3/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    private val ytService: YoutubeService
    private val keySet = listOf(35, 22, 18, 6, 5)

    init {
        ytService = retrofit.create(YoutubeService::class.java)
    }

    override fun getTrendingList(completion: () -> Unit) {

        playerService.updateNotification("Fetching youtube trending list...", true)

        ytService.listYtTrending().enqueue(object : Callback<YtResponse> {
            override fun onFailure(call: Call<YtResponse>?, t: Throwable?) {
                L.e { "getYoutubeTrendingList() failed" }
                completion.invoke()
            }

            override fun onResponse(call: Call<YtResponse>?, response: Response<YtResponse>?) {
                response?.body()?.let { body ->
                    body.items.shuffled().subList(0, maxVideoCount).forEach { item ->
                        val srcUrl = "https://www.youtube.com/watch?v=${item.id}"
                        webUrlsList.add(srcUrl)
                    }

                    playerService.updateNotification("Fetching youtube source urls...", true)
                    getVideoUrlByIndex(0, completion)
                }
            }
        })
    }

    override fun getSrcUrl(webUrl: String, timeToLoadWebUrlPage: Long, completion: () -> Unit) {
        object : YouTubeExtractor(playerService) {
            override fun onExtractionComplete(ytFile: SparseArray<YtFile>?, meta: VideoMeta?) {

                var sourceUrl: String? = null

                ytFile?.let {
                    for (i in 0 until keySet.size) {
                        val itag = keySet[i]
                        if (it.get(itag) != null) {
                            sourceUrl = it.get(itag).url
                            //L.d { "srcUrl = ${it.get(itag).url}" }
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