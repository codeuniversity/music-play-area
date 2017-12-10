package com.projects.piero.websocketclient;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Build;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import java.io.PrintWriter;
import java.io.StringWriter;

import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;
import de.tavendo.autobahn.WebSocketHandler;


public class MainActivity extends Activity implements SensorEventListener {

    private SensorManager sm;
    private long lastSendTime;

    private Sensor mAccelerometer;
    private Sensor mMagnetometer;
    private float[] mOrientation = new float[3];
    private float[] mLastAccelerometer = new float[3];
    private float[] mLastMagnetometer = new float[3];
    private boolean mLastAccelerometerSet = false;
    private boolean mLastMagnetometerSet = false;
    private float[] mR = new float[9];

    private NfcAdapter mNfcAdapter;

    private static final String TAG = "wsclient";
    private WebSocketConnection mConnection;
    private int mFreq = 3;

    private void start() {

        final String wsuri = "ws://10.0.2.2:9000";

        try {
            mConnection = new WebSocketConnection();
            mConnection.connect(wsuri, new WebSocketHandler() {

                @Override
                public void onOpen() {
                    Log.d(TAG, "Status: Connected to " + wsuri);
                    // mConnection.sendTextMessage("Hello, world!");
                }

                @Override
                public void onTextMessage(String payload) {
                    Log.d(TAG, "Got echo: " + payload);
                }

                @Override
                public void onClose(int code, String reason) {
                    Log.d(TAG, "Connection lost.");
                    start();
                }
            });
        } catch (WebSocketException e) {

            Log.d(TAG, e.toString());
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == mAccelerometer) {
            System.arraycopy(event.values, 0, mLastAccelerometer, 0, event.values.length);
            mLastAccelerometerSet = true;
        } else if (event.sensor == mMagnetometer) {
            System.arraycopy(event.values, 0, mLastMagnetometer, 0, event.values.length);
            mLastMagnetometerSet = true;
        }

        if (mLastAccelerometerSet && mLastMagnetometerSet) {
            SensorManager.getRotationMatrix(mR, null, mLastAccelerometer, mLastMagnetometer);
            SensorManager.getOrientation(mR, mOrientation);
        }

        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE && mLastAccelerometerSet && mLastMagnetometerSet) {
            float axisX = event.values[0];
            float axisY = event.values[1];
            float axisZ = event.values[2];

            long millisec = 100; // Expect messages to be sent in every "millisec" interval

            if (lastSendTime == 0) {
                lastSendTime = event.timestamp;
            }

            if ((event.timestamp - lastSendTime) > (100*millisec) && mConnection != null && mConnection.isConnected()) {
                try {
                    this.mConnection.sendTextMessage(String.format(
                            "{ " +
                                    "\"instrument_id\": \"%d\", " +
                                    "\"freq\": \"%d\", " +
                                    "\"axis_x\": \"%f\", " +
                                    "\"axis_y\": \"%f\", " +
                                    "\"axis_z\": \"%f\", " +
                                    "\"orientation_x\": \"%f\", " +
                                    "\"orientation_y\": \"%f\", " +
                                    "\"orientation_z\": \"%f\"  " +
                            "}",
                            1, mFreq, axisX, axisY, axisZ, mOrientation[0], mOrientation[1], mOrientation[2]));
                }
                catch (Exception err) {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    err.printStackTrace(pw);
                }

                lastSendTime = event.timestamp;
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Tag tag = (Tag) intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        processNfcTag(tag);
    }

    private void processNfcTag(Tag tag) {
        if (tag != null) {
            Log.d(TAG, new String(tag.getId()));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // get an instance of the SensorManager
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sm.getSensorList(Sensor.TYPE_GYROSCOPE).size() != 0) {
            Sensor s = sm.getSensorList(Sensor.TYPE_GYROSCOPE).get(0);
            sm.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL);
        }

        mAccelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mMagnetometer = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        // start WebSocket client
        this.start();

        // register the NFC intent
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mNfcAdapter == null) {
            Log.e(TAG, "NFC is not supported.");
        }
        else if (mNfcAdapter.isEnabled()) {
            Log.d(TAG, "NFC enabled!");
        }
        else {
            Log.d(TAG, "NFC disabled.");
            showAlertDialog();
        }
    }

    private void showAlertDialog() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle(R.string.enable_nfc_title);
        alertDialog.setMessage(R.string.enable_nfc_msg);
        alertDialog.setPositiveButton(R.string.nfc_settings,
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
                    }
                    else {
                        startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                    }
                }
            }
        );
        alertDialog.setNegativeButton(R.string.close, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {}
        });
        alertDialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (sm.getSensorList(Sensor.TYPE_GYROSCOPE).size() != 0) {
            Sensor s = sm.getSensorList(Sensor.TYPE_GYROSCOPE).get(0);
            sm.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL);
        }

        mLastAccelerometerSet = false;
        mLastMagnetometerSet = false;
        sm.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sm.registerListener(this, mMagnetometer, SensorManager.SENSOR_DELAY_NORMAL);

    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        sm.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(Sensor arg0, int arg1) {
        // TODO Auto-generated method stub

    }

    public void setFreqUp(View view) {
        mFreq++;
    }

    public void setFreqDown(View view) {
        if (mFreq > 1)
        mFreq--;
    }
}
