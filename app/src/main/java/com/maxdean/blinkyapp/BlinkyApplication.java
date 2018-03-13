package com.maxdean.blinkyapp;

import java.io.FileInputStream;

import android.app.Application;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

import com.android.future.usb.UsbAccessory;

public class BlinkyApplication extends Application {
    private static final String TAG = "ServiceADKApplication";

    private UsbAccessory mAccessory;
    private ParcelFileDescriptor mFileDescriptor;
    public static boolean activityStarted = false;


    @Override
    public void onCreate() {
        super.onCreate();
        Intent intent = new Intent(this, BlinkyService.class);
        startService(intent);
    }


    public ParcelFileDescriptor getFileDescriptor() {
        return mFileDescriptor;
    }


    public boolean activityRunning() {
        return activityStarted;
    }

    public void setActivityStarted( boolean s) {
        activityStarted = s;
    }

    public boolean adkConnected() {
        if(mFileDescriptor != null) {
            Log.v(TAG, "ADK is connected");
            return true;
        }

        Log.v(TAG, "ADK not connected");
        return false;
    }

    public void setFileDescriptor(ParcelFileDescriptor f) {
        mFileDescriptor = f;
    }

    public void setUsbAccessory(UsbAccessory a) {
        mAccessory = a;
    }
}
