package com.example.server;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
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
    private TextView tvStatus;
    private TextView tvText;
    private Button btnPause;
    private Button btnStop;
    private Button btnClose;
    
    private TTSState currentState = TTSState.IDLE;
    private String currentText = "";
    private boolean isPaused = false;
    
    private FloatingWindowListener listener;
    
    public interface FloatingWindowListener {
        void onPause();
        void onStop();
        void onClose();
    }
    
    public void setListener(FloatingWindowListener listener) {
        this.listener = listener;
    }
    
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
        
        tvStatus = floatingView.findViewById(R.id.tv_status);
        tvText = floatingView.findViewById(R.id.tv_text);
        btnPause = floatingView.findViewById(R.id.btn_pause);
        btnStop = floatingView.findViewById(R.id.btn_stop);
        btnClose = floatingView.findViewById(R.id.btn_close);
        
        // Set button listeners
        btnPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePause();
            }
        });
        
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onStop();
                }
                updateState(TTSState.IDLE);
            }
        });
        
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onClose();
                }
                hideWindow();
            }
        });
        
        // Make window draggable
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private boolean isDragging = false;
            
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = getLayoutParams().x;
                        initialY = getLayoutParams().y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        return true;
                        
                    case MotionEvent.ACTION_MOVE:
                        int deltaX = (int) (event.getRawX() - initialTouchX);
                        int deltaY = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(deltaX) > 5 || Math.abs(deltaY) > 5) {
                            isDragging = true;
                            WindowManager.LayoutParams params = getLayoutParams();
                            params.x = initialX + deltaX;
                            params.y = initialY + deltaY;
                            windowManager.updateViewLayout(floatingView, params);
                        }
                        return true;
                        
                    case MotionEvent.ACTION_UP:
                        return isDragging;
                }
                return false;
            }
        });
    }
    
    private WindowManager.LayoutParams getLayoutParams() {
        return (WindowManager.LayoutParams) floatingView.getLayoutParams();
    }
    
    public void showWindow() {
        if (floatingView != null && floatingView.getWindowToken() == null) {
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O 
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
                    : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
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
    
    public void updateState(TTSState state) {
        currentState = state;
        updateUI();
    }
    
    public void updateText(String text) {
        currentText = text;
        updateUI();
    }
    
    private void updateUI() {
        if (tvStatus != null && tvText != null) {
            // Update status text
            String statusText = "";
            switch (currentState) {
                case IDLE:
                    statusText = "空闲";
                    break;
                case PLAYING:
                    statusText = isPaused ? "已暂停" : "播放中...";
                    break;
                case PAUSED:
                    statusText = "已暂停";
                    break;
                case COMPLETED:
                    statusText = "已完成";
                    break;
                case LOADING:
                    statusText = "加载中...";
                    break;
                case ERROR:
                    statusText = "错误";
                    break;
            }
            tvStatus.setText(statusText);
            
            // Update text
            tvText.setText(currentText);
            
            // Update pause button text
            btnPause.setText(isPaused ? "继续" : "暂停");
        }
    }
    
    private void togglePause() {
        isPaused = !isPaused;
        if (listener != null) {
            if (isPaused) {
                listener.onPause();
                updateState(TTSState.PAUSED);
            } else {
                updateState(TTSState.PLAYING);
            }
        }
        updateUI();
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