package de.tum.`in`.cm.vodmeasurementtool.util

import android.util.Log

object L {
    private fun getCallerClassName() : String {
        val stackTrace = Thread.currentThread().stackTrace
        for (i in 1 until stackTrace.size) {
            val ste = stackTrace[i]
            if (ste.className != L.javaClass.name && ste.className.indexOf("java.lang.Thread")!=0) {
                return ste.fileName
            }
        }
        return "Unknown caller"
    }

    fun d(includeTimeStamp: Boolean = false, tag: String = getCallerClassName(), message: () -> String) {
        var m = ""
        if (includeTimeStamp) {
            m += "${System.currentTimeMillis()}:\n"
        }
        m += "${message.invoke()}\n"
        Log.d(tag, m)
    }

    fun e(e: Exception? = null, includeTimeStamp: Boolean = false, tag: String = getCallerClassName(), message: () -> String) {
        var m = ""
        if (includeTimeStamp) {
            m += "${System.currentTimeMillis()}:\n"
        }
        e?.let {
            m += "${it.localizedMessage} \n"
        }
        m += "${message.invoke()}\n"
        Log.d(tag, m)
    }
}