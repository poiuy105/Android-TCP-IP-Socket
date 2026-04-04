package com.example.server;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class FloatingWindowService extends Service {
    private static final String TAG = "FloatingWindowService";
    
    private final IBinder binder = new LocalBinder();
    
    public class LocalBinder extends Binder {
        FloatingWindowService getService() {
            return FloatingWindowService.this;
        }
    }
    
    private WindowManager windowManager;
    private View floatingView;
    private TextView tvText;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "FloatingWindowService created");
        createFloatingWindow();
    }
    
    private void createFloatingWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        
        LayoutInflater inflater = LayoutInflater.from(this);
        floatingView = inflater.inflate(R.layout.floating_window, null);
        
        tvText = floatingView.findViewById(R.id.tv_text);
    }
    
    public void showWindow() {
        if (floatingView != null && floatingView.getWindowToken() == null) {
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                getWindowWidth(),
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O 
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
                    : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            );
            
            params.gravity = Gravity.TOP | Gravity.END;
            params.x = 16;
            params.y = 16;
            
            windowManager.addView(floatingView, params);
            Log.d(TAG, "Floating window shown");
        }
    }
    
    public void hideWindow() {
        if (floatingView != null && floatingView.getWindowToken() != null) {
            windowManager.removeView(floatingView);
            Log.d(TAG, "Floating window hidden");
        }
    }
    
    public void updateText(String text) {
        if (tvText != null) {
            tvText.setText(text);
        }
    }
    
    private int getWindowWidth() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        if (windowManager != null) {
            windowManager.getDefaultDisplay().getMetrics(displayMetrics);
            int screenWidth = displayMetrics.widthPixels;
            return screenWidth / 3;
        }
        return 300;
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
        hideWindow();
        Log.d(TAG, "FloatingWindowService destroyed");
    }
}