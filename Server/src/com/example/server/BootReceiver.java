package com.example.server;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    private static final int MAX_RETRY_COUNT = 3;
    private static final int RETRY_DELAY_MS = 5000;
    private static final int TV_BOOT_DELAY_MS = 10000;
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Received broadcast: " + action);
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(action) ||
            "android.intent.action.REBOOT".equals(action) ||
            "android.intent.action.LOCKED_BOOT_COMPLETED".equals(action) ||
            "android.intent.action.USER_PRESENT".equals(action) ||
            "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {
            
            Log.d(TAG, "Starting Server service after boot");
            
            boolean isTvDevice = isTvDevice(context);
            int bootDelay = isTvDevice ? TV_BOOT_DELAY_MS : 2000;
            
            Log.d(TAG, "Device type: " + (isTvDevice ? "TV" : "Non-TV") + ", boot delay: " + bootDelay + "ms");
            
            startServiceWithRetry(context, bootDelay, 0);
        }
    }
    
    private boolean isTvDevice(Context context) {
        PackageManager pm = context.getPackageManager();
        
        boolean isTv = pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION);
        boolean isLeanback = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        boolean isAndroidTv = pm.hasSystemFeature("android.hardware.type.tv");
        
        Log.d(TAG, "isTv: " + isTv + ", isLeanback: " + isLeanback + ", isAndroidTv: " + isAndroidTv);
        
        return isTv || isLeanback || isAndroidTv;
    }
    
    private void startServiceWithRetry(final Context context, final int delay, final int retryCount) {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                boolean success = startService(context);
                
                if (!success && retryCount < MAX_RETRY_COUNT) {
                    Log.w(TAG, "Service start failed, retrying... (" + (retryCount + 1) + "/" + MAX_RETRY_COUNT + ")");
                    startServiceWithRetry(context, RETRY_DELAY_MS, retryCount + 1);
                } else if (!success) {
                    Log.e(TAG, "Failed to start service after " + MAX_RETRY_COUNT + " retries");
                } else {
                    Log.d(TAG, "Service started successfully");
                }
            }
        }, delay);
    }
    
    private boolean startService(Context context) {
        try {
            Intent startIntent = new Intent(context, ServerService.class);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(startIntent);
                Log.d(TAG, "Started foreground service for Android O+");
            } else {
                context.startService(startIntent);
                Log.d(TAG, "Started service for pre-Android O");
            }
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error starting service: " + e.getMessage(), e);
            return false;
        }
    }
}
