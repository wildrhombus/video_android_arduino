package com.maxdean.blinkyapp;

//import java.io.FileDescriptor;
import java.io.IOException;

import android.app.ActionBar.LayoutParams;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaPlayer.OnVideoSizeChangedListener;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.android.future.usb.UsbAccessory;
import com.android.future.usb.UsbManager;

public class BlinkyActivity extends Activity implements OnBufferingUpdateListener, OnCompletionListener,
OnPreparedListener, OnVideoSizeChangedListener, SurfaceHolder.Callback {
    private static final String TAG = "BlinkyActivity";

    private int mVideoWidth;
    private int mVideoHeight;
    private MediaPlayer mMediaPlayer;
    private SurfaceView mPreview;
    private SurfaceHolder holder;
    private String path;
    private boolean mIsVideoSizeKnown = false;
    private boolean mIsVideoReadyToBePlayed = false;

    private static final String ACTION_USB_PERMISSION = "com.wildrhombus.Blinky.action.USB_PERMISSION";

    private UsbManager mUsbManager;
    private PendingIntent mPermissionIntent;
    private boolean mPermissionRequestPending;

    UsbAccessory mAccessory;
    ParcelFileDescriptor mFileDescriptor = null;

    private PowerManager.WakeLock wl;

    public int videostarted = 0;
    public int videopaused = 0;

    int CURRENT_TAB = 0;

    Thread mThread;

    TextView fileDescText;

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            fileDescText.setText("onReceive");

            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                fileDescText.setText("Attach");

                synchronized (this) {
                    UsbAccessory accessory = UsbManager.getAccessory(intent);
                    if (intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        openAccessory(accessory);
                    } else {
                        Log.d(TAG, "permission denied for accessory "
                                + accessory);
                    }
                    mPermissionRequestPending = false;
                }
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                fileDescText.setText("Detach");

                UsbAccessory accessory = UsbManager.getAccessory(intent);
                if (accessory != null && accessory.equals(mAccessory)) {
                    closeAccessory();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ((BlinkyApplication) getApplication()).setActivityStarted(true);

        mUsbManager = UsbManager.getInstance(this);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(
                ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        registerReceiver(mUsbReceiver, filter);

        if (getLastNonConfigurationInstance() != null) {
            mAccessory = (UsbAccessory) getLastNonConfigurationInstance();
            openAccessory(mAccessory);
        }


        Log.v(TAG, "Hellohello!");

        setContentView(R.layout.main);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BlinkyPartialScreen");

        mPreview = (SurfaceView) findViewById(R.id.surface);
        holder = mPreview.getHolder();
        holder.addCallback(this);

        fileDescText = (TextView)findViewById(R.id.textView1);
        fileDescText.setText("hellohello");
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        if (mAccessory != null) {
            return mAccessory;
        } else {
            return super.onRetainNonConfigurationInstance();
        }
    }
    
    @Override
    public void onResume() {

        Log.v(TAG, "onResume");

        wl.acquire();
        Toast.makeText(BlinkyActivity.this,
                "Resumed", Toast.LENGTH_LONG)
                .show();

        if (mAccessory != null ) {
            Log.v(TAG, "mAccessory wasn't null!");
            enableControls(true);
            return;
        }

        UsbAccessory[] accessories = mUsbManager.getAccessoryList();

        Log.v(TAG, "all the accessories: " + accessories);

        UsbAccessory accessory = (accessories == null ? null : accessories[0]);
        if (accessory != null) {

            if (mUsbManager.hasPermission(accessory)) {
                Log.v(TAG, "mUsbManager does have permission");
                openAccessory(accessory);
            } else {
                Log.v(TAG, "mUsbManager did not have permission");

                synchronized (mUsbReceiver) {
                    if (!mPermissionRequestPending) {
                        mUsbManager.requestPermission(accessory,
                                mPermissionIntent);
                        mPermissionRequestPending = true;
                    }
                }
            }
        } else {
            Log.d(TAG, "mAccessory is null");
        }
        super.onResume();
    }
    
    @Override
    public void onPause() {
        Log.v(TAG, "onPause");

         if( wl.isHeld() ) {
             wl.release();
         }


        closeAccessory();

        Log.v(TAG, "done, now pause");

        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(onNotice);
    }

    @Override
    public void onDestroy() {
        Toast.makeText(BlinkyActivity.this,
                "destroyed", Toast.LENGTH_LONG)
                .show();
        Log.v(TAG, "onDestroy");
        fileDescText.setText("destroy");

        closeAccessory();
        unregisterReceiver(mUsbReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(onNotice);
        ((BlinkyApplication) getApplication()).setActivityStarted(false);

        releaseMediaPlayer();
        doCleanUp();

        super.onDestroy();
    }

     @Override
     protected void onStop() {
         fileDescText.setText("stopped");
            Toast.makeText(BlinkyActivity.this,
                    "Stopped", Toast.LENGTH_LONG)
                    .show();
          super.onStop();
     }

     private void playVideo() {
         doCleanUp();
         try {
             path = Environment.getExternalStorageDirectory().getPath()+"/Movies/BLINKY.mp4";
             if (path != "") {
                 // Create a new media player and set the listeners
                 mMediaPlayer = new MediaPlayer();
                 mMediaPlayer.setDataSource(path);
                 mMediaPlayer.setDisplay(holder);
                 mMediaPlayer.prepare();
                 mMediaPlayer.setOnBufferingUpdateListener(this);
                 mMediaPlayer.setOnCompletionListener(this);
                 mMediaPlayer.setOnPreparedListener(this);
                 mMediaPlayer.setOnVideoSizeChangedListener(this);
    //	         mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
             }
         } catch (Exception e) {
             Log.e(TAG, "error: " + e.getMessage(), e);
         }
     }

     public void onBufferingUpdate(MediaPlayer arg0, int percent) {
         Log.d(TAG, "onBufferingUpdate percent:" + percent);
     }

     public void onCompletion(MediaPlayer arg0) {
         Log.d(TAG, "onCompletion called");
     }

     public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
         Log.v(TAG, "onVideoSizeChanged called");
         if (width == 0 || height == 0) {
             Log.e(TAG, "invalid video width(" + width + ") or height(" + height + ")");
             return;
         }
         mIsVideoSizeKnown = true;
         mVideoWidth = width;
         mVideoHeight = height;
         if (mIsVideoReadyToBePlayed && mIsVideoSizeKnown) {
             fileDescText.setText("on sized changed start pause");
             pauseVideoPlayback();
         }
     }

     public void onPrepared(MediaPlayer mediaplayer) {
         Log.d(TAG, "onPrepared called");
         mIsVideoReadyToBePlayed = true;
         mediaplayer.setLooping(true);
         if (mIsVideoReadyToBePlayed && mIsVideoSizeKnown) {
             startVideoPlayback();
         }
     }

     public void surfaceChanged(SurfaceHolder surfaceholder, int i, int j, int k) {
         Log.d(TAG, "surfaceChanged called");
     }

     public void surfaceDestroyed(SurfaceHolder surfaceholder) {
         Log.d(TAG, "surfaceDestroyed called");
     }

     public void surfaceCreated(SurfaceHolder holder) {
         Log.d(TAG, "surfaceCreated called");
         playVideo();
     }

     private void releaseMediaPlayer() {
         if (mMediaPlayer != null) {
             mMediaPlayer.release();
             mMediaPlayer = null;
         }
     }

     private void doCleanUp() {
         mVideoWidth = 0;
         mVideoHeight = 0;
         mIsVideoReadyToBePlayed = false;
         mIsVideoSizeKnown = false;
     }

     public void startVideoPlayback() {
         wl.acquire();
         getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

         videostarted++;
         Log.v(TAG, "startVideoPlayback");
         if( mMediaPlayer == null ) {
             fileDescText.setText("start player is null");
             return;
         }
         if( !mMediaPlayer.isPlaying() ) {
             try {
                 mMediaPlayer.start();
             } catch (IllegalArgumentException e) {
                    fileDescText.setText(e.getMessage());
                } catch (SecurityException e) {
                    fileDescText.setText(e.getMessage());
                } catch (IllegalStateException e) {
                    fileDescText.setText(e.getMessage());
                }
         } else {
            //	fileDescText.setText("already started " + videostarted);
         }
     }

     public void pauseVideoPlayback() {
         getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
         if( wl.isHeld() ) {
             wl.release();
         }
         videopaused++;
         Log.v(TAG, "pauseVideoPlayback");
         if( mMediaPlayer == null ) {
             fileDescText.setText("pause player is null");
             return;
         }

         if( mMediaPlayer.isPlaying() ) {
             try {
             mMediaPlayer.pause();
             } catch (IllegalArgumentException e) {
                    fileDescText.setText(e.getMessage());
                } catch (SecurityException e) {
                    fileDescText.setText(e.getMessage());
                } catch (IllegalStateException e) {
                    fileDescText.setText(e.getMessage());
                }
         } else {
            //	fileDescText.setText("already paused " + videopaused);
         }
     }

     private BroadcastReceiver onNotice= new BroadcastReceiver() {
         private int numreceived = 0;
         @Override
            public void onReceive(Context context, Intent intent) {
                // intent can contain anydata
                numreceived++;
                String sensor = intent.getStringExtra("sensor");
            //	fileDescText.setText(sensor);
                if( sensor.contains( "on" ) ) {
                    fileDescText.setText("start video playback");
                    //holder.setFixedSize(mVideoWidth, mVideoHeight);
                    startVideoPlayback();
                }
                if( sensor.contains("off") ) {
                    fileDescText.setText("pause video");
                    pauseVideoPlayback();
                }
            }
     };

    private void openAccessory(UsbAccessory accessory) {
        Log.v(TAG, "openAccessory: " + accessory);
        Toast.makeText(BlinkyActivity.this,
                "open", Toast.LENGTH_LONG)
                .show();
        mFileDescriptor = mUsbManager.openAccessory(accessory);

        Log.d(TAG, "Tried to open");

        if (mFileDescriptor != null) {
            mAccessory = accessory;
        //	FileDescriptor fd = mFileDescriptor.getFileDescriptor();

            IntentFilter iff= new IntentFilter(BlinkyService.ACTION);
            LocalBroadcastManager.getInstance(this).registerReceiver(onNotice, iff);

        //	mThread = new Thread(null, this, "DemoKit");
        //	mThread.start();
            Log.v(TAG, "accessory opened");
            enableControls(true);
        } else {
            fileDescText.setText("Open failed");
            Toast.makeText(BlinkyActivity.this,
                    "open failed", Toast.LENGTH_LONG)
                    .show();

            Log.v(TAG, "accessory open fail");
            enableControls(false);
        }

    }

    private void closeAccessory() {
    //	fileDescText.setText("Close");

        Log.v(TAG, "closing accessory");

        Log.v(TAG,"stop updater if running");
        if( BlinkyService.self.isUpdaterRunning() ) {
            try {
                BlinkyService.self.stopUpdater();
            } catch(Exception e) {

            }
        }

        try {
            if (mFileDescriptor != null) {
                mFileDescriptor.close();
            }
        } catch (IOException e) {
            fileDescText.setText("file close exception "+e.getMessage());
        } finally {
            mFileDescriptor = null;
            mAccessory = null;
        }
        ((BlinkyApplication) getApplication()).setFileDescriptor(mFileDescriptor);
        ((BlinkyApplication) getApplication()).setUsbAccessory(mAccessory);
    }

    private void enableControls(boolean b) {
        //fileDescText.setText("Enable Controls");

        ((BlinkyApplication) getApplication()).setFileDescriptor(mFileDescriptor);
        ((BlinkyApplication) getApplication()).setUsbAccessory(mAccessory);

        if(!b) {
            //fileDescText.setText("Stop Updater if running");

            Log.v(TAG,"stop updater if running");
            if( BlinkyService.self.isUpdaterRunning() ) {
                try {
                    BlinkyService.self.stopUpdater();
                } catch(Exception e) {

                }
            }
        } else {
            if( !BlinkyService.self.isUpdaterRunning() ) {
                try {
                    fileDescText.setText("Start Updater");

                    BlinkyService.self.startUpdater();
                } catch(Exception e) {

                }
            }
        }

    }

}
