package de.tum.`in`.cm.vodmeasurementtool.util.youtubeservice

class YtResponse(val nextPageToken: String, val items: List<YtItem>)
class YtItem(val id: String, val contentDetails: YtContentDetails)
class YtContentDetails(val duration: String)