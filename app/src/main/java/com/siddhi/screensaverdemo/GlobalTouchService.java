package com.siddhi.screensaverdemo;

import android.app.ActivityManager;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public class GlobalTouchService extends Service implements View.OnTouchListener {

    private Handler mHandler;
    private Runnable mRunnable;
    private final int mTimerDelay = 20000;//inactivity delay in milliseconds
    private LinearLayout mTouchLayout;//the transparent view

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mTouchLayout = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        );
        mTouchLayout.setLayoutParams(lp);
        mTouchLayout.setOnTouchListener(this);

        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        WindowManager.LayoutParams mParams;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // Android O (API 26) and above
            mParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, // Use this for Android 13+
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );
        } else {
            mParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );
        }

        mParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowManager.addView(mTouchLayout, mParams);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        initTimer();
        return START_STICKY;
    }


    @Override
    public boolean onTouch(View v, MotionEvent event) {
        Log.d("IdleDetectorService", "Touch detected. Resetting timer");
        initTimer();
        return false;
    }


//    @Override
//    public void onDestroy() {
//        super.onDestroy();
//        mHandler.removeCallbacks(mRunnable);
//        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
//        if (windowManager != null && mTouchLayout != null) {
//            windowManager.removeView(mTouchLayout);
//        }
//    }

//    private boolean isMediaPlaying(Context context) {
//        MediaSessionManager mediaSessionManager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
//        if (mediaSessionManager != null) {
//            List<MediaController> controllers = mediaSessionManager.getActiveSessions(null);
//            for (MediaController controller : controllers) {
//                MediaController.PlaybackInfo playbackInfo = controller.getPlaybackInfo();
//                if (playbackInfo != null) {
//                    Log.d("MediaCheck", "Playback Info: " + playbackInfo.toString());
//                    if (controller.getPlaybackState() != null &&
//                            controller.getPlaybackState().getState() == android.media.session.PlaybackState.STATE_PLAYING) {
//                        return true;
//                    }
//                }
//            }
//        }
//        return false;
//    }

    private boolean isAudioPlaying(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            return audioManager.isMusicActive();
        }
        return false;
    }


    /**
     * (Re)sets the timer to send the inactivity broadcast
     */
    private void initTimer() {
        // Start timer and timer task
        if (mRunnable == null) {

            mRunnable = new Runnable() {
                @Override
                public void run() {
                    Log.d("IdleDetectorService", "Inactivity detected. Sending broadcast to start the app");

                    try {
                        if (!isAudioPlaying(getApplicationContext())) {
                            boolean isInForeground = new ForegroundCheckTask().execute(getApplicationContext()).get();

                            if (!isInForeground) {
                                Intent launchIntent = getApplication()
                                        .getPackageManager()
                                        .getLaunchIntentForPackage("com.siddhi.screensaverdemo");
                                if (launchIntent != null) {
                                    Log.d("IdleDetectorService", "App started");
                                    getApplication().startActivity(launchIntent);
                                }
                            }
                        }

                        stopSelf();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
        }

        if (mHandler == null) {
            mHandler = new Handler();
        }

        mHandler.removeCallbacks(mRunnable);
        mHandler.postDelayed(mRunnable, mTimerDelay);
    }


    private class ForegroundCheckTask extends AsyncTask<Context, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Context... params) {
            final Context context = params[0];
            return isAppOnForeground(context);
        }

        private boolean isAppOnForeground(Context context) {
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
                // For Android KitKat and below
                List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
                if (appProcesses != null) {
                    final String packageName = context.getPackageName();
                    for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
                        if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                                appProcess.processName.equals(packageName)) {
                            return true;
                        }
                    }
                }
            } else {
                // For Android Lollipop (API 21) and above
                UsageStatsManager usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
                long currentTime = System.currentTimeMillis();

                // Query app usage stats for the last minute
                List<UsageStats> usageStatsList = usageStatsManager.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY,
                        currentTime - 1000 * 60, currentTime);

                if (usageStatsList != null && !usageStatsList.isEmpty()) {
                    SortedMap<Long, UsageStats> sortedMap = new TreeMap<>();
                    for (UsageStats usageStats : usageStatsList) {
                        sortedMap.put(usageStats.getLastTimeUsed(), usageStats);
                    }

                    if (!sortedMap.isEmpty()) {
                        UsageStats recentStats = sortedMap.get(sortedMap.lastKey());
                        if (recentStats != null && recentStats.getPackageName().equals(context.getPackageName())) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

}
