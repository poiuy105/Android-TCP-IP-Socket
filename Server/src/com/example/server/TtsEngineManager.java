package com.example.server;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TtsEngineManager {
    private static final String TAG = "TtsEngineManager";
    
    private static final int DEFAULT_MAX_RETRY_COUNT = 3;
    private static final long DEFAULT_RETRY_INTERVAL_MS = 1000;
    private static final long DEFAULT_TEST_TIMEOUT_MS = 5000;
    
    private static final String MIUI_TV_TTS_ENGINE = "com.xiaomi.mibrain.speech";
    private static final String XIAOMI_TTS_ENGINE = "com.xiaomi.mibrain";
    private static final String GOOGLE_TTS_ENGINE = "com.google.android.tts";
    private static final String SAMSUNG_TTS_ENGINE = "com.samsung.SMT";
    private static final String DEFAULT_TTS_ENGINE = "com.android.tts";
    
    private Context context;
    private TextToSpeech textToSpeech;
    private TTSState currentState = TTSState.IDLE;
    private TtsEngineInfo currentEngine;
    private List<TtsEngineInfo> availableEngines = new ArrayList<>();
    private List<TtsEngineInfo> fallbackEngines = new ArrayList<>();
    
    private int retryCount = 0;
    private int maxRetryCount = DEFAULT_MAX_RETRY_COUNT;
    private long retryIntervalMs = DEFAULT_RETRY_INTERVAL_MS;
    private boolean isInitializing = false;
    
    private Handler mainHandler;
    private TtsInitListener initListener;
    private TtsStateListener stateListener;
    private TtsErrorListener errorListener;
    
    private boolean isMiuiTv = false;
    private String preferredEngineName = null;
    
    public interface TtsInitListener {
        void onInitSuccess(TtsEngineInfo engine);
        void onInitFailed(int error, String message);
    }
    
    public interface TtsStateListener {
        void onStateChanged(TTSState oldState, TTSState newState);
        void onSpeakStart(String utteranceId);
        void onSpeakDone(String utteranceId);
    }
    
    public interface TtsErrorListener {
        void onError(String utteranceId, int errorCode, String errorMessage);
        void onEngineError(TtsEngineInfo engine, int errorCode, String errorMessage);
    }
    
    public interface TtsTestListener {
        void onTestComplete(TtsTestResult result);
    }
    
    public static class TtsEngineInfo {
        public String name;
        public String packageName;
        public String label;
        public int priority;
        public boolean isDefault;
        public boolean isAvailable;
        public boolean supportsChinese;
        public boolean supportsEnglish;
        
        @Override
        public String toString() {
            return "TtsEngineInfo{" +
                   "name='" + name + '\'' +
                   ", packageName='" + packageName + '\'' +
                   ", label='" + label + '\'' +
                   ", priority=" + priority +
                   ", isDefault=" + isDefault +
                   ", isAvailable=" + isAvailable +
                   ", supportsChinese=" + supportsChinese +
                   ", supportsEnglish=" + supportsEnglish +
                   '}';
        }
    }
    
    public static class TtsTestResult {
        public boolean success;
        public String engineName;
        public String enginePackage;
        public long testDurationMs;
        public String errorMessage;
        public int errorCode;
        public boolean supportsChinese;
        public boolean supportsEnglish;
        
        @Override
        public String toString() {
            return "TtsTestResult{" +
                   "success=" + success +
                   ", engineName='" + engineName + '\'' +
                   ", enginePackage='" + enginePackage + '\'' +
                   ", testDurationMs=" + testDurationMs +
                   ", errorMessage='" + errorMessage + '\'' +
                   ", errorCode=" + errorCode +
                   ", supportsChinese=" + supportsChinese +
                   ", supportsEnglish=" + supportsEnglish +
                   '}';
        }
    }
    
    public TtsEngineManager(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.isMiuiTv = MiuiTvDetector.isMiuiTv(context);
        detectAvailableEngines();
    }
    
    public void setMaxRetryCount(int maxRetryCount) {
        this.maxRetryCount = Math.max(1, maxRetryCount);
    }
    
    public void setRetryIntervalMs(long retryIntervalMs) {
        this.retryIntervalMs = Math.max(100, retryIntervalMs);
    }
    
    public void setInitListener(TtsInitListener listener) {
        this.initListener = listener;
    }
    
    public void setStateListener(TtsStateListener listener) {
        this.stateListener = listener;
    }
    
    public void setErrorListener(TtsErrorListener listener) {
        this.errorListener = listener;
    }
    
    public void setPreferredEngine(String enginePackageName) {
        this.preferredEngineName = enginePackageName;
    }
    
    public TTSState getCurrentState() {
        return currentState;
    }
    
    public TtsEngineInfo getCurrentEngine() {
        return currentEngine;
    }
    
    public List<TtsEngineInfo> getAvailableEngines() {
        return new ArrayList<>(availableEngines);
    }
    
    public boolean isInitialized() {
        return textToSpeech != null && currentState != TTSState.IDLE && currentState != TTSState.ERROR;
    }
    
    private void detectAvailableEngines() {
        availableEngines.clear();
        
        Intent intent = new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE);
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> resolveInfos = pm.queryIntentServices(intent, PackageManager.MATCH_DEFAULT_ONLY);
        
        for (ResolveInfo resolveInfo : resolveInfos) {
            TtsEngineInfo engineInfo = new TtsEngineInfo();
            engineInfo.packageName = resolveInfo.serviceInfo.packageName;
            engineInfo.name = resolveInfo.loadLabel(pm).toString();
            engineInfo.label = engineInfo.name;
            engineInfo.isAvailable = true;
            engineInfo.priority = calculateEnginePriority(engineInfo.packageName);
            engineInfo.isDefault = isDefaultEngine(engineInfo.packageName);
            
            availableEngines.add(engineInfo);
            Log.d(TAG, "Detected TTS engine: " + engineInfo);
        }
        
        Collections.sort(availableEngines, new Comparator<TtsEngineInfo>() {
            @Override
            public int compare(TtsEngineInfo o1, TtsEngineInfo o2) {
                return Integer.compare(o2.priority, o1.priority);
            }
        });
        
        Log.d(TAG, "Total available engines: " + availableEngines.size());
    }
    
    private int calculateEnginePriority(String packageName) {
        if (packageName == null) {
            return 0;
        }
        
        if (isMiuiTv) {
            if (packageName.equals(MIUI_TV_TTS_ENGINE)) {
                return 100;
            }
            if (packageName.equals(XIAOMI_TTS_ENGINE)) {
                return 95;
            }
        }
        
        if (packageName.equals(GOOGLE_TTS_ENGINE)) {
            return 80;
        }
        
        if (packageName.equals(XIAOMI_TTS_ENGINE)) {
            return 75;
        }
        
        if (packageName.equals(SAMSUNG_TTS_ENGINE)) {
            return 60;
        }
        
        if (packageName.equals(DEFAULT_TTS_ENGINE)) {
            return 50;
        }
        
        return 30;
    }
    
    private boolean isDefaultEngine(String packageName) {
        if (textToSpeech != null) {
            String defaultEngine = textToSpeech.getDefaultEngine();
            return packageName != null && packageName.equals(defaultEngine);
        }
        return false;
    }
    
    public void initialize() {
        initialize(null);
    }
    
    public void initialize(String enginePackageName) {
        if (isInitializing) {
            Log.w(TAG, "TTS initialization already in progress");
            return;
        }
        
        isInitializing = true;
        retryCount = 0;
        
        TtsEngineInfo targetEngine = selectEngine(enginePackageName);
        
        if (targetEngine == null) {
            Log.e(TAG, "No suitable TTS engine found");
            isInitializing = false;
            notifyInitFailed(TextToSpeech.ERROR, "No suitable TTS engine found");
            return;
        }
        
        initializeWithEngine(targetEngine);
    }
    
    private TtsEngineInfo selectEngine(String preferredPackage) {
        if (preferredPackage != null && !preferredPackage.isEmpty()) {
            for (TtsEngineInfo engine : availableEngines) {
                if (preferredPackage.equals(engine.packageName)) {
                    Log.d(TAG, "Using preferred engine: " + engine.packageName);
                    return engine;
                }
            }
        }
        
        if (preferredEngineName != null && !preferredEngineName.isEmpty()) {
            for (TtsEngineInfo engine : availableEngines) {
                if (preferredEngineName.equals(engine.packageName)) {
                    Log.d(TAG, "Using configured preferred engine: " + engine.packageName);
                    return engine;
                }
            }
        }
        
        if (isMiuiTv) {
            for (TtsEngineInfo engine : availableEngines) {
                if (MIUI_TV_TTS_ENGINE.equals(engine.packageName) || 
                    XIAOMI_TTS_ENGINE.equals(engine.packageName)) {
                    Log.d(TAG, "Using MIUI TV recommended engine: " + engine.packageName);
                    return engine;
                }
            }
        }
        
        for (TtsEngineInfo engine : availableEngines) {
            if (engine.isDefault) {
                Log.d(TAG, "Using system default engine: " + engine.packageName);
                return engine;
            }
        }
        
        if (!availableEngines.isEmpty()) {
            TtsEngineInfo engine = availableEngines.get(0);
            Log.d(TAG, "Using highest priority engine: " + engine.packageName);
            return engine;
        }
        
        return null;
    }
    
    private void initializeWithEngine(final TtsEngineInfo engineInfo) {
        Log.d(TAG, "Initializing TTS with engine: " + engineInfo.packageName + 
              ", attempt: " + (retryCount + 1) + "/" + maxRetryCount);
        
        updateState(TTSState.LOADING);
        
        if (textToSpeech != null) {
            try {
                textToSpeech.stop();
                textToSpeech.shutdown();
            } catch (Exception e) {
                Log.w(TAG, "Error shutting down previous TTS instance: " + e.getMessage());
            }
            textToSpeech = null;
        }
        
        textToSpeech = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    Log.d(TAG, "TTS engine initialized successfully: " + engineInfo.packageName);
                    
                    boolean languageSet = setupLanguage();
                    
                    if (languageSet) {
                        setupUtteranceProgressListener();
                        currentEngine = engineInfo;
                        retryCount = 0;
                        isInitializing = false;
                        updateState(TTSState.IDLE);
                        notifyInitSuccess(engineInfo);
                    } else {
                        handleInitFailure(engineInfo, TextToSpeech.ERROR, "Failed to set language");
                    }
                } else {
                    handleInitFailure(engineInfo, status, "TTS initialization failed with status: " + status);
                }
            }
        }, engineInfo.packageName);
    }
    
    private boolean setupLanguage() {
        if (textToSpeech == null) {
            return false;
        }
        
        Locale defaultLocale = Locale.getDefault();
        int result = textToSpeech.setLanguage(defaultLocale);
        
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Default language not supported, trying Chinese");
            result = textToSpeech.setLanguage(Locale.CHINESE);
            
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Chinese not supported, trying English");
                result = textToSpeech.setLanguage(Locale.ENGLISH);
                
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "No supported language found");
                    return false;
                }
            }
        }
        
        if (currentEngine != null) {
            currentEngine.supportsChinese = checkLanguageSupport(Locale.CHINESE);
            currentEngine.supportsEnglish = checkLanguageSupport(Locale.ENGLISH);
        }
        
        Log.d(TAG, "Language setup complete");
        return true;
    }
    
    private boolean checkLanguageSupport(Locale locale) {
        if (textToSpeech == null) {
            return false;
        }
        int result = textToSpeech.isLanguageAvailable(locale);
        return result == TextToSpeech.LANG_AVAILABLE || 
               result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
               result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE;
    }
    
    private void setupUtteranceProgressListener() {
        if (textToSpeech == null) {
            return;
        }
        
        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                Log.d(TAG, "TTS started speaking: " + utteranceId);
                updateState(TTSState.PLAYING);
                if (stateListener != null) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            stateListener.onSpeakStart(utteranceId);
                        }
                    });
                }
            }
            
            @Override
            public void onDone(String utteranceId) {
                Log.d(TAG, "TTS completed speaking: " + utteranceId);
                updateState(TTSState.COMPLETED);
                if (stateListener != null) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            stateListener.onSpeakDone(utteranceId);
                        }
                    });
                }
                mainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (currentState == TTSState.COMPLETED) {
                            updateState(TTSState.IDLE);
                        }
                    }
                }, 500);
            }
            
            @Override
            public void onError(String utteranceId) {
                Log.e(TAG, "TTS error for utterance: " + utteranceId);
                handleSpeakError(utteranceId, TextToSpeech.ERROR, "TTS synthesis error");
            }
            
            @Override
            public void onError(String utteranceId, int errorCode) {
                Log.e(TAG, "TTS error for utterance: " + utteranceId + ", code: " + errorCode);
                handleSpeakError(utteranceId, errorCode, "TTS synthesis error with code: " + errorCode);
            }
        });
    }
    
    private void handleInitFailure(TtsEngineInfo failedEngine, int errorCode, String errorMessage) {
        Log.e(TAG, "TTS initialization failed: " + errorMessage);
        
        if (errorListener != null) {
            final int code = errorCode;
            final String msg = errorMessage;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    errorListener.onEngineError(failedEngine, code, msg);
                }
            });
        }
        
        retryCount++;
        
        if (retryCount < maxRetryCount) {
            Log.d(TAG, "Retrying TTS initialization in " + retryIntervalMs + "ms");
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    TtsEngineInfo nextEngine = getNextFallbackEngine(failedEngine);
                    if (nextEngine != null) {
                        initializeWithEngine(nextEngine);
                    } else {
                        initializeWithEngine(failedEngine);
                    }
                }
            }, retryIntervalMs);
        } else {
            TtsEngineInfo nextEngine = getNextFallbackEngine(failedEngine);
            if (nextEngine != null) {
                Log.d(TAG, "Max retry reached, trying fallback engine: " + nextEngine.packageName);
                retryCount = 0;
                initializeWithEngine(nextEngine);
            } else {
                Log.e(TAG, "All TTS engines failed, giving up");
                isInitializing = false;
                updateState(TTSState.ERROR);
                notifyInitFailed(errorCode, "All TTS engines failed after " + maxRetryCount + " attempts");
            }
        }
    }
    
    private TtsEngineInfo getNextFallbackEngine(TtsEngineInfo failedEngine) {
        boolean foundFailed = false;
        for (TtsEngineInfo engine : availableEngines) {
            if (foundFailed) {
                return engine;
            }
            if (engine.packageName.equals(failedEngine.packageName)) {
                foundFailed = true;
            }
        }
        return null;
    }
    
    private void handleSpeakError(String utteranceId, int errorCode, String errorMessage) {
        updateState(TTSState.ERROR);
        
        if (errorListener != null) {
            final String id = utteranceId;
            final int code = errorCode;
            final String msg = errorMessage;
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    errorListener.onError(id, code, msg);
                }
            });
        }
        
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (currentState == TTSState.ERROR) {
                    updateState(TTSState.IDLE);
                }
            }
        }, 1000);
    }
    
    private void updateState(TTSState newState) {
        TTSState oldState = currentState;
        currentState = newState;
        Log.d(TAG, "TTS state changed: " + oldState + " -> " + newState);
        
        if (stateListener != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    stateListener.onStateChanged(oldState, newState);
                }
            });
        }
    }
    
    private void notifyInitSuccess(TtsEngineInfo engine) {
        if (initListener != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    initListener.onInitSuccess(engine);
                }
            });
        }
    }
    
    private void notifyInitFailed(int error, String message) {
        if (initListener != null) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    initListener.onInitFailed(error, message);
                }
            });
        }
    }
    
    public int speak(String text) {
        return speak(text, 1.0f, 0.0f, null);
    }
    
    public int speak(String text, float volume, float pan, String utteranceId) {
        if (textToSpeech == null || !isInitialized()) {
            Log.e(TAG, "TTS not initialized");
            return TextToSpeech.ERROR;
        }
        
        if (text == null || text.isEmpty()) {
            Log.w(TAG, "Empty text, skipping speak");
            return TextToSpeech.ERROR;
        }
        
        if (currentState == TTSState.PLAYING) {
            Log.d(TAG, "Stopping current speech for new text");
            textToSpeech.stop();
        }
        
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, Math.max(0.0f, Math.min(1.0f, volume)));
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, Math.max(-1.0f, Math.min(1.0f, pan)));
        
        if (utteranceId == null || utteranceId.isEmpty()) {
            utteranceId = "tts_" + System.currentTimeMillis();
        }
        
        updateState(TTSState.LOADING);
        int result = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
        
        if (result == TextToSpeech.SUCCESS) {
            Log.d(TAG, "Speech started successfully: " + utteranceId);
        } else {
            Log.e(TAG, "Failed to start speech: " + result);
            updateState(TTSState.ERROR);
        }
        
        return result;
    }
    
    public int speakQueue(String text, float volume, float pan, String utteranceId) {
        if (textToSpeech == null || !isInitialized()) {
            Log.e(TAG, "TTS not initialized");
            return TextToSpeech.ERROR;
        }
        
        if (text == null || text.isEmpty()) {
            Log.w(TAG, "Empty text, skipping speak");
            return TextToSpeech.ERROR;
        }
        
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, Math.max(0.0f, Math.min(1.0f, volume)));
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, Math.max(-1.0f, Math.min(1.0f, pan)));
        
        if (utteranceId == null || utteranceId.isEmpty()) {
            utteranceId = "tts_queue_" + System.currentTimeMillis();
        }
        
        int result = textToSpeech.speak(text, TextToSpeech.QUEUE_ADD, params, utteranceId);
        
        return result;
    }
    
    public void stop() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            updateState(TTSState.IDLE);
            Log.d(TAG, "TTS stopped");
        }
    }
    
    public void pause() {
        if (textToSpeech != null && currentState == TTSState.PLAYING) {
            textToSpeech.stop();
            updateState(TTSState.PAUSED);
            Log.d(TAG, "TTS paused");
        }
    }
    
    public void resume() {
        if (currentState == TTSState.PAUSED) {
            updateState(TTSState.IDLE);
            Log.d(TAG, "TTS resumed (ready for new speech)");
        }
    }
    
    public void testEngine(String enginePackageName, TtsTestListener listener) {
        testEngine(enginePackageName, "测试语音", listener);
    }
    
    public void testEngine(final String enginePackageName, final String testText, final TtsTestListener listener) {
        final long startTime = System.currentTimeMillis();
        final TtsTestResult result = new TtsTestResult();
        result.enginePackage = enginePackageName;
        
        TtsEngineInfo engineInfo = null;
        for (TtsEngineInfo info : availableEngines) {
            if (info.packageName.equals(enginePackageName)) {
                engineInfo = info;
                break;
            }
        }
        
        if (engineInfo == null) {
            result.success = false;
            result.errorCode = TextToSpeech.ERROR;
            result.errorMessage = "Engine not found: " + enginePackageName;
            result.testDurationMs = System.currentTimeMillis() - startTime;
            if (listener != null) {
                listener.onTestComplete(result);
            }
            return;
        }
        
        result.engineName = engineInfo.name;
        
        final Handler timeoutHandler = new Handler(Looper.getMainLooper());
        final boolean[] completed = {false};
        
        Runnable timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                if (!completed[0]) {
                    completed[0] = true;
                    result.success = false;
                    result.errorCode = TextToSpeech.ERROR;
                    result.errorMessage = "Test timeout";
                    result.testDurationMs = System.currentTimeMillis() - startTime;
                    if (listener != null) {
                        listener.onTestComplete(result);
                    }
                }
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, DEFAULT_TEST_TIMEOUT_MS);
        
        TextToSpeech testTts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.SUCCESS) {
                    if (!completed[0]) {
                        completed[0] = true;
                        timeoutHandler.removeCallbacks(timeoutRunnable);
                        result.success = false;
                        result.errorCode = status;
                        result.errorMessage = "TTS initialization failed";
                        result.testDurationMs = System.currentTimeMillis() - startTime;
                        if (listener != null) {
                            listener.onTestComplete(result);
                        }
                    }
                    return;
                }
                
                int langResult = testTts.setLanguage(Locale.CHINESE);
                result.supportsChinese = (langResult != TextToSpeech.LANG_MISSING_DATA && 
                                         langResult != TextToSpeech.LANG_NOT_SUPPORTED);
                
                langResult = testTts.setLanguage(Locale.ENGLISH);
                result.supportsEnglish = (langResult != TextToSpeech.LANG_MISSING_DATA && 
                                         langResult != TextToSpeech.LANG_NOT_SUPPORTED);
            }
        }, enginePackageName);
        
        final TextToSpeech finalTestTts = testTts;
        testTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
            }
            
            @Override
            public void onDone(String utteranceId) {
                if (!completed[0]) {
                    completed[0] = true;
                    timeoutHandler.removeCallbacks(timeoutRunnable);
                    result.success = true;
                    result.testDurationMs = System.currentTimeMillis() - startTime;
                    
                    if (finalTestTts != null) {
                        finalTestTts.shutdown();
                    }
                    
                    if (listener != null) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                listener.onTestComplete(result);
                            }
                        });
                    }
                }
            }
            
            @Override
            public void onError(String utteranceId) {
                if (!completed[0]) {
                    completed[0] = true;
                    timeoutHandler.removeCallbacks(timeoutRunnable);
                    result.success = false;
                    result.errorCode = TextToSpeech.ERROR;
                    result.errorMessage = "Speech synthesis error";
                    result.testDurationMs = System.currentTimeMillis() - startTime;
                    
                    if (finalTestTts != null) {
                        finalTestTts.shutdown();
                    }
                    
                    if (listener != null) {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                listener.onTestComplete(result);
                            }
                        });
                    }
                }
            }
        });
        
        Bundle params = new Bundle();
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.5f);
        testTts.speak(testText, TextToSpeech.QUEUE_FLUSH, params, "test_" + System.currentTimeMillis());
    }
    
    public void testAllEngines(final TtsTestListener listener) {
        final List<TtsTestResult> results = new ArrayList<>();
        final int[] testedCount = {0};
        final int totalEngines = availableEngines.size();
        
        if (totalEngines == 0) {
            TtsTestResult result = new TtsTestResult();
            result.success = false;
            result.errorMessage = "No TTS engines available";
            if (listener != null) {
                listener.onTestComplete(result);
            }
            return;
        }
        
        for (TtsEngineInfo engine : availableEngines) {
            testEngine(engine.packageName, new TtsTestListener() {
                @Override
                public void onTestComplete(TtsTestResult result) {
                    results.add(result);
                    testedCount[0]++;
                    
                    if (testedCount[0] == totalEngines) {
                        TtsTestResult bestResult = findBestTestResult(results);
                        if (listener != null) {
                            listener.onTestComplete(bestResult);
                        }
                    }
                }
            });
        }
    }
    
    private TtsTestResult findBestTestResult(List<TtsTestResult> results) {
        TtsTestResult best = null;
        for (TtsTestResult result : results) {
            if (result.success) {
                if (best == null || 
                    (result.supportsChinese && !best.supportsChinese) ||
                    (result.supportsChinese == best.supportsChinese && result.testDurationMs < best.testDurationMs)) {
                    best = result;
                }
            }
        }
        
        if (best == null && !results.isEmpty()) {
            best = results.get(0);
        }
        
        return best;
    }
    
    public void reinitialize() {
        Log.d(TAG, "Reinitializing TTS");
        shutdown();
        detectAvailableEngines();
        initialize();
    }
    
    public void reinitializeWithRetry() {
        retryCount = 0;
        reinitialize();
    }
    
    public void shutdown() {
        Log.d(TAG, "Shutting down TTS");
        
        if (textToSpeech != null) {
            try {
                textToSpeech.stop();
                textToSpeech.shutdown();
            } catch (Exception e) {
                Log.e(TAG, "Error during TTS shutdown: " + e.getMessage());
            }
            textToSpeech = null;
        }
        
        currentEngine = null;
        isInitializing = false;
        updateState(TTSState.IDLE);
    }
    
    public boolean isLanguageAvailable(Locale locale) {
        if (textToSpeech == null) {
            return false;
        }
        int result = textToSpeech.isLanguageAvailable(locale);
        return result == TextToSpeech.LANG_AVAILABLE || 
               result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
               result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE;
    }
    
    public List<Locale> getAvailableLanguages() {
        if (textToSpeech == null) {
            return new ArrayList<>();
        }
        
        Locale[] locales = Locale.getAvailableLocales();
        List<Locale> availableLocales = new ArrayList<>();
        
        for (Locale locale : locales) {
            if (isLanguageAvailable(locale)) {
                availableLocales.add(locale);
            }
        }
        
        return availableLocales;
    }
    
    public boolean setLanguage(Locale locale) {
        if (textToSpeech == null) {
            return false;
        }
        
        int result = textToSpeech.setLanguage(locale);
        return result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED;
    }
    
    public boolean setSpeechRate(float rate) {
        if (textToSpeech == null) {
            return false;
        }
        int result = textToSpeech.setSpeechRate(Math.max(0.5f, Math.min(2.0f, rate)));
        return result == TextToSpeech.SUCCESS;
    }
    
    public boolean setPitch(float pitch) {
        if (textToSpeech == null) {
            return false;
        }
        int result = textToSpeech.setPitch(Math.max(0.5f, Math.min(2.0f, pitch)));
        return result == TextToSpeech.SUCCESS;
    }
    
    public Map<String, String> getEngineInfo() {
        Map<String, String> info = new HashMap<>();
        
        if (currentEngine != null) {
            info.put("current_engine_name", currentEngine.name);
            info.put("current_engine_package", currentEngine.packageName);
            info.put("current_engine_label", currentEngine.label);
            info.put("supports_chinese", String.valueOf(currentEngine.supportsChinese));
            info.put("supports_english", String.valueOf(currentEngine.supportsEnglish));
        }
        
        info.put("current_state", currentState.name());
        info.put("is_initialized", String.valueOf(isInitialized()));
        info.put("is_miui_tv", String.valueOf(isMiuiTv));
        info.put("available_engines_count", String.valueOf(availableEngines.size()));
        
        StringBuilder engineList = new StringBuilder();
        for (int i = 0; i < availableEngines.size(); i++) {
            if (i > 0) engineList.append(", ");
            engineList.append(availableEngines.get(i).packageName);
        }
        info.put("available_engines", engineList.toString());
        
        return info;
    }
}
