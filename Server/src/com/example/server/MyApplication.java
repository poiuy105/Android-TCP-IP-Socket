package com.example.server;

import android.app.Application;
import android.util.Log;

public class MyApplication extends Application {
    private static final String TAG = "MyApplication";
    
    private static MyApplication instance;
    private static boolean isMiuiTv = false;
    private static boolean isMiuiDevice = false;
    private static boolean isTvDevice = false;
    private static boolean isVersion1_3_48 = false;
    private static String miuiVersion = null;
    private static MiuiTvDetector.MiuiTvInfo miuiTvInfo = null;
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        
        Log.d(TAG, "Application onCreate - initializing MIUI TV detection");
        initMiuiTvDetection();
    }
    
    private void initMiuiTvDetection() {
        try {
            miuiTvInfo = MiuiTvDetector.getMiuiTvInfo(this);
            
            isMiuiTv = miuiTvInfo.isMiuiTv;
            isMiuiDevice = miuiTvInfo.isMiuiDevice;
            isTvDevice = miuiTvInfo.isTvDevice;
            isVersion1_3_48 = miuiTvInfo.isVersion1_3_48;
            miuiVersion = miuiTvInfo.miuiVersion;
            
            Log.d(TAG, "MIUI TV detection completed:");
            Log.d(TAG, "  isMiuiTv: " + isMiuiTv);
            Log.d(TAG, "  isMiuiDevice: " + isMiuiDevice);
            Log.d(TAG, "  isTvDevice: " + isTvDevice);
            Log.d(TAG, "  isVersion1_3_48: " + isVersion1_3_48);
            Log.d(TAG, "  miuiVersion: " + miuiVersion);
            Log.d(TAG, "  Full info: " + miuiTvInfo.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize MIUI TV detection", e);
        }
    }
    
    public static MyApplication getInstance() {
        return instance;
    }
    
    public static boolean isMiuiTv() {
        return isMiuiTv;
    }
    
    public static boolean isMiuiDevice() {
        return isMiuiDevice;
    }
    
    public static boolean isTvDevice() {
        return isTvDevice;
    }
    
    public static boolean isVersion1_3_48() {
        return isVersion1_3_48;
    }
    
    public static String getMiuiVersion() {
        return miuiVersion;
    }
    
    public static MiuiTvDetector.MiuiTvInfo getMiuiTvInfo() {
        return miuiTvInfo;
    }
    
    public static void refreshMiuiTvDetection() {
        if (instance != null) {
            MiuiTvDetector.clearCache();
            instance.initMiuiTvDetection();
        }
    }
}
