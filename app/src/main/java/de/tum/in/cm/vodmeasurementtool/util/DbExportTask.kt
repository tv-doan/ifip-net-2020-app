package de.tum.`in`.cm.vodmeasurementtool.util

import android.content.Context
import android.os.AsyncTask
import android.os.Build
import android.os.Environment
import android.widget.Toast
import java.io.File
import com.jcraft.jsch.*
import de.tum.`in`.cm.vodmeasurementtool.MainActivity
import de.tum.`in`.cm.vodmeasurementtool.MediaPlayerService
import de.tum.`in`.cm.vodmeasurementtool.model.formattedDateTime
import kotlin.Exception

enum class FinishStatus {
    PENDING, FAILED, NOT_EXPORTED, NOT_UPLOADED, SUCCEEDED
}

class DbExportTask(private val context: Context, private val completion: () -> Unit)
    : AsyncTask<Unit, Unit, FinishStatus>() {

    // Edit following with the server address where you want to send and store collected data,
    // and your ssh credentials with which you have (read/write) access to the server.
    private val serverAddress = "my_server_adress"
    private val username = "my_username"
    private val password = "my_password"
    private val port = -1

    private val deviceName = "${Build.MANUFACTURER.toLowerCase()}_${Build.MODEL.toLowerCase()}"

    private fun exportDbToServer(): FinishStatus {

        val prefsUtil = PreferencesUtil(context)

        var result = FinishStatus.PENDING

        val lastDbVersionUpdate = prefsUtil.lastDbVersionUpdate
        //Update DB naming every week in order to limit possible corrupted file to overwrite intact DB
        if (System.currentTimeMillis() - lastDbVersionUpdate >= 604800000L) {
            prefsUtil.updateDbVersion()
        }

        var dbFile: File? = null

        val clonedFileName = "measurements_${deviceName.replace(' ', '_')}_${prefsUtil.appId}_${prefsUtil.dbVersion}.db"
        var clonedFile: File? = null
        var dbFilePath = ""

        try {
            dbFile = context.getDatabasePath("vod_measurements_database")
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

            if (dbFile.exists() && downloadDir.exists() && downloadDir.isDirectory) {
                clonedFile = File(downloadDir, clonedFileName)

                if (!clonedFile.exists()) {
                    clonedFile.createNewFile()
                }

                clonedFile.writeBytes(dbFile.readBytes())
            }

        } catch (e: java.lang.Exception) {
            L.e(e) { "Failed to export db" }
            result = FinishStatus.NOT_EXPORTED
        } finally {
            val jsch = JSch()
            var session: Session? = null

            try {
                session = jsch.getSession(username, serverAddress, port)
                session.setConfig("StrictHostKeyChecking", "no")
                session.setPassword(password)
                session.connect()

                val channel: Channel = session.openChannel("sftp")
                channel.connect()
                val sftpChannel: ChannelSftp = channel as ChannelSftp

                if (dbFile != null && dbFile.exists()) {
                    sftpChannel.put(dbFile.canonicalPath, "uploads/$clonedFileName")
                    dbFilePath = dbFile.canonicalPath
                }
                sftpChannel.exit()
            } catch (e1: Exception) {
                L.e(e1) { "Failed to upload db" }

                try {
                    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (downloadDir.exists() && downloadDir.isDirectory) {
                        val logFile = File(downloadDir, "vodme_db_export_log.txt")

                        if (!logFile.exists()) {
                            logFile.createNewFile()
                        }

                        var message = "${System.currentTimeMillis().formattedDateTime()}:\ndbFilePath: $dbFilePath\n${e1.localizedMessage}\n"
                        e1.stackTrace.forEach {elem ->
                            message += "$elem\n"
                        }

                        logFile.appendBytes(message.toByteArray() )
                    }
                } catch (e5: Exception) {
                    L.e(e5) {"Failed to write log file"}
                }

                if (result == FinishStatus.NOT_EXPORTED) {
                    result = FinishStatus.FAILED
                } else {
                    result = FinishStatus.NOT_UPLOADED
                }
            } finally {
                session?.disconnect()
                if (result == FinishStatus.PENDING) {
                    result = FinishStatus.SUCCEEDED
                }
            }
        }
        return result
    }

    override fun onPreExecute() {
        super.onPreExecute()
        try {

            if (context is MediaPlayerService) {
                context.updateNotification("Exporting DB...", true)
            }
        } catch (e: Exception) {
            L.e(e) { "DbExportTask.onPreExecute(): something went wrong" }
            this.cancel(true)
        }
    }

    override fun doInBackground(vararg params: Unit?): FinishStatus {
        return exportDbToServer()
    }

    override fun onPostExecute(result: FinishStatus) {
        super.onPostExecute(result)
        try {
            val message = when (result) {
                FinishStatus.PENDING ->
                    "Exporting pending, but should have finished"

                FinishStatus.FAILED ->
                    "Failed to export or upload DB"

                FinishStatus.NOT_EXPORTED ->
                    "DB uploaded but failed to export"

                FinishStatus.NOT_UPLOADED ->
                    "DB exported but failed to upload"

                FinishStatus.SUCCEEDED ->
                    "Successfully exported and uploaded DB"
            }
            if (context is MediaPlayerService) {
                context.updateNotification(message)
            }  else if (context is MainActivity) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            L.e(e) { "DbExportTask.onPostExecute(): something went wrong" }
        }
        completion.invoke()
    }
}