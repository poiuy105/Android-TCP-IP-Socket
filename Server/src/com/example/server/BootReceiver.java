package com.example.server;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Received broadcast: " + action);
        
        // Handle multiple boot-related actions for better compatibility
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(action) ||
            "android.intent.action.REBOOT".equals(action) ||
            "android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)) {
            
            Log.d(TAG, "Starting Server service after boot");
            
            // Start the service directly without UI
            Intent startIntent = new Intent(context, ServerService.class);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(startIntent);
                    Log.d(TAG, "Started foreground service for Android O+");
                } else {
                    context.startService(startIntent);
                    Log.d(TAG, "Started service for pre-Android O");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error starting service: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}