package de.tum.`in`.cm.vodmeasurementtool.model

import de.tum.`in`.cm.vodmeasurementtool.util.BasicVideoData

class VideoContent(val basicVideoData: BasicVideoData) {
    val platform: Platform = if (basicVideoData.webUrl.contains("youtube")) Platform.YOUTUBE else Platform.DTUBE
}