package de.tum.`in`.cm.vodmeasurementtool

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import de.tum.`in`.cm.vodmeasurementtool.model.formattedDateTime
import de.tum.`in`.cm.vodmeasurementtool.util.DbExportTask
import de.tum.`in`.cm.vodmeasurementtool.util.PreferencesUtil
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    /*
     * For the playbacks to stay active even if the application is in background, all playbacks should be managed and
     * run on a foreground service. A foreground service continues running even when the user isn't interacting
     * with the app. MainActivity keeps reference to this service to infer the current state of playbacks, in case
     * the user reopens the app.
     * */
    private var foregroundService: MediaPlayerService? = null

    /*
    * An interface, through which this activity can access the reference of the currently running foreground service.
    * Note that the textView `textView_app_status`, which denotes the current state of the foreground service, has its
    * text set manually. During debugging, it was discovered that sometimes, the text does not match the real state of
    * the foreground service. Clearly, we missed some edge cases where we did not update the status. Future work should
    * take this in consideration, and use LiveData to hold status text, instead of manually assigning texts.
    * */
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            foregroundService?.mainActivity = null
            foregroundService = null
            this@MainActivity.runOnUiThread {
                textView_app_status.text = "Background service disconnected"
                button_start_measurements.isEnabled = true
                button_start_measurements.visibility = View.VISIBLE
            }
        }

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            foregroundService = (binder as? MediaPlayerService.MediaPlayerServiceBinder)?.service
            foregroundService?.let {
                if (it.isServiceRunning) {
                    this@MainActivity.runOnUiThread {
                        textView_app_status.text = "Media Player Service running"
                        button_start_measurements.isEnabled = false
                        button_start_measurements.visibility = View.INVISIBLE
                        playerView.player = it.exoPlayer
                    }
                    it.mainActivity = this@MainActivity
                } else {
                    this@MainActivity.runOnUiThread {
                        textView_app_status.text = "Background service not running at the moment"
                        button_start_measurements.isEnabled = true
                        button_start_measurements.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        button_start_measurements.setOnClickListener {

            textView_app_status.text = "Launching Media Player Service ..."
            // After clicking 'start measurements', we disable the button, in order to avoid executing another set of
            // playbacks / measurements. The button is enabled again after all measurements have finished.
            button_start_measurements.isEnabled = false
            button_start_measurements.visibility = View.INVISIBLE

            val prefsUtil = PreferencesUtil(this)
            if (prefsUtil.lastServiceFinishTime != -1L
                    && System.currentTimeMillis() - prefsUtil.lastServiceFinishTime <= 60000L) {
                // Mandatory waiting for at least 1 minute after the last measurements session finished. This interval
                // is just to ensure that old resources are cleared.
                Toast.makeText(this, "Service cooling down, please wait 1 minute", Toast.LENGTH_SHORT).show()
            } else {
                // We attempt to start the foreground service, where we will run the playbacks / measurements.
                val intent = Intent(this, MediaPlayerService::class.java)
                runOnUiThread {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                }
                bindService(intent, serviceConnection, Context.BIND_IMPORTANT)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.main_menu_2, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.open_settings -> {
                val settingsIntent = Intent(this, SettingsActivity::class.java)
                startActivity(settingsIntent)
                true
            }

            R.id.export_db_2 -> {
                DbExportTask(this) {}.execute()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        // When the app comes back to foreground (user opens app), attempt to connect to an existing foreground service.
        val intent = Intent(this, MediaPlayerService::class.java)
        bindService(intent, serviceConnection, Context.BIND_IMPORTANT)

        if (foregroundService == null) {
            // If no foreground service is found, we enable 'start measurements'
            button_start_measurements.isEnabled = true
            button_start_measurements.visibility = View.VISIBLE
        }
        // If the foreground service exists, this activity (should) show its current state,
        // see serviceConnection.onServiceConnected()

        val prefsUtil = PreferencesUtil(this)
        if (prefsUtil.isSchedulingEnabled) {
            textView_next_scheduled_measurement.text =
                    "Next scheduled: ${PreferencesUtil(this).nextScheduledMeasurement.formattedDateTime()}"
        } else {
            textView_next_scheduled_measurement.text = "Next scheduled: disabled"
        }
    }

    override fun onPause() {
        super.onPause()
        // If the app is sent to background, disconnect from current existing foreground service, to release its
        // reference.
        unbindService(serviceConnection)
    }

}
