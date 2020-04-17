package de.tum.`in`.cm.vodmeasurementtool.util.youtubeservice

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface YoutubeService {
    @GET("videos")
    fun listYtTrending(@Query("part") part: String = "contentDetails",
                       @Query("chart") chart: String = "mostPopular",
                       @Query("regionCode") regionCode: String = "DE",
                       @Query("maxResults") resultCount: Int = 50,
                       @Query("pageToken") pageToken: String? = null,
                       @Query("key") key: String = "AIzaSyAxOgosXHkDQZyDcNXIgJq80KRuKMFk6YU")
            : Call<YtResponse>
}