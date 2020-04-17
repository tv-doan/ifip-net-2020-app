package de.tum.`in`.cm.vodmeasurementtool.util

import de.tum.`in`.cm.vodmeasurementtool.model.Platform

class BasicVideoData(val platform: Platform, val webUrl: String, val srcUrl: String, val timeToLoadWebPageMs: Long) {

    override fun toString(): String {
        return "webUrl: $webUrl, srcUrl: $srcUrl, timeToLoadWebPageMs = $timeToLoadWebPageMs"
    }
}