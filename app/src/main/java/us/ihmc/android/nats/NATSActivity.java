package us.ihmc.android.nats;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;

/**
 * NATSActivity.java
 * <p>
 * Creates and handles interactions of UI elements.
 * A click on each of the following buttons will enable the specific setting associated
 * with the button. The setting will override the NATS default setting.
 * <p>
 * HOST                  Bind to HOST address (default: 0.0.0.0)
 * PORT                  Use PORT for clients (default: 4222)
 * LOG                   File to redirect log output
 * CONFIG                Configuration File
 *
 * @author Enrico Casini (ecasini@ihmc.us)
 */
public class NATSActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //copy NATS executable from assets to local directory
        copyNATSExecutable();

        setContentView(R.layout.activity_main);
        //set default status for UI elements
        setDefaultUI();

        //disable extra settings by default
        final ToggleButton btnHost = (ToggleButton) findViewById(R.id.btnHost);
        btnHost.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                modifyHost(btnHost.isChecked());
            }
        });

        final ToggleButton btnPort = (ToggleButton) findViewById(R.id.btnPort);
        btnPort.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                modifyPort(btnPort.isChecked());
            }
        });

        final ToggleButton btnLog = (ToggleButton) findViewById(R.id.btnLog);
        btnLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ActivityCompat.requestPermissions(NATSActivity.this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        1);
                modifyLog(btnLog.isChecked());
            }
        });


        final ToggleButton btnConfig = (ToggleButton) findViewById(R.id.btnConfig);
        btnConfig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ActivityCompat.requestPermissions(NATSActivity.this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        2);
                modifyConfig(btnConfig.isChecked());
                Toast.makeText(getBaseContext(),
                        getString(R.string.toast_config),
                        Toast.LENGTH_SHORT).show();
            }
        });


        //get EditText elements to retrieve values
        final EditText etHost = (EditText) findViewById(R.id.etHost);
        final EditText etPort = (EditText) findViewById(R.id.etPort);
        final EditText etLog = (EditText) findViewById(R.id.etLog);
        final EditText etConfig = (EditText) findViewById(R.id.etConfig);

        final ToggleButton btnStartStop = (ToggleButton) findViewById(R.id.btnStartStop);
        btnStartStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (btnStartStop.isChecked()) {

                    String host = btnHost.isChecked() ? etHost.getText().toString() : null;
                    if (!Utils.isValidIPv4(host)) {
                        //if IP not valid, don't use as argument
                        host = null;
                    }

                    String port = btnPort.isChecked() ? etPort.getText().toString() : null;
                    if (!Utils.isValidPort(port)) {
                        //if port not valid, don't use as argument
                        port = null;
                    }
                    String log = btnLog.isChecked() ? etLog.getText().toString() : null;
                    if (log != null) {
                        log = Utils.getExternalStorageDirectory()
                                + File.separator + log;
                    }

                    String config = btnConfig.isChecked() ? etConfig.getText().toString() : null;

                    //check if provided config file exists
                    if (config != null) {
                        File fileConfig = new File(Utils.getExternalStorageDirectory()
                                + File.separator + config);
                        if (!fileConfig.exists()) {
                            //if file doesn't exist, don't use as option
                            Toast.makeText(NATSActivity.this,
                                    getString(R.string.toast_config_not_found), Toast.LENGTH_SHORT).show();
                            btnStartStop.setChecked(false);
                            return;

                        }

                        config = Utils.getExternalStorageDirectory()
                                + File.separator + config;
                    }

                    new RunNATSTask().execute(host, port, log, config);
                    modifyUI(true);
                    Toast.makeText(getBaseContext(),
                            getString(R.string.toast_start),
                            Toast.LENGTH_LONG).show();
                } else {

                    //should be checked because running, then decide what to do after alert dialog
                    btnStartStop.setChecked(true);
                    //ask user if he's sure to stop NATS Server
                    new AlertDialog.Builder(NATSActivity.this)
                            .setTitle(getString(R.string.alert_stop_title))
                            .setMessage(getString(R.string.alert_stop_message))
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    // continue with stop
                                    if (killNATS()) {
                                        setDefaultUI();
                                        Toast.makeText(getBaseContext(),
                                                getString(R.string.toast_stop),
                                                Toast.LENGTH_LONG).show();

                                    }
                                }
                            })
                            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    // do nothing
                                }
                            })
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            //log case
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay!
                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(NATSActivity.this,
                            getString(R.string.toast_permission_denied), Toast.LENGTH_SHORT).show();
                    //show evidence to the user that log option was disabled
                    ToggleButton btnLog = (ToggleButton) findViewById(R.id.btnLog);
                    btnLog.setChecked(false);
                }
                return;
            }
            //config case
            case 2: {

                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                } else {
                    Toast.makeText(NATSActivity.this,
                            getString(R.string.toast_permission_denied), Toast.LENGTH_SHORT).show();
                    //show evidence to the user that config option was disabled
                    ToggleButton btnConfig = (ToggleButton) findViewById(R.id.btnConfig);
                    btnConfig.setChecked(false);
                }
                return;
            }
        }
    }

    private void copyNATSExecutable() {
        File NATSExecutable = new File("/data/data/" +
                getString(R.string.app_package) + File.separator +
                getString(R.string.nats_release) + File.separator +
                getString(R.string.nats_executable));
        if (!NATSExecutable.exists()) {
            Log.d(TAG, "Copying asset: " + getNATSAssetsExecutablePath()
                    + " to " + getNATSLocalDirPath());
            copyFile(getNATSAssetsExecutablePath(), getNATSLocalDirPath(), getBaseContext());
        }
    }

    private String getNATSAssetsExecutablePath() {
        return getString(R.string.nats_release) + File.separator +
                getString(R.string.nats_executable);
    }

    private String getNATSLocalDirPath() {
        return "/data/data/" +
                getString(R.string.app_package) + File.separator +
                getString(R.string.nats_release); //same directory structure
    }

    private String getNATSLocalExecutable() {
        return getNATSLocalDirPath() + File.separator + getString(R.string.nats_executable);
    }

    private void modifyHost(boolean isChecked) {
        ToggleButton tb = (ToggleButton) findViewById(R.id.btnHost);
        tb.setChecked(isChecked);
        findViewById(R.id.etHost).setEnabled(isChecked);
    }

    private void modifyPort(boolean isChecked) {
        ToggleButton tb = (ToggleButton) findViewById(R.id.btnPort);
        tb.setChecked(isChecked);
        findViewById(R.id.etPort).setEnabled(isChecked);
    }

    private void modifyLog(boolean isChecked) {
        ToggleButton tb = (ToggleButton) findViewById(R.id.btnLog);
        tb.setChecked(isChecked);
        findViewById(R.id.etLog).setEnabled(isChecked);
    }

    private void modifyConfig(boolean isChecked) {
        ToggleButton tb = (ToggleButton) findViewById(R.id.btnConfig);
        tb.setChecked(isChecked);
        findViewById(R.id.etConfig).setEnabled(isChecked);

        //disable others
        modifyHost(false);
        modifyPort(false);
        modifyLog(false);
        findViewById(R.id.btnHost).setEnabled(!isChecked);
        findViewById(R.id.btnPort).setEnabled(!isChecked);
        findViewById(R.id.btnLog).setEnabled(!isChecked);
    }

    private void setDefaultUI() {

        ToggleButton btnStartStop = (ToggleButton) findViewById(R.id.btnStartStop);
        btnStartStop.setEnabled(true);
        btnStartStop.setChecked(false);
        findViewById(R.id.pbRunning).setVisibility(View.INVISIBLE);
        ToggleButton btnHost = (ToggleButton) findViewById(R.id.btnHost);
        btnHost.setEnabled(true);
        btnHost.setChecked(false);
        ToggleButton btnPort = (ToggleButton) findViewById(R.id.btnPort);
        btnPort.setEnabled(true);
        btnPort.setChecked(false);
        ToggleButton btnLog = (ToggleButton) findViewById(R.id.btnLog);
        btnLog.setEnabled(true);
        btnLog.setChecked(false);
        ToggleButton btnConfig = (ToggleButton) findViewById(R.id.btnConfig);
        btnConfig.setEnabled(true);
        btnConfig.setChecked(false);
        findViewById(R.id.etHost).setEnabled(false);
        findViewById(R.id.etPort).setEnabled(false);
        findViewById(R.id.etLog).setEnabled(false);
        findViewById(R.id.etConfig).setEnabled(false);
    }

    private void modifyUI(boolean isNATSRunning) {

        findViewById(R.id.pbRunning).setVisibility(isNATSRunning ? View.VISIBLE : View.INVISIBLE);

        //Button elements
        findViewById(R.id.btnHost).setEnabled(!isNATSRunning);
        findViewById(R.id.btnPort).setEnabled(!isNATSRunning);
        findViewById(R.id.btnLog).setEnabled(!isNATSRunning);
        findViewById(R.id.btnConfig).setEnabled(!isNATSRunning);

        //EditText elements
        findViewById(R.id.etHost).setEnabled(!isNATSRunning);
        findViewById(R.id.etPort).setEnabled(!isNATSRunning);
        findViewById(R.id.etLog).setEnabled(!isNATSRunning);
        findViewById(R.id.etConfig).setEnabled(!isNATSRunning);
    }

    private class RunNATSTask extends AsyncTask<String, Void, Boolean> {
        protected Boolean doInBackground(String... params) {
            return runNATS(params[0], params[1], params[2], params[3]);
        }

        protected void onPostExecute(Boolean result) {

            if (result != null && !result) {
                //disable running notification if false
                modifyUI(false);
            }
        }
    }

    private boolean killNATS() {
        try {
            String kill = "kill -9 " + _natsProcessPID;
            Log.d(TAG, "Executing: " + kill);
            Runtime.getRuntime().exec(kill);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Unable to kill NATS: ", e);
            return false;
        }
    }

    private boolean runNATS(String host, String port, String log, String config) {
        try {

            StringBuilder args = new StringBuilder();
            if (host != null) {
                args.append(" -a ").append(host);
            }
            if (port != null) {
                args.append(" -p ").append(port);
            }
            if (log != null) {
                args.append(" -l ").append(log);
            }
            if (config != null) {
                args.append(" -c ").append(config);
            }

            Log.d(TAG, "Executing: " + getNATSLocalExecutable() + args.toString());
            Process natsProcess = Runtime.getRuntime().exec(
                    getNATSLocalExecutable() + args.toString());

            //use reflection to extract PID
            Field f = natsProcess.getClass().getDeclaredField("pid");
            f.setAccessible(true);
            _natsProcessPID = (Integer) f.get(natsProcess);
            Log.d(TAG, "NATS PID is: " + _natsProcessPID);

            BufferedReader reader = new BufferedReader(new InputStreamReader(natsProcess.getInputStream()));
            int read;
            char[] buffer = new char[4096];
            StringBuffer output = new StringBuffer();
            while ((read = reader.read(buffer)) > 0) {
                output.append(buffer, 0, read);
            }
            reader.close();

            // Waits for the command to finish.
            natsProcess.waitFor();

            //String nativeOutput = output.toString();
            //Log.d(TAG, "Output: " + nativeOutput);

        } catch (InterruptedException | IOException e) {
            Log.e(TAG, "Unable to run NATS: ", e);
            return false;
        } catch (NoSuchFieldException e) {
            Log.e(TAG, "Unable to extract field ", e);
            return false;
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Unable to access field ", e);
        }

        return true;
    }

    private static void copyFile(String assetPath, String localDirPath, Context context) {
        try {
            InputStream in = context.getAssets().open(assetPath);

            File localDir = new File(localDirPath);
            if (!localDir.exists()) {
                boolean success = localDir.mkdir();
                if (!success) {
                    throw new RuntimeException("Unable to create directory: " + localDirPath);
                }
            }

            File assetsFile = new File(assetPath);

            String outFilePath = localDirPath + File.separator + assetsFile.getName();
            FileOutputStream out = new FileOutputStream(outFilePath);
            Log.d(TAG, "Copying file to: " + outFilePath);
            int read;
            byte[] buffer = new byte[4096];
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            out.close();
            in.close();

            //set executable
            File bin = new File(localDirPath + File.separator + assetsFile.getName());
            bin.setExecutable(true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static int _natsProcessPID;
    private final static String TAG = "NATSActivity";
}
