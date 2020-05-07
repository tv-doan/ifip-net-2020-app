package de.tum.`in`.cm.vodmeasurementtool.util.youtubeservice

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface YoutubeService {

    /**
     * API call to https://www.googleapis.com/youtube/v3/ in order to get list of videos that match the request
     * parameters.
     *
     * @see <a href="https://developers.google.com/youtube/v3/docs/videos/list">https://developers.google.com/youtube/v3/docs/videos/list</a>
     *
     * @param   part        required parameter, should be `contentDetails` to get videos' metadata, avoid changing
     * @param   chart       the chart to retrieve. We use `mostPopular` to get videos from the trending chart
     * @param   regionCode  content of the chart can differ depending on selected country. Currently, Germany is
     *                      hard-coded for this parameter. Consider providing this parameter dynamically, depending
     *                      on user's current location.
     * @param   resultCount max. number of videos to retrieve
     * @param   pageToken   optional, used for paging, see the API's documentation for more details
     * @param   key         the google project's api key that this app is linked to, in order to use youtube api.
     *                      For personal use, consider replacing this key with your own created key. Follow
     *                      https://developers.google.com/youtube/v3/getting-started .
     *@return               a `YTResponse`, a class that mirrors the API's JSON response to this API call
     */
    @GET("videos")
    fun listYtTrending(@Query("part") part: String = "contentDetails",
                       @Query("chart") chart: String = "mostPopular",
                       @Query("regionCode") regionCode: String = "DE",
                       @Query("maxResults") resultCount: Int = 50,
                       @Query("pageToken") pageToken: String? = null,
                       @Query("key") key: String = "AIzaSyAxOgosXHkDQZyDcNXIgJq80KRuKMFk6YU")
            : Call<YtResponse>
}