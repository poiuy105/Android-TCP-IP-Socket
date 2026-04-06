package com.example.server;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

public class MiuiTvSettingsActivity extends Activity {
    private static final String TAG = "MiuiTvSettingsActivity";
    
    private static final int REQUEST_CODE_OVERLAY_PERMISSION = 1001;
    private static final int REQUEST_CODE_TTS_SETTINGS = 1002;
    private static final int REQUEST_CODE_BATTERY_OPTIMIZATION = 1003;
    
    private TextView tvDeviceInfo;
    private TextView tvKeepAliveStatus;
    private TextView tvOverlayStatus;
    private TextView tvTtsStatus;
    
    private Button btnKeepAliveSettings;
    private Button btnOverlaySettings;
    private Button btnTtsSettings;
    private Button btnTtsTest;
    private Button btnRefresh;
    private Button btnClose;
    
    private ScrollView scrollView;
    private View lastFocusedView;
    
    private TtsEngineManager ttsEngineManager;
    private MiuiTvDetector.MiuiTvInfo miuiTvInfo;
    private Handler mainHandler;
    
    private boolean isMiuiTv = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.activity_miui_tv_settings);
        
        mainHandler = new Handler(Looper.getMainLooper());
        
        initViews();
        initTtsManager();
        loadDeviceInfo();
        checkAllPermissions();
        setupButtonListeners();
        setupFocusNavigation();
    }
    
    private void initViews() {
        tvDeviceInfo = (TextView) findViewById(R.id.tv_device_info);
        tvKeepAliveStatus = (TextView) findViewById(R.id.tv_keep_alive_status);
        tvOverlayStatus = (TextView) findViewById(R.id.tv_overlay_status);
        tvTtsStatus = (TextView) findViewById(R.id.tv_tts_status);
        
        btnKeepAliveSettings = (Button) findViewById(R.id.btn_keep_alive_settings);
        btnOverlaySettings = (Button) findViewById(R.id.btn_overlay_settings);
        btnTtsSettings = (Button) findViewById(R.id.btn_tts_settings);
        btnTtsTest = (Button) findViewById(R.id.btn_tts_test);
        btnRefresh = (Button) findViewById(R.id.btn_refresh);
        btnClose = (Button) findViewById(R.id.btn_close);
        
        scrollView = (ScrollView) findViewById(R.id.scroll_view);
    }
    
    private void initTtsManager() {
        ttsEngineManager = new TtsEngineManager(this);
        ttsEngineManager.setInitListener(new TtsEngineManager.TtsInitListener() {
            @Override
            public void onInitSuccess(TtsEngineManager.TtsEngineInfo engine) {
                Log.d(TAG, "TTS initialized: " + engine.name);
                updateTtsStatus();
            }
            
            @Override
            public void onInitFailed(int error, String message) {
                Log.e(TAG, "TTS init failed: " + message);
                updateTtsStatus();
            }
        });
        
        ttsEngineManager.setStateListener(new TtsEngineManager.TtsStateListener() {
            @Override
            public void onStateChanged(TTSState oldState, TTSState newState) {
                Log.d(TAG, "TTS state: " + oldState + " -> " + newState);
            }
            
            @Override
            public void onSpeakStart(String utteranceId) {
                showToast("TTS测试开始");
            }
            
            @Override
            public void onSpeakDone(String utteranceId) {
                showToast("TTS测试完成");
            }
        });
        
        ttsEngineManager.setErrorListener(new TtsEngineManager.TtsErrorListener() {
            @Override
            public void onError(String utteranceId, int errorCode, String errorMessage) {
                showToast("TTS错误: " + errorMessage);
            }
            
            @Override
            public void onEngineError(TtsEngineManager.TtsEngineInfo engine, int errorCode, String errorMessage) {
                showToast("TTS引擎错误: " + errorMessage);
            }
        });
    }
    
    private void loadDeviceInfo() {
        miuiTvInfo = MiuiTvDetector.getMiuiTvInfo(this);
        isMiuiTv = miuiTvInfo.isMiuiTv;
        
        StringBuilder info = new StringBuilder();
        info.append("═══════ 设备信息 ═══════\n");
        info.append("制造商: ").append(miuiTvInfo.manufacturer).append("\n");
        info.append("品牌: ").append(miuiTvInfo.brand).append("\n");
        info.append("型号: ").append(miuiTvInfo.model).append("\n");
        info.append("设备: ").append(miuiTvInfo.device).append("\n");
        info.append("SDK版本: ").append(miuiTvInfo.sdkVersion).append("\n");
        info.append("\n═══════ MIUI信息 ═══════\n");
        info.append("MIUI设备: ").append(miuiTvInfo.isMiuiDevice ? "是" : "否").append("\n");
        info.append("TV设备: ").append(miuiTvInfo.isTvDevice ? "是" : "否").append("\n");
        info.append("MIUI TV: ").append(isMiuiTv ? "是" : "否").append("\n");
        
        if (miuiTvInfo.miuiVersion != null && !miuiTvInfo.miuiVersion.isEmpty()) {
            info.append("MIUI版本: ").append(miuiTvInfo.miuiVersion).append("\n");
        }
        if (miuiTvInfo.miuiTvVersion != null && !miuiTvInfo.miuiTvVersion.isEmpty()) {
            info.append("TV版本: ").append(miuiTvInfo.miuiTvVersion).append("\n");
        }
        info.append("目标版本(1.3.48): ").append(miuiTvInfo.isVersion1_3_48 ? "是" : "否").append("\n");
        
        tvDeviceInfo.setText(info.toString());
    }
    
    private void checkAllPermissions() {
        checkKeepAlivePermission();
        checkOverlayPermission();
        checkTtsStatus();
    }
    
    private void checkKeepAlivePermission() {
        boolean isIgnoring = isIgnoringBatteryOptimizations();
        boolean hasWakeLock = hasWakeLockPermission();
        
        StringBuilder status = new StringBuilder();
        status.append("电池优化白名单: ").append(isIgnoring ? "已添加 ✓" : "未添加 ✗").append("\n");
        status.append("唤醒锁权限: ").append(hasWakeLock ? "已授予 ✓" : "未授予 ✗").append("\n");
        
        if (!isIgnoring) {
            status.append("\n⚠️ 建议添加到电池优化白名单\n");
            status.append("以确保后台服务稳定运行");
        }
        
        tvKeepAliveStatus.setText(status.toString());
        
        if (isIgnoring) {
            btnKeepAliveSettings.setText("已添加白名单");
            btnKeepAliveSettings.setEnabled(false);
        } else {
            btnKeepAliveSettings.setText("添加到白名单");
            btnKeepAliveSettings.setEnabled(true);
        }
    }
    
    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            return pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        return true;
    }
    
    private boolean hasWakeLockPermission() {
        return checkSelfPermission(android.Manifest.permission.WAKE_LOCK) == PackageManager.PERMISSION_GRANTED;
    }
    
    private void checkOverlayPermission() {
        boolean hasOverlay = Settings.canDrawOverlays(this);
        
        StringBuilder status = new StringBuilder();
        status.append("悬浮窗权限: ").append(hasOverlay ? "已授予 ✓" : "未授予 ✗").append("\n");
        
        if (isMiuiTv) {
            status.append("\n📺 MIUI TV 特殊说明:\n");
            status.append("悬浮窗用于显示连接状态\n");
            status.append("请在系统设置中授予权限");
        }
        
        tvOverlayStatus.setText(status.toString());
        
        if (hasOverlay) {
            btnOverlaySettings.setText("已授予权限");
            btnOverlaySettings.setEnabled(false);
        } else {
            btnOverlaySettings.setText("申请悬浮窗权限");
            btnOverlaySettings.setEnabled(true);
        }
    }
    
    private void checkTtsStatus() {
        ttsEngineManager.initialize();
        updateTtsStatus();
    }
    
    private void updateTtsStatus() {
        StringBuilder status = new StringBuilder();
        
        TtsEngineManager.TtsEngineInfo currentEngine = ttsEngineManager.getCurrentEngine();
        List<TtsEngineManager.TtsEngineInfo> engines = ttsEngineManager.getAvailableEngines();
        
        status.append("TTS引擎状态: ").append(ttsEngineManager.isInitialized() ? "已初始化 ✓" : "未初始化 ✗").append("\n");
        
        if (currentEngine != null) {
            status.append("当前引擎: ").append(currentEngine.name).append("\n");
            status.append("支持中文: ").append(currentEngine.supportsChinese ? "是" : "否").append("\n");
            status.append("支持英文: ").append(currentEngine.supportsEnglish ? "是" : "否").append("\n");
        }
        
        status.append("\n可用引擎数量: ").append(engines.size()).append("\n");
        for (TtsEngineManager.TtsEngineInfo engine : engines) {
            status.append("• ").append(engine.name);
            if (engine.isDefault) {
                status.append(" (默认)");
            }
            status.append("\n");
        }
        
        if (isMiuiTv) {
            status.append("\n📺 MIUI TV 建议:\n");
            status.append("推荐使用小米语音引擎");
        }
        
        tvTtsStatus.setText(status.toString());
        
        btnTtsTest.setEnabled(ttsEngineManager.isInitialized());
    }
    
    private void setupButtonListeners() {
        btnKeepAliveSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openBatteryOptimizationSettings();
            }
        });
        
        btnOverlaySettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestOverlayPermission();
            }
        });
        
        btnTtsSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTtsSettings();
            }
        });
        
        btnTtsTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testTts();
            }
        });
        
        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshAll();
            }
        });
        
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
    
    private void setupFocusNavigation() {
        View.OnFocusChangeListener focusChangeListener = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    lastFocusedView = v;
                    v.setBackgroundColor(0x330000FF);
                } else {
                    v.setBackgroundColor(0x00000000);
                }
            }
        };
        
        btnKeepAliveSettings.setOnFocusChangeListener(focusChangeListener);
        btnOverlaySettings.setOnFocusChangeListener(focusChangeListener);
        btnTtsSettings.setOnFocusChangeListener(focusChangeListener);
        btnTtsTest.setOnFocusChangeListener(focusChangeListener);
        btnRefresh.setOnFocusChangeListener(focusChangeListener);
        btnClose.setOnFocusChangeListener(focusChangeListener);
        
        btnKeepAliveSettings.requestFocus();
    }
    
    private void openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            try {
                startActivityForResult(intent, REQUEST_CODE_BATTERY_OPTIMIZATION);
                showToast("请在设置中允许忽略电池优化");
            } catch (Exception e) {
                Log.e(TAG, "Failed to open battery optimization settings", e);
                openMiuiTvSecuritySettings();
            }
        } else {
            openMiuiTvSecuritySettings();
        }
    }
    
    private void openMiuiTvSecuritySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
            startActivity(intent);
            showToast("请在安全设置中配置自启动权限");
        } catch (Exception e) {
            Log.e(TAG, "Failed to open security settings", e);
            openAppSettings();
        }
    }
    
    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
        showToast("请在应用设置中配置权限");
    }
    
    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION);
                showToast("请授予悬浮窗权限");
            }
        }
    }
    
    private void openTtsSettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        try {
            startActivity(intent);
            showToast("请在无障碍设置中配置TTS");
        } catch (Exception e) {
            Log.e(TAG, "Failed to open accessibility settings", e);
            try {
                Intent ttsIntent = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(ttsIntent);
            } catch (Exception e2) {
                Log.e(TAG, "Failed to open TTS settings", e2);
                showToast("无法打开TTS设置");
            }
        }
    }
    
    private void testTts() {
        if (ttsEngineManager.isInitialized()) {
            String testText = "MIUI TV 设置引导测试成功";
            ttsEngineManager.speak(testText);
            showToast("正在测试TTS...");
        } else {
            showToast("TTS未初始化，请稍后再试");
            ttsEngineManager.initialize();
        }
    }
    
    private void refreshAll() {
        MiuiTvDetector.clearCache();
        loadDeviceInfo();
        checkAllPermissions();
        showToast("已刷新状态");
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        switch (requestCode) {
            case REQUEST_CODE_OVERLAY_PERMISSION:
                checkOverlayPermission();
                break;
            case REQUEST_CODE_BATTERY_OPTIMIZATION:
                checkKeepAlivePermission();
                break;
            case REQUEST_CODE_TTS_SETTINGS:
                ttsEngineManager.reinitialize();
                updateTtsStatus();
                break;
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        checkAllPermissions();
    }
    
    @Override
    protected void onDestroy() {
        if (ttsEngineManager != null) {
            ttsEngineManager.shutdown();
        }
        super.onDestroy();
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || 
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return super.onKeyDown(keyCode, event);
        }
        
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        
        return super.onKeyDown(keyCode, event);
    }
    
    private void showToast(final String message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MiuiTvSettingsActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
