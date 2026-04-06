package com.example.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.app.Notification.Builder;
import android.provider.Settings;
import android.net.Uri;

public class ServerService extends Service {
    private static final String TAG = "ServerService";
    private ServerSocket serverSocket;
    private TtsEngineManager ttsEngineManager;
    private boolean isRunning = false;
    private static final int serverPort = 1234;
    
    private FloatingWindowService floatingWindowService;
    private boolean isFloatingWindowBound = false;
    private TTSState currentTTSState = TTSState.IDLE;
    private Handler mainHandler;
    
    private ServiceConnection floatingWindowConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "FloatingWindowService connected");
            FloatingWindowService.LocalBinder binder = (FloatingWindowService.LocalBinder) service;
            floatingWindowService = binder.getService();
            isFloatingWindowBound = true;
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "FloatingWindowService disconnected");
            floatingWindowService = null;
            isFloatingWindowBound = false;
        }
    };
    
    private static final String NOTIFICATION_CHANNEL_ID = "server_service_channel";
    private static final int NOTIFICATION_ID = 1001;
    private int currentServerPort = 1234;
    
    private static final String PREFS_NAME = "miui_keepalive_prefs";
    private static final String KEY_WHITELIST_SHOWN = "whitelist_guide_shown";
    private static final String KEY_PERMISSION_SHOWN = "permission_guide_shown";
    
    private boolean isMIUITV = false;
    private boolean isMIUI = false;
    private String miuiVersion = "";
    
    private static final long HEARTBEAT_INTERVAL = 30000;
    private static final long RESTART_DELAY = 5000;
    private static final int MAX_RESTART_ATTEMPTS = 3;
    
    private Handler heartbeatHandler;
    private Runnable heartbeatRunnable;
    private int restartAttempts = 0;
    private long lastHeartbeatTime = 0;
    private PowerManager.WakeLock wakeLock;
    
    private Handler restartHandler;
    private Runnable restartRunnable;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        
        mainHandler = new Handler(Looper.getMainLooper());
        heartbeatHandler = new Handler(Looper.getMainLooper());
        restartHandler = new Handler(Looper.getMainLooper());
        
        detectMIUIDevice();
        
        if (isMIUITV) {
            Log.d(TAG, "MIUI TV detected, applying special keep-alive strategies");
            initMIUITVKeepAlive();
        }
        
        createNotificationChannel();
        
        startForeground(NOTIFICATION_ID, createNotification());
        
        ttsEngineManager = new TtsEngineManager(this);
        ttsEngineManager.setMaxRetryCount(3);
        ttsEngineManager.setRetryIntervalMs(1000);
        
        ttsEngineManager.setInitListener(new TtsEngineManager.TtsInitListener() {
            @Override
            public void onInitSuccess(TtsEngineManager.TtsEngineInfo engine) {
                Log.d(TAG, "TTS initialized successfully with engine: " + engine.packageName);
            }
            
            @Override
            public void onInitFailed(int error, String message) {
                Log.e(TAG, "TTS initialization failed: " + message + ", error: " + error);
            }
        });
        
        ttsEngineManager.setStateListener(new TtsEngineManager.TtsStateListener() {
            @Override
            public void onStateChanged(TTSState oldState, TTSState newState) {
                Log.d(TAG, "TTS state changed: " + oldState + " -> " + newState);
                currentTTSState = newState;
            }
            
            @Override
            public void onSpeakStart(String utteranceId) {
                Log.d(TAG, "TTS started speaking: " + utteranceId);
            }
            
            @Override
            public void onSpeakDone(String utteranceId) {
                Log.d(TAG, "TTS completed speaking: " + utteranceId);
                Log.d(TAG, "Starting 2-second delay before hiding window");
                
                mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "2-second delay elapsed, calling hideFloatingWindow()");
                        hideFloatingWindow();
                    }
                }, 2000);
            }
        });
        
        ttsEngineManager.setErrorListener(new TtsEngineManager.TtsErrorListener() {
            @Override
            public void onError(String utteranceId, int errorCode, String errorMessage) {
                Log.e(TAG, "TTS error - utteranceId: " + utteranceId + ", error: " + errorMessage);
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        hideFloatingWindow();
                    }
                });
            }
            
            @Override
            public void onEngineError(TtsEngineManager.TtsEngineInfo engine, int errorCode, String errorMessage) {
                Log.e(TAG, "TTS engine error - engine: " + engine.packageName + ", error: " + errorMessage);
            }
        });
        
        ttsEngineManager.initialize();
        
        Intent floatingWindowIntent = new Intent(this, FloatingWindowService.class);
        startService(floatingWindowIntent);
        bindService(floatingWindowIntent, floatingWindowConnection, Context.BIND_AUTO_CREATE);
        
        startServer();
        
        startHeartbeat();
    }
    
    private void detectMIUIDevice() {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method get = c.getMethod("get", String.class);
            
            miuiVersion = (String) get.invoke(null, "ro.miui.ui.version.name");
            String miuiCode = (String) get.invoke(null, "ro.miui.ui.version.code");
            String deviceType = (String) get.invoke(null, "ro.build.characteristics");
            String productName = (String) get.invoke(null, "ro.product.name");
            String device = (String) get.invoke(null, "ro.product.device");
            
            isMIUI = miuiVersion != null && !miuiVersion.isEmpty();
            
            if (isMIUI) {
                Log.d(TAG, "MIUI detected - Version: " + miuiVersion + ", Code: " + miuiCode);
                Log.d(TAG, "Device type: " + deviceType + ", Product: " + productName + ", Device: " + device);
                
                isMIUITV = deviceType != null && deviceType.toLowerCase().contains("tv");
                
                if (!isMIUITV) {
                    isMIUITV = productName != null && 
                        (productName.toLowerCase().contains("tv") || 
                         productName.toLowerCase().contains("mitv") ||
                         productName.toLowerCase().contains("mibox"));
                }
                
                if (!isMIUITV) {
                    isMIUITV = device != null && 
                        (device.toLowerCase().contains("tv") || 
                         device.toLowerCase().contains("mitv") ||
                         device.toLowerCase().contains("mibox"));
                }
                
                if (isMIUITV) {
                    Log.d(TAG, "MIUI TV device detected!");
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Not a MIUI device or detection failed: " + e.getMessage());
            isMIUI = false;
            isMIUITV = false;
        }
    }
    
    private void initMIUITVKeepAlive() {
        acquireWakeLock();
        
        checkMIUIWhitelist();
        
        checkMIUIBackgroundPermission();
        
        setupMIUIRestartMechanism();
    }
    
    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "ServerService:MIUITVKeepAlive"
                );
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
                Log.d(TAG, "WakeLock acquired for MIUI TV keep-alive");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to acquire WakeLock: " + e.getMessage());
        }
    }
    
    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
                Log.d(TAG, "WakeLock released");
            } catch (Exception e) {
                Log.e(TAG, "Failed to release WakeLock: " + e.getMessage());
            }
        }
    }
    
    private boolean isInMIUIWhitelist() {
        try {
            String packageName = getPackageName();
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    boolean isIgnoring = pm.isIgnoringBatteryOptimizations(packageName);
                    Log.d(TAG, "Battery optimization whitelist status: " + isIgnoring);
                    return isIgnoring;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking MIUI whitelist: " + e.getMessage());
        }
        return false;
    }
    
    private void checkMIUIWhitelist() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean guideShown = prefs.getBoolean(KEY_WHITELIST_SHOWN, false);
        
        if (!isInMIUIWhitelist()) {
            Log.w(TAG, "App not in MIUI whitelist, service may be killed");
            
            if (!guideShown) {
                showWhitelistGuide();
                prefs.edit().putBoolean(KEY_WHITELIST_SHOWN, true).apply();
            }
        } else {
            Log.d(TAG, "App is in MIUI whitelist");
        }
    }
    
    private void showWhitelistGuide() {
        Log.d(TAG, "Showing MIUI whitelist guide");
        
        Intent intent = new Intent();
        String packageName = getPackageName();
        
        try {
            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Log.d(TAG, "Opened battery optimization settings");
            return;
        } catch (Exception e) {
            Log.w(TAG, "Failed to open battery optimization settings: " + e.getMessage());
        }
        
        try {
            intent = new Intent("miui.intent.action.OP_AUTO_START");
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Log.d(TAG, "Opened MIUI auto-start settings");
            return;
        } catch (Exception e) {
            Log.w(TAG, "Failed to open MIUI auto-start settings: " + e.getMessage());
        }
        
        try {
            intent = new Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST");
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Log.d(TAG, "Opened MIUI power saver settings");
        } catch (Exception e) {
            Log.w(TAG, "Failed to open MIUI power saver settings: " + e.getMessage());
        }
    }
    
    private boolean hasMIUIBackgroundPermission() {
        try {
            String packageName = getPackageName();
            
            Class<?> c = Class.forName("android.app.AppOpsManager");
            java.lang.reflect.Method checkOp = c.getMethod("checkOpNoThrow", int.class, int.class, String.class);
            java.lang.reflect.Method opRunInBackground = c.getField("OP_RUN_IN_BACKGROUND").getInt(null);
            
            Object appOps = getSystemService(Context.APP_OPS_SERVICE);
            int result = (Integer) checkOp.invoke(appOps, opRunInBackground, android.os.Process.myUid(), packageName);
            
            return result == 0;
        } catch (Exception e) {
            Log.d(TAG, "Cannot check MIUI background permission: " + e.getMessage());
            return true;
        }
    }
    
    private void checkMIUIBackgroundPermission() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean guideShown = prefs.getBoolean(KEY_PERMISSION_SHOWN, false);
        
        if (!hasMIUIBackgroundPermission()) {
            Log.w(TAG, "MIUI background permission not granted");
            
            if (!guideShown) {
                showBackgroundPermissionGuide();
                prefs.edit().putBoolean(KEY_PERMISSION_SHOWN, true).apply();
            }
        } else {
            Log.d(TAG, "MIUI background permission granted");
        }
    }
    
    private void showBackgroundPermissionGuide() {
        Log.d(TAG, "Showing MIUI background permission guide");
        
        Intent intent = new Intent();
        
        try {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Log.d(TAG, "Opened app details settings");
            return;
        } catch (Exception e) {
            Log.w(TAG, "Failed to open app details: " + e.getMessage());
        }
        
        try {
            intent = new Intent("miui.intent.action.APP_PERM_EDITOR");
            intent.putExtra("extra_pkgname", getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Log.d(TAG, "Opened MIUI permission editor");
        } catch (Exception e) {
            Log.w(TAG, "Failed to open MIUI permission editor: " + e.getMessage());
        }
    }
    
    private void setupMIUIRestartMechanism() {
        restartRunnable = new Runnable() {
            @Override
            public void run() {
                if (isMIUITV && isRunning) {
                    Log.d(TAG, "MIUI TV periodic restart check");
                    restartServiceIfNeeded();
                    restartHandler.postDelayed(this, 60000);
                }
            }
        };
        restartHandler.postDelayed(restartRunnable, 60000);
    }
    
    private void restartServiceIfNeeded() {
        if (!isRunning) {
            Log.w(TAG, "Service not running, attempting restart");
            restartService();
        }
    }
    
    private void restartService() {
        if (restartAttempts >= MAX_RESTART_ATTEMPTS) {
            Log.e(TAG, "Max restart attempts reached, stopping restart");
            return;
        }
        
        restartAttempts++;
        Log.d(TAG, "Restarting service, attempt " + restartAttempts + "/" + MAX_RESTART_ATTEMPTS);
        
        try {
            isRunning = false;
            
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startServer(currentServerPort);
                    restartAttempts = 0;
                    Log.d(TAG, "Service restarted successfully");
                }
            }, RESTART_DELAY);
        } catch (Exception e) {
            Log.e(TAG, "Error during service restart: " + e.getMessage());
        }
    }
    
    private void startHeartbeat() {
        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                performHeartbeat();
                heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL);
            }
        };
        heartbeatHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL);
        
        Log.d(TAG, "Heartbeat started with interval: " + HEARTBEAT_INTERVAL + "ms");
    }
    
    private void stopHeartbeat() {
        if (heartbeatHandler != null && heartbeatRunnable != null) {
            heartbeatHandler.removeCallbacks(heartbeatRunnable);
            Log.d(TAG, "Heartbeat stopped");
        }
    }
    
    private void performHeartbeat() {
        lastHeartbeatTime = System.currentTimeMillis();
        
        boolean serverHealthy = checkServerHealth();
        boolean serviceHealthy = checkServiceHealth();
        
        Log.d(TAG, "Heartbeat check - Server: " + serverHealthy + ", Service: " + serviceHealthy);
        
        if (!serverHealthy || !serviceHealthy) {
            Log.w(TAG, "Health check failed, attempting recovery");
            recoverService();
        }
        
        if (isMIUITV) {
            ensureForegroundState();
        }
    }
    
    private boolean checkServerHealth() {
        if (serverSocket == null || serverSocket.isClosed()) {
            Log.w(TAG, "Server socket is null or closed");
            return false;
        }
        
        try {
            if (serverSocket.getLocalPort() != currentServerPort) {
                Log.w(TAG, "Server port mismatch");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking server socket: " + e.getMessage());
            return false;
        }
        
        return true;
    }
    
    private boolean checkServiceHealth() {
        if (!isRunning) {
            Log.w(TAG, "Service isRunning flag is false");
            return false;
        }
        
        if (isMIUITV && wakeLock != null && !wakeLock.isHeld()) {
            Log.w(TAG, "WakeLock not held on MIUI TV");
            return false;
        }
        
        return true;
    }
    
    private void recoverService() {
        Log.d(TAG, "Starting service recovery");
        
        if (!checkServerHealth()) {
            Log.d(TAG, "Recovering server socket");
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error closing old socket: " + e.getMessage());
            }
            
            startServer(currentServerPort);
        }
        
        if (isMIUITV) {
            if (wakeLock == null || !wakeLock.isHeld()) {
                acquireWakeLock();
            }
            
            ensureForegroundState();
        }
        
        Log.d(TAG, "Service recovery completed");
    }
    
    private void ensureForegroundState() {
        try {
            Notification notification = createNotification();
            startForeground(NOTIFICATION_ID, notification);
            Log.d(TAG, "Ensured foreground state");
        } catch (Exception e) {
            Log.e(TAG, "Error ensuring foreground state: " + e.getMessage());
        }
    }
    
    private void showFloatingWindow(String text) {
        Log.d(TAG, "showFloatingWindow called with text: " + text);
        if (isFloatingWindowBound && floatingWindowService != null) {
            Log.d(TAG, "FloatingWindowService is bound, calling updateText and showWindow");
            floatingWindowService.updateText(text);
            floatingWindowService.showWindow();
        } else {
            Log.w(TAG, "FloatingWindowService not bound, cannot show window");
        }
    }
    
    private void hideFloatingWindow() {
        Log.d(TAG, "hideFloatingWindow called");
        if (isFloatingWindowBound && floatingWindowService != null) {
            Log.d(TAG, "FloatingWindowService is bound, calling hideWindow");
            floatingWindowService.hideWindow();
        } else {
            Log.w(TAG, "FloatingWindowService not bound, cannot hide window");
        }
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Post Tell Me Service",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Background service for Post Tell Me");
            channel.setShowBadge(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private String getDeviceIpAddress() {
        String ipAddress = "127.0.0.1";
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }
                
                java.util.Enumeration<java.net.InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress inetAddress = addresses.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress.getHostAddress().indexOf(':') < 0) {
                        ipAddress = inetAddress.getHostAddress();
                        if (networkInterface.getName().toLowerCase().contains("wlan") ||
                            networkInterface.getName().toLowerCase().contains("wifi")) {
                            return ipAddress;
                        }
                    }
                }
            }
        } catch (java.net.SocketException e) {
            Log.e(TAG, "Error getting IP address", e);
        }
        return ipAddress;
    }
    
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            notificationIntent, 
            PendingIntent.FLAG_IMMUTABLE
        );
        
        String ipAddress = getDeviceIpAddress();
        String contentText = ipAddress + ":" + currentServerPort;
        
        if (isMIUITV) {
            contentText += " [MIUI TV]";
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("post tell me")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
        } else {
            return new Notification.Builder(this)
                .setContentTitle("post tell me")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
        }
    }
    
    public void updateNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification());
        }
    }
    
    private void startServer() {
        startServer(currentServerPort);
    }
    
    private void startServer(int port) {
        isRunning = true;
        currentServerPort = port;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(currentServerPort);
                    Log.d(TAG, "Server started on port " + currentServerPort);
                    
                    while (isRunning) {
                        try {
                            Socket socket = serverSocket.accept();
                            Log.d(TAG, "Client connected");
                            new ServerAsyncTask().execute(socket);
                        } catch (IOException e) {
                            if (isRunning) {
                                Log.e(TAG, "Error accepting client connection", e);
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error starting server", e);
                    if (isMIUITV) {
                        Log.d(TAG, "MIUI TV: Scheduling server restart due to error");
                        mainHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                restartService();
                            }
                        }, RESTART_DELAY);
                    }
                }
            }
        }).start();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        
        if (intent != null && intent.hasExtra("port")) {
            int newPort = intent.getIntExtra("port", currentServerPort);
            if (newPort != currentServerPort) {
                Log.d(TAG, "Changing port from " + currentServerPort + " to " + newPort);
                
                isRunning = false;
                if (serverSocket != null) {
                    try {
                        serverSocket.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing server socket", e);
                    }
                }
                
                currentServerPort = newPort;
                startServer(currentServerPort);
                
                updateNotification();
            }
        }
        
        if (isMIUITV) {
            ensureForegroundState();
            
            if (wakeLock == null || !wakeLock.isHeld()) {
                acquireWakeLock();
            }
        }
        
        return START_STICKY;
    }
    
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "Task removed, restarting service");
        
        if (isMIUITV) {
            handleMIUITVTaskRemoved(rootIntent);
        } else {
            handleNormalTaskRemoved(rootIntent);
        }
        
        super.onTaskRemoved(rootIntent);
    }
    
    private void handleMIUITVTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "MIUI TV: Task removed, applying special restart strategy");
        
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
            .putLong("last_task_removed", System.currentTimeMillis())
            .putInt("restart_attempts", restartAttempts + 1)
            .apply();
        
        Intent restartIntent = new Intent(getApplicationContext(), ServerService.class);
        restartIntent.setPackage(getPackageName());
        restartIntent.putExtra("port", currentServerPort);
        
        PendingIntent restartPendingIntent = PendingIntent.getService(
            getApplicationContext(),
            1002,
            restartIntent,
            PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );
        
        try {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                long triggerAt = System.currentTimeMillis() + RESTART_DELAY;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        android.app.AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        restartPendingIntent
                    );
                } else {
                    alarmManager.setExact(
                        android.app.AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        restartPendingIntent
                    );
                }
                Log.d(TAG, "MIUI TV: Scheduled service restart via AlarmManager");
            }
        } catch (Exception e) {
            Log.e(TAG, "MIUI TV: Failed to schedule restart via AlarmManager: " + e.getMessage());
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent);
        } else {
            startService(restartIntent);
        }
        
        Log.d(TAG, "MIUI TV: Direct service restart initiated");
    }
    
    private void handleNormalTaskRemoved(Intent rootIntent) {
        Intent restartIntent = new Intent(getApplicationContext(), ServerService.class);
        restartIntent.setPackage(getPackageName());
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent);
        } else {
            startService(restartIntent);
        }
    }
    
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Log.d(TAG, "onTrimMemory called with level: " + level);
        
        switch (level) {
            case TRIM_MEMORY_RUNNING_LOW:
            case TRIM_MEMORY_RUNNING_CRITICAL:
                Log.w(TAG, "Memory pressure detected, but keeping service running");
                break;
            case TRIM_MEMORY_UI_HIDDEN:
                Log.d(TAG, "UI hidden, but service continues");
                break;
            case TRIM_MEMORY_MODERATE:
            case TRIM_MEMORY_COMPLETE:
                Log.w(TAG, "Severe memory pressure, but keeping essential components");
                break;
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
        
        stopHeartbeat();
        
        if (restartHandler != null && restartRunnable != null) {
            restartHandler.removeCallbacks(restartRunnable);
        }
        
        releaseWakeLock();
        
        isRunning = false;
        
        if (isMIUITV) {
            Log.d(TAG, "MIUI TV: Service being destroyed, scheduling restart");
            scheduleMIUITVRestart();
        }
        
        if (isFloatingWindowBound) {
            unbindService(floatingWindowConnection);
            isFloatingWindowBound = false;
        }
        
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing server socket", e);
            }
        }
        
        if (ttsEngineManager != null) {
            ttsEngineManager.shutdown();
        }
    }
    
    private void scheduleMIUITVRestart() {
        Intent restartIntent = new Intent(getApplicationContext(), ServerService.class);
        restartIntent.setPackage(getPackageName());
        restartIntent.putExtra("port", currentServerPort);
        
        PendingIntent restartPendingIntent = PendingIntent.getService(
            getApplicationContext(),
            1003,
            restartIntent,
            PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );
        
        try {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                long triggerAt = System.currentTimeMillis() + RESTART_DELAY;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        android.app.AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        restartPendingIntent
                    );
                } else {
                    alarmManager.setExact(
                        android.app.AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        restartPendingIntent
                    );
                }
                Log.d(TAG, "MIUI TV: Scheduled restart after destroy");
            }
        } catch (Exception e) {
            Log.e(TAG, "MIUI TV: Failed to schedule restart: " + e.getMessage());
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    public boolean isMIUITVDevice() {
        return isMIUITV;
    }
    
    public boolean isInWhitelist() {
        return isInMIUIWhitelist();
    }
    
    public boolean hasBackgroundPermission() {
        return hasMIUIBackgroundPermission();
    }
    
    public void requestWhitelist() {
        showWhitelistGuide();
    }
    
    public void requestBackgroundPermission() {
        showBackgroundPermissionGuide();
    }
    
    public long getLastHeartbeatTime() {
        return lastHeartbeatTime;
    }
    
    private class ServerAsyncTask extends AsyncTask<Socket, Void, String> {
        @Override
        protected String doInBackground(Socket... params) {
            String result = null;
            Socket mySocket = params[0];
            try {
                InputStream is = mySocket.getInputStream();
                OutputStream os = mySocket.getOutputStream();
                
                HttpRequestInfo requestInfo = parseHttpRequest(is);
                
                if (requestInfo.isPost && requestInfo.contentLength > 0) {
                    result = readPostPayload(is, requestInfo.contentLength);
                }
                
                sendHttpResponse(os);
                
                mySocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error handling client connection", e);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error", e);
            }
            return result;
        }
        
        @Override
        protected void onPostExecute(String s) {
            if (s != null) {
                Log.d(TAG, "Received POST payload: " + s);
                
                try {
                    JSONObject jsonObject = new JSONObject(s);
                    if (jsonObject.has("tts") && jsonObject.getBoolean("tts")) {
                        if (jsonObject.has("txt")) {
                            String text = jsonObject.getString("txt");
                            if (!text.isEmpty() && ttsEngineManager != null && ttsEngineManager.isInitialized()) {
                                int volume = 40;
                                if (jsonObject.has("volume")) {
                                    try {
                                        volume = jsonObject.getInt("volume");
                                        volume = Math.max(0, Math.min(100, volume));
                                    } catch (Exception e) {
                                    }
                                }
                                
                                String channel = "stereo";
                                if (jsonObject.has("channel")) {
                                    channel = jsonObject.getString("channel").toLowerCase();
                                    if (!channel.equals("left") && !channel.equals("right") && !channel.equals("stereo")) {
                                        channel = "stereo";
                                    }
                                }
                                
                                float volumeFloat = volume / 100.0f;
                                
                                float pan = 0.0f;
                                switch (channel) {
                                    case "left":
                                        pan = -1.0f;
                                        break;
                                    case "right":
                                        pan = 1.0f;
                                        break;
                                    default:
                                        pan = 0.0f;
                                        break;
                                }
                                
                                String utteranceId = "tts_" + System.currentTimeMillis();
                                
                                showFloatingWindow(text);
                                
                                ttsEngineManager.speak(text, volumeFloat, pan, utteranceId);
                            } else if (!text.isEmpty() && ttsEngineManager != null && !ttsEngineManager.isInitialized()) {
                                Log.w(TAG, "TTS not initialized yet, attempting reinitialize");
                                ttsEngineManager.reinitializeWithRetry();
                            }
                        }
                    }
                } catch (JSONException e) {
                    Log.d(TAG, "Received non-JSON payload");
                }
            }
        }
        
        private HttpRequestInfo parseHttpRequest(InputStream is) throws IOException {
            HttpRequestInfo info = new HttpRequestInfo();
            StringBuilder headerLine = new StringBuilder();
            int b;
            boolean firstLine = true;
            
            while ((b = is.read()) != -1) {
                if (b == '\r') {
                    int next = is.read();
                    if (next == '\n') {
                        String line = headerLine.toString();
                        headerLine.setLength(0);
                        
                        if (line.isEmpty()) {
                            break;
                        }
                        
                        if (firstLine) {
                            if (line.startsWith("POST ")) {
                                info.isPost = true;
                            }
                            firstLine = false;
                        } else if (line.toLowerCase().startsWith("content-length:")) {
                            try {
                                info.contentLength = Integer.parseInt(line.substring(15).trim());
                            } catch (NumberFormatException e) {
                                info.contentLength = 0;
                            }
                        }
                    } else if (next != -1) {
                        headerLine.append('\r');
                        headerLine.append((char) next);
                    }
                } else {
                    headerLine.append((char) b);
                }
            }
            
            return info;
        }
        
        private String readPostPayload(InputStream is, int contentLength) throws IOException {
            if (contentLength <= 0) {
                return "";
            }
            
            byte[] buffer = new byte[contentLength];
            int totalRead = 0;
            
            while (totalRead < contentLength) {
                int read = is.read(buffer, totalRead, contentLength - totalRead);
                if (read == -1) break;
                totalRead += read;
            }
            
            return new String(buffer, 0, totalRead, "UTF-8");
        }
        
        private void sendHttpResponse(OutputStream os) throws IOException {
            String response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/plain; charset=UTF-8\r\n" +
                    "Content-Length: 0\r\n" +
                    "Connection: close\r\n" +
                    "\r\n";
            os.write(response.getBytes("UTF-8"));
            os.flush();
        }
        
        private class HttpRequestInfo {
            boolean isPost = false;
            int contentLength = 0;
        }
    }
}
