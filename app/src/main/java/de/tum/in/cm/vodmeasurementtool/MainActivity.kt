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
import com.google.android.exoplayer2.*
import de.tum.`in`.cm.vodmeasurementtool.model.formattedDateTime
import de.tum.`in`.cm.vodmeasurementtool.util.DbExportTask
import de.tum.`in`.cm.vodmeasurementtool.util.PreferencesUtil
import de.tum.`in`.cm.vodmeasurementtool.util.ServiceScheduler
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private var exoPlayer: SimpleExoPlayer? = null
    private var foregroundService: MediaPlayerService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            foregroundService?.mainActivity = null
            foregroundService = null
            this@MainActivity.runOnUiThread {
                textView_app_status.text = "Background service disconnected"
                button_start_measurements.isEnabled = true
                button_start_measurements.visibility = View.VISIBLE
                //progressBar_load_lists.visibility = View.INVISIBLE
                //button_stop_measurements.isEnabled = false
            }
        }

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            foregroundService = (binder as? MediaPlayerService.MediaPlayerServiceBinder)?.service
            foregroundService?.let {
                if (it.isServiceRunning) {
                    this@MainActivity.runOnUiThread {
                        //button_stop_measurements.isEnabled = true
                        textView_app_status.text = "Media Player Service running"
                        button_start_measurements.isEnabled = false
                        button_start_measurements.visibility = View.INVISIBLE
                        playerView.player = it.exoPlayer
                    }
                    it.mainActivity = this@MainActivity
                } else {
                    this@MainActivity.runOnUiThread {
                        textView_app_status.text = "Background service not running at the moment"
                        //progressBar_load_lists.visibility = View.INVISIBLE
                        button_start_measurements.isEnabled = true
                        button_start_measurements.visibility = View.VISIBLE
                        //button_stop_measurements.isEnabled = false
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        button_start_measurements.setOnClickListener {

            //progressBar_load_lists.visibility = View.VISIBLE
            textView_app_status.text = "Launching Media Player Service ..."
            button_start_measurements.isEnabled = false
            button_start_measurements.visibility = View.INVISIBLE

            val intent = Intent(this, MediaPlayerService::class.java)

            val prefsUtil = PreferencesUtil(this)
            if (prefsUtil.lastServiceFinishTime != -1L && System.currentTimeMillis() - prefsUtil.lastServiceFinishTime <= 60000L) {
                Toast.makeText(this, "Service cooling down, please wait 1 minute", Toast.LENGTH_SHORT).show()
            } else {
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

//        button_stop_measurements.setOnClickListener {
//            foregroundService?.delayedStopSelf(5000)
//        }
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
        val intent = Intent(this, MediaPlayerService::class.java)
        bindService(intent, serviceConnection, Context.BIND_IMPORTANT)

        if (foregroundService == null) {
            button_start_measurements.isEnabled = true
            button_start_measurements.visibility = View.VISIBLE
            //button_stop_measurements.isEnabled = false
        }

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
        unbindService(serviceConnection)
    }

    override fun onDestroy() {
        exoPlayer?.release()
        super.onDestroy()
    }
}
