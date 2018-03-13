package com.maxdean.blinkyapp;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class BlinkyService extends Service {

    private static final String TAG = "BlinkyAppBlinkyService";

    public static final String ACTION="com.maxdean.blinkyApp.blinkyAction";

    private Updater updater;

    public static BlinkyService self;

    FileInputStream mInputStream;
    BlinkyActivity mHostActivity;

    public int numrunning = 0;

    int CURRENT_TAB = 0;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    // ---------
    // Lifecycle
    // ---------

    @Override
    public void onCreate() {
        Log.v(TAG, "service onCreate");
        self = this;

        Notification notification = new Notification(R.drawable.ic_launcher, getText(R.string.ticker_text),
                System.currentTimeMillis());
        Intent notificationIntent = new Intent(this, BlinkyActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        notification.setLatestEventInfo(this, getText(R.string.notification_title),
                getText(R.string.notification_message), pendingIntent);

        int myId = 8934;
        startForeground( myId, notification);


//		updater = new Updater();
        updater = null;
        super.onCreate();
    }

    @Override
    public synchronized void onStart(Intent intent, int startId) {
        if( !((BlinkyApplication) getApplication()).activityRunning() ) {
            Intent intents = new Intent(getBaseContext(), BlinkyActivity.class);
            intents.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intents);
        }

        Log.v(TAG, "service onStart");
    //	sendLocalMessage("started");
        super.onStart(intent, startId);
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "onDestroy");

        // Stop the updater
        if (updater.isRunning()) {
            updater.interrupt();
        }

    //	updater = null;

        super.onDestroy();
    }

    // -------
    // Updater
    // -------

    public void startUpdater() {
        Log.v(TAG, "Starting updater");
        if( updater == null ) {
            Log.d(TAG, "updater never run");
            updater = new Updater();
            updater.start();
            sendLocalMessage("Updater started");
        } else {
            sendLocalMessage("updater already running");
        }
    }

    public void stopUpdater() {
        Log.v(TAG, "Stopping updater");

        if( updater != null ) {
            Log.v(TAG, "updater not null");
            updater.setIsRunning(false);

            if (updater.isRunning()) {
                updater.interrupt();
            }
        }
    }

    public boolean isUpdaterRunning() {
        if( updater == null ) {
            return false;
        }
        return ( updater.isRunning() );
    }

    class Updater extends Thread {
        private static final long DELAY = 2;
        private boolean isRunning = false;
        public FileInputStream mInputStream;
        public ParcelFileDescriptor mFileDescriptor;

        public Updater() {
            super("Updater");
            mFileDescriptor = ((BlinkyApplication) getApplication()).getFileDescriptor();
            FileDescriptor fd = mFileDescriptor.getFileDescriptor();
            mInputStream = new FileInputStream(fd);
            numrunning++;
        }

        @Override
        public void run() {
            int ret = 0;
            byte[] buffer = new byte[16384];
            int i;

            int count = 0;
            isRunning = true;
            sendLocalMessage( "in run");
            Log.v(TAG,"in run");

            try {
                while( !Thread.interrupted() && ret >= 0 ) {
                    count++;

                    try {
                        ret = mInputStream.read(buffer);
                    } catch (IOException e) {
                        sendLocalMessage("Exception " + e.getMessage() );
                        Log.v(TAG,"Exception " + e.getMessage() );
                        break;
                    }
                    i = 0;
                    while (i < ret) {
                        int len = ret - i;

                        Log.v(TAG, "Read: " + buffer[i]);
                        if( buffer[i] == 0x01 ) {
                            count++;
                            if (len >= 2) {
                                if( buffer[i+1] == 1 ) {
                                    sendLocalMessage("on");
                                    Log.v(TAG,"on num running " + numrunning);
                                } else {
                                    sendLocalMessage("off");
                                    Log.v(TAG, "off num running " + numrunning);
                                }
                            }

                            i += 2;
                        } else {
                            sendLocalMessage("unknown " + buffer[i+1] + " len " + len);

                            Log.d(TAG, "unknown msg: " + buffer[i]);
                            i = len;
                        }
                    }
                    Thread.sleep(DELAY);
                }
            } catch( InterruptedException ex ) {
                sendLocalMessage("Thread shutting down");
            } finally {
                try {
                    mInputStream.close();
                } catch( IOException e ) {
                    sendLocalMessage("can't close input");
                } finally {
                    sendLocalMessage("closed input");
                }
                /*
                try {
                    mFileDescriptor.close();
                } catch( IOException e ) {
                    sendLocalMessage("can't close filedescriptor");
                } finally {
                    sendLocalMessage("closed filedescriptor");
                }
                */
                mFileDescriptor = null;
                mInputStream = null;
                updater = null;
            }

            Log.v(TAG,"not running");
        }

        public boolean isRunning() {
            return this.isRunning;
        }

        public void setIsRunning(boolean s) {
            this.isRunning = s;
        }
    }

    public void sendLocalMessage( String msg ) {
        if( ((BlinkyApplication) getApplication()).activityRunning() ) {
            Intent in = new Intent(ACTION);
            in.putExtra("sensor",msg);
            LocalBroadcastManager.getInstance(this).sendBroadcast(in);
            Log.v(TAG,"local message "+msg);
        }
    }

    public void enableControls(boolean b) {

    }
}
