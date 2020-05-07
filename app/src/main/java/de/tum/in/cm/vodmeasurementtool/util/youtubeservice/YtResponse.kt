package de.tum.`in`.cm.vodmeasurementtool.util.youtubeservice

/**
 * The response returned by youtube API after calling https://www.googleapis.com/youtube/v3/videos.
 * The returned response is in JSON format, detailed structure is described here:
 * https://developers.google.com/youtube/v3/docs/videos/list#response
 * The only relevant field for us is the `items` field, which contains information about the retrieved videos.
 */
class YtResponse(val nextPageToken: String, val items: List<YtItem>)

/**
 * A wrapper of youtube video's information, reduced to only relevant fields to this app. Detailed structure:
 * https://developers.google.com/youtube/v3/docs/videos#resource-representation
 *
 * @param   id              the video's id on youtube website. For example, if the id is 'abcd', then
 *                          https://www.youtube.com/watch?v=abcd will lead to the video on youtube web page
 * @param   contentDetails  the video's metadata
 */
class YtItem(val id: String, val contentDetails: YtContentDetails)

/**
 * A wrapper of youtube video's metadata, part of `YTItem`. Not used anywhere in the app, consider removing, or
 * expand in case you want to acquire the metadata.
 */
class YtContentDetails(val duration: String)