package com.example.server;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;

public class FloatingWindowService extends Service {
    private static final String TAG = "FloatingWindowService";
    
    private static final int REQUEST_CODE_OVERLAY_PERMISSION = 1001;
    private static final int LAYER_ADJUSTMENT_DELAY = 500;
    private static final int MAX_LAYER_ADJUSTMENT_ATTEMPTS = 3;
    
    private final IBinder binder = new LocalBinder();
    
    public class LocalBinder extends Binder {
        FloatingWindowService getService() {
            return FloatingWindowService.this;
        }
    }
    
    public interface PermissionCallback {
        void onPermissionGranted();
        void onPermissionDenied();
        void onPermissionGuideNeeded(Intent intent);
    }
    
    private WindowManager windowManager;
    private View floatingView;
    private TextView tvText;
    private boolean isTvDevice = false;
    private boolean isMiuiDevice = false;
    private boolean isMiuiTv = false;
    private String miuiVersion = "";
    private PermissionCallback permissionCallback;
    private Handler handler;
    private WindowManager.LayoutParams currentParams;
    private int layerAdjustmentAttempts = 0;
    private Runnable layerAdjustmentRunnable;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "FloatingWindowService created");
        
        handler = new Handler(Looper.getMainLooper());
        
        detectDeviceInfo();
        Log.d(TAG, "Device type: " + (isTvDevice ? "TV" : "Non-TV") + 
              ", MIUI: " + isMiuiDevice + ", MIUI TV: " + isMiuiTv +
              ", MIUI Version: " + miuiVersion);
        
        createFloatingWindow();
    }
    
    private void detectDeviceInfo() {
        isTvDevice = isTvDevice();
        isMiuiDevice = isMiuiDevice();
        miuiVersion = getMiuiVersion();
        isMiuiTv = isMiuiDevice && isTvDevice;
    }
    
    private boolean isTvDevice() {
        PackageManager pm = getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
               pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
               pm.hasSystemFeature("android.hardware.type.tv");
    }
    
    private boolean isMiuiDevice() {
        try {
            Class<?> clazz = Class.forName("android.os.SystemProperties");
            Method method = clazz.getMethod("get", String.class);
            String miuiVersion = (String) method.invoke(null, "ro.miui.ui.version.name");
            return miuiVersion != null && !miuiVersion.isEmpty();
        } catch (Exception e) {
            Log.d(TAG, "Not a MIUI device: " + e.getMessage());
            return false;
        }
    }
    
    private String getMiuiVersion() {
        try {
            Class<?> clazz = Class.forName("android.os.SystemProperties");
            Method method = clazz.getMethod("get", String.class);
            return (String) method.invoke(null, "ro.miui.ui.version.name");
        } catch (Exception e) {
            return "";
        }
    }
    
    private boolean isMiuiTvSpecialVersion() {
        if (!isMiuiTv) return false;
        try {
            String version = miuiVersion.toLowerCase();
            return version.contains("tv") || version.startsWith("2");
        } catch (Exception e) {
            return false;
        }
    }
    
    public boolean hasOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }
    
    public boolean checkAndRequestOverlayPermission(PermissionCallback callback) {
        this.permissionCallback = callback;
        
        if (hasOverlayPermission()) {
            Log.d(TAG, "Overlay permission already granted");
            if (callback != null) {
                callback.onPermissionGranted();
            }
            return true;
        }
        
        Log.d(TAG, "Overlay permission not granted, requesting...");
        
        if (isMiuiTv) {
            return requestMiuiTvOverlayPermission(callback);
        } else if (isMiuiDevice) {
            return requestMiuiOverlayPermission(callback);
        } else {
            return requestStandardOverlayPermission(callback);
        }
    }
    
    private boolean requestMiuiTvOverlayPermission(PermissionCallback callback) {
        Log.d(TAG, "Requesting MIUI TV overlay permission");
        
        Intent intent = getMiuiTvPermissionIntent();
        if (intent != null) {
            if (callback != null) {
                callback.onPermissionGuideNeeded(intent);
            }
            return false;
        }
        
        return requestStandardOverlayPermission(callback);
    }
    
    private Intent getMiuiTvPermissionIntent() {
        Intent intent = null;
        
        try {
            intent = new Intent("miui.intent.action.APP_PERM_EDITOR");
            intent.setClassName("com.miui.securitycenter", 
                "com.miui.permcenter.permissions.PermissionsEditorActivity");
            intent.putExtra("extra_pkgname", getPackageName());
            Log.d(TAG, "Using MIUI Security Center permission editor");
            return intent;
        } catch (Exception e) {
            Log.d(TAG, "MIUI Security Center not available: " + e.getMessage());
        }
        
        try {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", getPackageName(), null));
            Log.d(TAG, "Using standard app details settings");
            return intent;
        } catch (Exception e) {
            Log.d(TAG, "Standard settings not available: " + e.getMessage());
        }
        
        try {
            intent = new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
            Log.d(TAG, "Using manage applications settings");
            return intent;
        } catch (Exception e) {
            Log.d(TAG, "Manage applications settings not available: " + e.getMessage());
        }
        
        return null;
    }
    
    private boolean requestMiuiOverlayPermission(PermissionCallback callback) {
        Log.d(TAG, "Requesting MIUI overlay permission");
        
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + getPackageName()));
        
        if (callback != null) {
            callback.onPermissionGuideNeeded(intent);
        }
        return false;
    }
    
    private boolean requestStandardOverlayPermission(PermissionCallback callback) {
        Log.d(TAG, "Requesting standard overlay permission");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
            
            if (callback != null) {
                callback.onPermissionGuideNeeded(intent);
            }
            return false;
        }
        
        if (callback != null) {
            callback.onPermissionGranted();
        }
        return true;
    }
    
    public void handlePermissionResult(boolean granted) {
        if (granted && permissionCallback != null) {
            permissionCallback.onPermissionGranted();
        } else if (!granted && permissionCallback != null) {
            permissionCallback.onPermissionDenied();
        }
    }
    
    private void createFloatingWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        LayoutInflater inflater = LayoutInflater.from(this);
        floatingView = inflater.inflate(R.layout.floating_window, null);
        
        tvText = floatingView.findViewById(R.id.tv_text);
    }
    
    public void showWindow() {
        Log.d(TAG, "showWindow called");
        
        if (!hasOverlayPermission()) {
            Log.w(TAG, "No overlay permission, cannot show window");
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show();
            return;
        }
        
        if (floatingView != null && floatingView.getWindowToken() == null) {
            WindowManager.LayoutParams params = createOptimizedLayoutParams();
            currentParams = params;
            
            try {
                windowManager.addView(floatingView, params);
                Log.d(TAG, "Floating window shown successfully (MIUI TV: " + isMiuiTv + ")");
                
                if (isMiuiTv) {
                    startLayerAdjustment();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to show floating window: " + e.getMessage());
                handleWindowAddFailure(e);
            }
        } else {
            Log.w(TAG, "Cannot show window - view is null or already shown");
        }
    }
    
    private WindowManager.LayoutParams createOptimizedLayoutParams() {
        int windowType = getWindowType();
        int windowFlags = getWindowFlags();
        
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            getWindowWidth(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            windowFlags,
            PixelFormat.TRANSLUCENT
        );
        
        params.gravity = Gravity.TOP | Gravity.END;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
        
        if (isMiuiTv) {
            applyMiuiTvOptimizations(params);
        } else if (isMiuiDevice) {
            applyMiuiOptimizations(params);
        } else if (isTvDevice) {
            applyTvOptimizations(params);
        } else {
            applyDefaultOptimizations(params);
        }
        
        return params;
    }
    
    private int getWindowType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            return WindowManager.LayoutParams.TYPE_PHONE;
        }
    }
    
    private int getWindowFlags() {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                   WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                   WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        
        if (isMiuiTv) {
            flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        }
        
        if (isTvDevice) {
            flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        }
        
        return flags;
    }
    
    private void applyMiuiTvOptimizations(WindowManager.LayoutParams params) {
        Log.d(TAG, "Applying MIUI TV optimizations");
        
        DisplayMetrics displayMetrics = getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;
        
        params.x = getMiuiTvPositionX(screenWidth);
        params.y = getMiuiTvPositionY(screenHeight);
        
        params.horizontalMargin = 0;
        params.verticalMargin = 0;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = 
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }
    
    private int getMiuiTvPositionX(int screenWidth) {
        int marginDp = 32;
        float density = getResources().getDisplayMetrics().density;
        int marginPx = (int) (marginDp * density);
        
        return marginPx;
    }
    
    private int getMiuiTvPositionY(int screenHeight) {
        int marginTopDp = 48;
        float density = getResources().getDisplayMetrics().density;
        int marginTopPx = (int) (marginTopDp * density);
        
        return marginTopPx;
    }
    
    private void applyMiuiOptimizations(WindowManager.LayoutParams params) {
        Log.d(TAG, "Applying MIUI optimizations");
        
        params.x = 16;
        params.y = 16;
    }
    
    private void applyTvOptimizations(WindowManager.LayoutParams params) {
        Log.d(TAG, "Applying TV optimizations");
        
        params.x = 48;
        params.y = 48;
    }
    
    private void applyDefaultOptimizations(WindowManager.LayoutParams params) {
        params.x = 16;
        params.y = 16;
    }
    
    private DisplayMetrics getDisplayMetrics() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        if (windowManager != null) {
            windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
        } else {
            displayMetrics = getResources().getDisplayMetrics();
        }
        return displayMetrics;
    }
    
    private void startLayerAdjustment() {
        Log.d(TAG, "Starting layer adjustment for MIUI TV");
        layerAdjustmentAttempts = 0;
        
        if (layerAdjustmentRunnable != null) {
            handler.removeCallbacks(layerAdjustmentRunnable);
        }
        
        layerAdjustmentRunnable = new Runnable() {
            @Override
            public void run() {
                if (floatingView != null && floatingView.getWindowToken() != null) {
                    adjustWindowLayer();
                    
                    layerAdjustmentAttempts++;
                    if (layerAdjustmentAttempts < MAX_LAYER_ADJUSTMENT_ATTEMPTS) {
                        handler.postDelayed(this, LAYER_ADJUSTMENT_DELAY);
                    }
                }
            }
        };
        
        handler.postDelayed(layerAdjustmentRunnable, LAYER_ADJUSTMENT_DELAY);
    }
    
    private void adjustWindowLayer() {
        if (currentParams == null || floatingView == null || 
            floatingView.getWindowToken() == null) {
            return;
        }
        
        Log.d(TAG, "Adjusting window layer for MIUI TV, attempt: " + layerAdjustmentAttempts);
        
        try {
            windowManager.updateViewLayout(floatingView, currentParams);
            Log.d(TAG, "Window layer adjusted successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to adjust window layer: " + e.getMessage());
        }
    }
    
    private void handleWindowAddFailure(Exception e) {
        String message = e.getMessage();
        
        if (message != null && message.contains("permission")) {
            Toast.makeText(this, "MIUI TV: 请在设置中开启悬浮窗权限", Toast.LENGTH_LONG).show();
        } else if (message != null && message.contains("type")) {
            Toast.makeText(this, "MIUI TV: 不支持的窗口类型", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "悬浮窗显示失败: " + message, Toast.LENGTH_LONG).show();
        }
    }
    
    public void bringToFront() {
        if (floatingView != null && floatingView.getWindowToken() != null && currentParams != null) {
            try {
                windowManager.removeView(floatingView);
                windowManager.addView(floatingView, currentParams);
                Log.d(TAG, "Window brought to front");
            } catch (Exception e) {
                Log.e(TAG, "Failed to bring window to front: " + e.getMessage());
            }
        }
    }
    
    public boolean isWindowVisible() {
        return floatingView != null && floatingView.getWindowToken() != null;
    }
    
    public void updatePosition(int x, int y) {
        if (floatingView != null && floatingView.getWindowToken() != null && currentParams != null) {
            currentParams.x = x;
            currentParams.y = y;
            try {
                windowManager.updateViewLayout(floatingView, currentParams);
                Log.d(TAG, "Window position updated: x=" + x + ", y=" + y);
            } catch (Exception e) {
                Log.e(TAG, "Failed to update window position: " + e.getMessage());
            }
        }
    }
    
    public void hideWindow() {
        Log.d(TAG, "hideWindow called");
        
        stopLayerAdjustment();
        
        if (floatingView != null && floatingView.getWindowToken() != null) {
            windowManager.removeView(floatingView);
            Log.d(TAG, "Floating window hidden successfully");
        } else {
            Log.w(TAG, "Cannot hide window - view is null or not shown");
        }
    }
    
    private void stopLayerAdjustment() {
        if (layerAdjustmentRunnable != null) {
            handler.removeCallbacks(layerAdjustmentRunnable);
            layerAdjustmentRunnable = null;
        }
        layerAdjustmentAttempts = 0;
    }
    
    public void updateText(String text) {
        if (tvText != null) {
            tvText.setText(text);
        }
    }
    
    private int getWindowWidth() {
        DisplayMetrics displayMetrics = getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;
        
        if (isMiuiTv) {
            return (int) (screenWidth * 0.6);
        } else if (isTvDevice) {
            return screenWidth * 2 / 3;
        } else {
            return screenWidth / 3;
        }
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopLayerAdjustment();
        hideWindow();
        Log.d(TAG, "FloatingWindowService destroyed");
    }
}
