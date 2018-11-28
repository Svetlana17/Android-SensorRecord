package io.iam360.sensorlog;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener2;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.LinkedList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends AppCompatActivity implements SensorEventListener2 {

    SensorManager manager;
    Button buttonStart;
    Button buttonStop;
    boolean isRunning;
    final String TAG = "SensorLog";
    FileWriter writer;
    Button shareButton;
    char delimiter = ';';

    private SensorData data = new SensorData();

    private static final char DEFAULT_SEPARATOR = ',';
    //CSVFormat fmt = CSVFormat.EXCEL.withDelimiter(';');
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        shareButton=(Button)findViewById(R.id.buttonShare);
        shareButton.setOnClickListener(new View.OnClickListener() {


            @Override
            public void onClick(View v) { share(); }}
        );
        isRunning = false;

        manager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        buttonStart = (Button)findViewById(R.id.buttonStart);
        buttonStop = (Button)findViewById(R.id.buttonStop);

        buttonStart.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                buttonStart.setEnabled(false);
                buttonStop.setEnabled(true);

                Log.d(TAG, "Writing to " + getStorageDir());
                try {
                   writer = new FileWriter(new File(getStorageDir(), "sensors_" + System.currentTimeMillis() + ".csv"));
                } catch (IOException e) {
                    e.printStackTrace();
                }

                manager.registerListener(MainActivity.this, manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), 0);
                manager.registerListener(MainActivity.this, manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), 0);

                isRunning = true;
                return true;
            }
        });
        buttonStop.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                buttonStart.setEnabled(true);
                buttonStop.setEnabled(false);
                isRunning = false;
                manager.flush(MainActivity.this);
                manager.unregisterListener(MainActivity.this);
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return true;
            }
        });
    }

    private String getStorageDir() {
        return this.getExternalFilesDir(null).getAbsolutePath();
      //  return "/storage/emulated/0/Android/data/com.iam360.sensorlog/";
    }

    @Override
    public void onFlushCompleted(Sensor sensor) {
    }

    @Override
    public void onSensorChanged(SensorEvent evt) {
        if(isRunning) {
            try {
                switch(evt.sensor.getType()) {
                    case Sensor.TYPE_GYROSCOPE:

                        data.setGyr(evt);
                        if(data.isAccDataExists()){
                            writer.write(data.getStringData());
                            data.clear();
                        }

                        break;
                    case Sensor.TYPE_ACCELEROMETER:

                        data.setAcc(evt);
                        if(data.isGyrDataExists()){
                            writer.write(data.getStringData());
                            data.clear();
                        }
                        break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void share() {
        File dir = getExternalFilesDir(null);
        File zipFile = new File(dir, "accel.zip");
        if (zipFile.exists()) {
            zipFile.delete();
        }
        File[] fileList = dir.listFiles();
        try {
            zipFile.createNewFile();
            ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile));
            //  out.setLevel(Deflater.DEFAULT_COMPRESSION);
            for (File file : fileList) {
                zipFile(out, file);
            }
            out.close();
            sendBundleInfo(zipFile);
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), "Can't send file!", Toast.LENGTH_LONG).show();
        }
    }

    private static void zipFile(ZipOutputStream zos, File file) throws IOException {
        zos.putNextEntry(new ZipEntry(file.getName()));
        FileInputStream fis = new FileInputStream(file);
        //byte[] buffer = new byte[4092];
        byte[] buffer = new byte[10000];
        int byteCount = 0;
        try {
            while ((byteCount = fis.read(buffer)) != -1) {
                zos.write(buffer, 0, byteCount);
            }
        } finally {
            safeClose(fis);
        }
        zos.closeEntry();
    }

    private static void safeClose(FileInputStream fis) {
        try {
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendBundleInfo(File file) {
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setType("message/rfc822");
        emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + file));
        startActivity(Intent.createChooser(emailIntent, "Send data"));
//    }
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}

class SensorData {

    private SensorEvent gyrEvent;
    private SensorEvent accEvent;

    float xaf, yaf, zaf;
    float xgf, ygf, zgf;
    float alpha=0.05f;
    float k=0.5f;

    public void setGyr(SensorEvent gyrEvent){
        this.gyrEvent = gyrEvent;
    }

    public void setAcc(SensorEvent accEvent){
        this.accEvent = accEvent;
    }

    public boolean isAccDataExists(){
        return accEvent != null;
    }

    public boolean isGyrDataExists(){
        return gyrEvent != null;
    }

    public void clear(){
        gyrEvent = null;
        accEvent = null;
    }

    public String getStringData(){

        xaf=xaf+alpha*(accEvent.values[0]-xaf);
        yaf=yaf+alpha*(accEvent.values[1]-yaf);
        zaf=zaf+alpha*(accEvent.values[2]-zaf);

        xgf = 1-k * gyrEvent.values[0];
        ygf = 1-k * gyrEvent.values[1];
        zgf = (1-k) * gyrEvent.values[2];

        return String.format("%d; %f; %f; %f; %f; %f; %f; %f; %f; %f; %f; %f; %f;\n", gyrEvent.timestamp,
                accEvent.values[0], accEvent.values[1], accEvent.values[2], xaf,yaf,zaf,
                gyrEvent.values[0], gyrEvent.values[1], gyrEvent.values[2], xgf, ygf, zgf);
    }
}
