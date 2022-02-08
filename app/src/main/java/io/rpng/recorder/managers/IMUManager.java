package io.rpng.recorder.managers;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import io.rpng.recorder.activities.MainActivity;

public class IMUManager implements SensorEventListener {

    // Activity
    Activity activity;

    // Sensor listeners
    private SensorManager mSensorManager;
    private Sensor mAccel;
    private Sensor mGyro;
    private Sensor mGyroUncalibrated;

    // Data storage (linear)
    long linear_time;
    int linear_acc;
    float[] linear_data;

    // Data storage (angular)
    long angular_time;
    long angular_uncalibrated_time;
    int angular_acc;
    float[] angular_data;
    float[] angular_uncalibrated_data;
    private boolean mWriteHeaderLine = true;
    private final String mHeaderLine = "timestamp,ax,ay,az,gx,gy,gz,gx_uncal,gy_uncal,gz_uncal,gbx,gby,gbz";

    public IMUManager(Activity activity) {
        // Set activity
        this.activity = activity;
        // Create the sensor objects
        mSensorManager = (SensorManager)activity.getSystemService(Context.SENSOR_SERVICE);
        mAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyro = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mGyroUncalibrated = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED);
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Handle accelerometer reading
        if (sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            linear_acc = accuracy;
        }
        // Handle a gyro reading
        else if (sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            angular_acc = accuracy;
        }
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {

        // Set event timestamp to current time in milliseconds
        // http://stackoverflow.com/a/9333605
        //event.timestamp = (new Date()).getTime() + (event.timestamp - System.nanoTime()) / 1000000L;

        // TODO: Figure out better way, for now just use the total time
        // https://code.google.com/p/android/issues/detail?id=56561
        //event.timestamp = new Date().getTime();
        if (MainActivity.starting_offset == -1) {
            throw new RuntimeException("Starting Offset has not been initialized!");
        }
        event.timestamp = (event.timestamp + MainActivity.starting_offset) / 1000000L;

        // Handle accelerometer reading
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            linear_time = event.timestamp;
            linear_data = event.values;
        }
        // Handle a gyro reading
        else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            angular_time = event.timestamp;
            angular_data = event.values;
        }
        else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE_UNCALIBRATED) {
            angular_uncalibrated_time = event.timestamp;
            angular_uncalibrated_data = event.values;
        }

        // If the timestamps are not zeros, then we know we have two measurements
        if(linear_time != 0 && angular_time != 0 && angular_uncalibrated_time != 0) {

            // Write the data to file if we are recording
            if(MainActivity.is_recording && MainActivity.record_imu) {

                // Create folder name
                String filename = "imu.csv";
                String path = Environment.getExternalStorageDirectory().getAbsolutePath()
                        + "/dataset_recorder/" + MainActivity.folder_name + "/";

                // Create export file
                new File(path).mkdirs();
                File dest = new File(path + filename);

                try {
                    // If the file does not exist yet, create it
                    if(!dest.exists()) {
                        dest.createNewFile();
                    }

                    // The true will append the new data
                    BufferedWriter writer = new BufferedWriter(new FileWriter(dest, true));

                    if (mWriteHeaderLine) {
                        writer.write(mHeaderLine + "\n");
                        writer.flush();
                        mWriteHeaderLine = false;
                    }

                    // Master string of information
                    String data = linear_time
                            + "," + linear_data[0] + "," + linear_data[1] + "," + linear_data[2]
                            + "," + angular_data[0] + "," + angular_data[1] + "," + angular_data[2]
                            + "," + angular_uncalibrated_data[0] + "," + angular_uncalibrated_data[1] + "," + angular_uncalibrated_data[2]
                            + "," + angular_uncalibrated_data[3] + "," + angular_uncalibrated_data[4] + "," + angular_uncalibrated_data[5];

                    // Appends the string to the file and closes
                    writer.write(data + "\n");
                    writer.flush();
                    writer.close();
                }
                // Ran into a problem writing to file
                catch(IOException ioe) {
                    System.err.println("IOException: " + ioe.getMessage());
                }
            }

            // Reset timestamps
            linear_time = 0;
            angular_time = 0;
        }
    }

    public void prepareNewRecording() {
        mWriteHeaderLine = true;
    }

    /**
     * This will register all IMU listeners
     */
    public void register() {
        // Get the freq we should get messages at (default is SensorManager.SENSOR_DELAY_GAME)
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(activity);
        String imuFreq = sharedPreferences.getString("perfImuFreq", "1");
        // Register the IMUs
        Log.i("IMUManager", "register: IMU latency=" + imuFreq);
        mSensorManager.registerListener(this, mAccel, Integer.parseInt(imuFreq));
        mSensorManager.registerListener(this, mGyro, Integer.parseInt(imuFreq));
        mSensorManager.registerListener(this, mGyroUncalibrated, Integer.parseInt(imuFreq));
    }

    /**
     * This will unregister all IMU listeners
     */
    public void unregister() {
        mSensorManager.unregisterListener(this, mAccel);
        mSensorManager.unregisterListener(this, mAccel);
        mSensorManager.unregisterListener(this);
    }
}
