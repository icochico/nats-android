package us.ihmc.android.nats

import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import android.widget.ToggleButton

import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.reflect.Field

/**
 * NATSActivity.kt
 *
 *
 * Simple gNATSd server for Android, written in Kotlin.
 * Creates and handles interactions of UI elements.
 * A click on each of the following buttons will enable the specific setting associated
 * with the button. The setting will override the NATS default setting.
 *
 *
 * HOST                  Bind to HOST address (default: 0.0.0.0)
 * PORT                  Use PORT for clients (default: 4222)
 * LOG                   File to redirect log output
 * CONFIG                Configuration File
 *
 * @author Enrico Casini (ecasini@ihmc.us)
 */
class NATSActivity : AppCompatActivity() {

    private val natsAssetsExecutablePath: String
        get() = getString(R.string.nats_release) + File.separator +
                getString(R.string.nats_executable)

    private//same directory structure
    val natsLocalDirPath: String
        get() = "/data/data/" +
                getString(R.string.app_package) + File.separator +
                getString(R.string.nats_release)

    private val natsLocalExecutable: String
        get() = natsLocalDirPath + File.separator + getString(R.string.nats_executable)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //copy NATS executable from assets to local directory
        copyNATSExecutable()

        setContentView(R.layout.activity_main)
        //set default status for UI elements
        setDefaultUI()

        //disable extra settings by default
        val btnHost = findViewById<View>(R.id.btnHost) as ToggleButton
        btnHost.setOnClickListener { modifyHost(btnHost.isChecked) }

        val btnPort = findViewById<View>(R.id.btnPort) as ToggleButton
        btnPort.setOnClickListener { modifyPort(btnPort.isChecked) }

        val btnLog = findViewById<View>(R.id.btnLog) as ToggleButton
        btnLog.setOnClickListener {
            ActivityCompat.requestPermissions(this@NATSActivity,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    1)
            modifyLog(btnLog.isChecked)
        }


        val btnConfig = findViewById<View>(R.id.btnConfig) as ToggleButton
        btnConfig.setOnClickListener {
            ActivityCompat.requestPermissions(this@NATSActivity,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    2)
            modifyConfig(btnConfig.isChecked)
            Toast.makeText(baseContext,
                    getString(R.string.toast_config),
                    Toast.LENGTH_SHORT).show()
        }


        //get EditText elements to retrieve values
        val etHost = findViewById<View>(R.id.etHost) as EditText
        val etPort = findViewById<View>(R.id.etPort) as EditText
        val etLog = findViewById<View>(R.id.etLog) as EditText
        val etConfig = findViewById<View>(R.id.etConfig) as EditText

        val btnStartStop = findViewById<View>(R.id.btnStartStop) as ToggleButton
        btnStartStop.setOnClickListener(View.OnClickListener {
            if (btnStartStop.isChecked) {

                var host: String? = if (btnHost.isChecked) etHost.text.toString() else null
                if (!Utils.isValidIPv4(host)) {
                    //if IP not valid, don't use as argument
                    host = null
                }

                var port: String? = if (btnPort.isChecked) etPort.text.toString() else null
                if (!Utils.isValidPort(port)) {
                    //if port not valid, don't use as argument
                    port = null
                }
                var log: String? = if (btnLog.isChecked) etLog.text.toString() else null
                if (log != null) {
                    log = (Utils.externalStorageDirectory
                            + File.separator + log)
                }

                var config: String? = if (btnConfig.isChecked) etConfig.text.toString() else null

                //check if provided config file exists
                if (config != null) {
                    val fileConfig = File(Utils.externalStorageDirectory
                            + File.separator + config)
                    if (!fileConfig.exists()) {
                        //if file doesn't exist, don't use as option
                        Toast.makeText(this@NATSActivity,
                                getString(R.string.toast_config_not_found), Toast.LENGTH_SHORT).show()
                        btnStartStop.isChecked = false
                        return@OnClickListener

                    }

                    config = (Utils.externalStorageDirectory
                            + File.separator + config)
                }

                RunNATSTask().execute(host, port, log, config)
                modifyUI(true)
                Toast.makeText(baseContext,
                        getString(R.string.toast_start),
                        Toast.LENGTH_LONG).show()
            } else {

                //should be checked because running, then decide what to do after alert dialog
                btnStartStop.isChecked = true
                //ask user if he's sure to stop NATS Server
                AlertDialog.Builder(this@NATSActivity)
                        .setTitle(getString(R.string.alert_stop_title))
                        .setMessage(getString(R.string.alert_stop_message))
                        .setPositiveButton(android.R.string.yes) { _, _ ->
                            // continue with stop
                            if (killNATS()) {
                                setDefaultUI()
                                Toast.makeText(baseContext,
                                        getString(R.string.toast_stop),
                                        Toast.LENGTH_LONG).show()

                            }
                        }
                        .setNegativeButton(android.R.string.no) { _, _ ->
                            // do nothing
                        }
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show()
            }
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
        //log case
            1 -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay!
                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(this@NATSActivity,
                            getString(R.string.toast_permission_denied), Toast.LENGTH_SHORT).show()
                    //show evidence to the user that log option was disabled
                    val btnLog = findViewById<View>(R.id.btnLog) as ToggleButton
                    btnLog.isChecked = false
                }
                return
            }
        //config case
            2 -> {

                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                } else {
                    Toast.makeText(this@NATSActivity,
                            getString(R.string.toast_permission_denied), Toast.LENGTH_SHORT).show()
                    //show evidence to the user that config option was disabled
                    val btnConfig = findViewById<View>(R.id.btnConfig) as ToggleButton
                    btnConfig.isChecked = false
                }
                return
            }
        }
    }

    private fun copyNATSExecutable() {
        val NATSExecutable = File("/data/data/" +
                getString(R.string.app_package) + File.separator +
                getString(R.string.nats_release) + File.separator +
                getString(R.string.nats_executable))
        if (!NATSExecutable.exists()) {
            Log.d(TAG, "Copying asset: " + natsAssetsExecutablePath
                    + " to " + natsLocalDirPath)
            copyFile(natsAssetsExecutablePath, natsLocalDirPath, baseContext)
        }
    }

    private fun modifyHost(isChecked: Boolean) {
        val tb = findViewById<View>(R.id.btnHost) as ToggleButton
        tb.isChecked = isChecked
        findViewById<View>(R.id.etHost).isEnabled = isChecked
    }

    private fun modifyPort(isChecked: Boolean) {
        val tb = findViewById<View>(R.id.btnPort) as ToggleButton
        tb.isChecked = isChecked
        findViewById<View>(R.id.etPort).isEnabled = isChecked
    }

    private fun modifyLog(isChecked: Boolean) {
        val tb = findViewById<View>(R.id.btnLog) as ToggleButton
        tb.isChecked = isChecked
        findViewById<View>(R.id.etLog).isEnabled = isChecked
    }

    private fun modifyConfig(isChecked: Boolean) {
        val tb = findViewById<View>(R.id.btnConfig) as ToggleButton
        tb.isChecked = isChecked
        findViewById<View>(R.id.etConfig).isEnabled = isChecked

        //disable others
        modifyHost(false)
        modifyPort(false)
        modifyLog(false)
        findViewById<View>(R.id.btnHost).isEnabled = !isChecked
        findViewById<View>(R.id.btnPort).isEnabled = !isChecked
        findViewById<View>(R.id.btnLog).isEnabled = !isChecked
    }

    private fun setDefaultUI() {

        val btnStartStop = findViewById<View>(R.id.btnStartStop) as ToggleButton
        btnStartStop.isEnabled = true
        btnStartStop.isChecked = false
        findViewById<View>(R.id.pbRunning).visibility = View.INVISIBLE
        val btnHost = findViewById<View>(R.id.btnHost) as ToggleButton
        btnHost.isEnabled = true
        btnHost.isChecked = false
        val btnPort = findViewById<View>(R.id.btnPort) as ToggleButton
        btnPort.isEnabled = true
        btnPort.isChecked = false
        val btnLog = findViewById<View>(R.id.btnLog) as ToggleButton
        btnLog.isEnabled = true
        btnLog.isChecked = false
        val btnConfig = findViewById<View>(R.id.btnConfig) as ToggleButton
        btnConfig.isEnabled = true
        btnConfig.isChecked = false
        findViewById<View>(R.id.etHost).isEnabled = false
        findViewById<View>(R.id.etPort).isEnabled = false
        findViewById<View>(R.id.etLog).isEnabled = false
        findViewById<View>(R.id.etConfig).isEnabled = false
    }

    private fun modifyUI(isNATSRunning: Boolean) {

        findViewById<View>(R.id.pbRunning).visibility = if (isNATSRunning) View.VISIBLE else View.INVISIBLE

        //Button elements
        findViewById<View>(R.id.btnHost).isEnabled = !isNATSRunning
        findViewById<View>(R.id.btnPort).isEnabled = !isNATSRunning
        findViewById<View>(R.id.btnLog).isEnabled = !isNATSRunning
        findViewById<View>(R.id.btnConfig).isEnabled = !isNATSRunning

        //EditText elements
        findViewById<View>(R.id.etHost).isEnabled = !isNATSRunning
        findViewById<View>(R.id.etPort).isEnabled = !isNATSRunning
        findViewById<View>(R.id.etLog).isEnabled = !isNATSRunning
        findViewById<View>(R.id.etConfig).isEnabled = !isNATSRunning
    }

    private inner class RunNATSTask : AsyncTask<String, Void, Boolean>() {
        override fun doInBackground(vararg params: String): Boolean? =
                runNATS(params[0], params[1], params[2], params[3])

        override fun onPostExecute(result: Boolean?) {

            if (result != null && !result) {
                //disable running notification if false
                modifyUI(false)
            }
        }
    }

    private fun killNATS(): Boolean {
        return try {
            val kill = "kill -9 " + _natsProcessPID
            Log.d(TAG, "Executing: " + kill)
            Runtime.getRuntime().exec(kill)
            true
        } catch (e: IOException) {
            Log.e(TAG, "Unable to kill NATS: ", e)
            false
        }

    }

    private fun runNATS(host: String?, port: String?, log: String?, config: String?): Boolean {
        try {

            val args = StringBuilder()
            if (host != null) {
                args.append(" -a ").append(host)
            }
            if (port != null) {
                args.append(" -p ").append(port)
            }
            if (log != null) {
                args.append(" -l ").append(log)
            }
            if (config != null) {
                args.append(" -c ").append(config)
            }

            Log.d(TAG, "Executing: " + natsLocalExecutable + args.toString())
            val natsProcess = Runtime.getRuntime().exec(
                    natsLocalExecutable + args.toString())

            //use reflection to extract PID
            val f = natsProcess.javaClass.getDeclaredField("pid")
            f.isAccessible = true
            _natsProcessPID = f.getInt(natsProcess)
            Log.d(TAG, "NATS PID is: " + _natsProcessPID)

            val reader = BufferedReader(InputStreamReader(natsProcess.inputStream))
//            var read: Int
//            val buffer = CharArray(4096)
//            val output = StringBuffer()

            reader.use { it.readText() }

//            while ((read = reader.read(buffer)) > 0) {
//                output.append(buffer, 0, read)
//            }
//            reader.close()

            // Waits for the command to finish.
            natsProcess.waitFor()

            //String nativeOutput = output.toString();
            //Log.d(TAG, "Output: " + nativeOutput);

        } catch (e: InterruptedException) {
            Log.e(TAG, "Unable to run NATS: ", e)
            return false
        } catch (e: IOException) {
            Log.e(TAG, "Unable to run NATS: ", e)
            return false
        } catch (e: NoSuchFieldException) {
            Log.e(TAG, "Unable to extract field ", e)
            return false
        } catch (e: IllegalAccessException) {
            Log.e(TAG, "Unable to access field ", e)
        }

        return true
    }

    companion object {

//        private fun copyFile(assetPath: String, localDirPath: String, context: Context) {
//            try {
//                val inputStream = context.assets.open(assetPath)
//
//                val localDir = File(localDirPath)
//                if (!localDir.exists()) {
//                    val success = localDir.mkdir()
//                    if (!success) {
//                        throw RuntimeException("Unable to create directory: " + localDirPath)
//                    }
//                }
//
//                val assetsFile = File(assetPath)
//
//                val outFilePath = localDirPath + File.separator + assetsFile.name
//                val out = FileOutputStream(outFilePath)
//                Log.d(TAG, "Copying file to: " + outFilePath)
//                var read: Int
//                val buffer = ByteArray(4096)
//                while ((read = inputStream.read(buffer)) > 0) {
//                    out.write(buffer, 0, read)
//                }
//                out.close()
//                inputStream.close()
//
//                //set executable
//                val bin = File(localDirPath + File.separator + assetsFile.name)
//                bin.setExecutable(true)
//            } catch (e: IOException) {
//                throw RuntimeException(e)
//            }
//        }

        private fun copyFile(assetFilePath: String, localDirPath: String, context: Context) {
            val assetsFile = File(assetFilePath)

            val localDir = File(localDirPath)
            if (!localDir.exists() && !localDir.mkdir()) {
                throw RuntimeException("Unable to create directory: " + localDirPath)
            }

            val outputFilePath = localDirPath + File.separator + assetsFile.name

            val inputStream = context.assets.open(assetFilePath)
            val outputStream = FileOutputStream(outputFilePath)

            try {
                inputStream.copyTo(outputStream)
                //set executable
                val bin = File(outputFilePath)
                bin.setExecutable(true)

            } finally {
                inputStream.close()
                outputStream.close()
            }
        }

        private var _natsProcessPID: Int = 0
        private val TAG = "NATSActivity"
    }
}
