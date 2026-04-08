package com.example.server;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import io.github.initio.JavaMQTT;

import org.eclipse.paho.client.mqttv3.MqttException;

import java.util.concurrent.Executor;

public class MqttManager {
    private static final String TAG = "MqttManager";
    private static final String PREFS_NAME = "MqttPrefs";
    
    public static final String ACTION_MQTT_CONNECTED = "com.example.server.MQTT_CONNECTED";
    public static final String ACTION_MQTT_DISCONNECTED = "com.example.server.MQTT_DISCONNECTED";
    public static final String ACTION_MQTT_MESSAGE_RECEIVED = "com.example.server.MQTT_MESSAGE_RECEIVED";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_TOPIC = "topic";
    
    private Context context;
    private JavaMQTT mqttClient;
    private MqttConfig config;
    private MqttConnectionListener connectionListener;
    private MqttMessageListener messageListener;
    private boolean isConnecting = false;
    private boolean initialized = false;
    
    public interface MqttConnectionListener {
        void onConnected();
        void onDisconnected();
        void onConnectionFailed(Throwable cause);
    }
    
    public interface MqttMessageListener {
        void onMessageReceived(String topic, String payload);
    }
    
    public MqttManager(Context context) {
        Log.d(TAG, "MqttManager constructor called");
        try {
            this.context = context.getApplicationContext();
            this.config = loadConfig();
            initMqttClient();
            this.initialized = true;
            Log.d(TAG, "MqttManager initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize MqttManager", e);
            this.initialized = false;
        }
    }
    
    public MqttManager(Context context, MqttConfig config) {
        Log.d(TAG, "MqttManager constructor with config called");
        try {
            this.context = context.getApplicationContext();
            this.config = config != null ? config : loadConfig();
            initMqttClient();
            this.initialized = true;
            Log.d(TAG, "MqttManager initialized successfully with config");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize MqttManager with config", e);
            this.initialized = false;
        }
    }
    
    private void initMqttClient() throws MqttException {
        Executor callbackExecutor = command -> new Handler(Looper.getMainLooper()).post(command);
        mqttClient = new JavaMQTT(
                config.getServerUri(),
                config.getClientId(),
                context.getFilesDir().getAbsolutePath(),
                callbackExecutor
        );
        
        mqttClient.setOnReconnectListener(() -> {
            subscribeToTopics();
        });
        
        mqttClient.setGlobalListener((topic, payload) -> {
            if (messageListener != null) {
                messageListener.onMessageReceived(topic, payload);
            }
        });
    }
    
    public boolean isInitialized() {
        return initialized;
    }
    
    public void setConnectionListener(MqttConnectionListener listener) {
        this.connectionListener = listener;
    }
    
    public void setMessageListener(MqttMessageListener listener) {
        this.messageListener = listener;
    }
    
    public MqttConfig getConfig() {
        return config;
    }
    
    public void setConfig(MqttConfig config) {
        this.config = config;
        saveConfig(config);
        // Reinitialize client with new config
        try {
            if (mqttClient != null) {
                mqttClient.close();
            }
            initMqttClient();
        } catch (MqttException e) {
            Log.e(TAG, "Error reinitializing MQTT client", e);
        }
    }
    
    public void connect() {
        Log.d(TAG, "connect() called, initialized=" + initialized);
        
        if (!initialized) {
            Log.e(TAG, "MqttManager not initialized, cannot connect");
            notifyConnectionFailed(new Exception("MqttManager not initialized"));
            return;
        }
        
        if (mqttClient != null && mqttClient.isConnected()) {
            Log.d(TAG, "Already connected");
            return;
        }
        
        if (isConnecting) {
            Log.d(TAG, "Connection in progress");
            return;
        }
        
        isConnecting = true;
        
        try {
            Log.d(TAG, "Connecting to MQTT broker: " + config.getServerUri());
            
            mqttClient.connect(config.getUsername(), config.getPassword(), new JavaMQTT.ConnectionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "MQTT connected successfully");
                    isConnecting = false;
                    subscribeToTopics();
                    notifyConnectionStatus(true);
                }
                
                @Override
                public void onFailure(Throwable exception) {
                    Log.e(TAG, "MQTT connection failed", exception);
                    isConnecting = false;
                    notifyConnectionFailed(exception);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error during MQTT connection", e);
            isConnecting = false;
            notifyConnectionFailed(e);
        }
    }
    
    private void subscribeToTopics() {
        if (mqttClient == null || !mqttClient.isConnected()) {
            Log.w(TAG, "Cannot subscribe - not connected");
            return;
        }
        
        String commandTopic = config.getCommandTopic();
        Log.d(TAG, "Subscribing to topic: " + commandTopic);
        
        mqttClient.subscribe(commandTopic, (topic, payload) -> {
            if (messageListener != null) {
                messageListener.onMessageReceived(topic, payload);
            }
        }, config.getQos());
    }
    
    public void disconnect() {
        Log.d(TAG, "disconnect() called");
        if (mqttClient != null && mqttClient.isConnected()) {
            mqttClient.disconnect();
            notifyConnectionStatus(false);
        }
    }
    
    public boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }
    
    public void publish(String topic, String payload) {
        if (mqttClient == null || !mqttClient.isConnected()) {
            Log.w(TAG, "Cannot publish - not connected");
            return;
        }
        
        mqttClient.put(topic, payload);
        Log.d(TAG, "Published to " + topic + ": " + payload);
    }
    
    public void publishStatus(String status) {
        publish(config.getStatusTopic(), status);
    }
    
    public void publishResponse(String messageId, String response) {
        publish(config.getResponseTopic(), "{\"message_id\":\"" + messageId + "\",\"response\":\"" + response + "\"}");
    }
    
    private void notifyConnectionStatus(boolean connected) {
        if (connectionListener != null) {
            if (connected) {
                connectionListener.onConnected();
            } else {
                connectionListener.onDisconnected();
            }
        }
        
        try {
            Intent intent = new Intent(connected ? ACTION_MQTT_CONNECTED : ACTION_MQTT_DISCONNECTED);
            context.sendBroadcast(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error sending broadcast", e);
        }
    }
    
    private void notifyConnectionFailed(Throwable cause) {
        if (connectionListener != null) {
            connectionListener.onConnectionFailed(cause);
        }
        
        try {
            Intent intent = new Intent(ACTION_MQTT_DISCONNECTED);
            context.sendBroadcast(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error sending broadcast", e);
        }
    }
    
    public void saveConfig(MqttConfig config) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("serverUri", config.getServerUri());
            editor.putString("clientId", config.getClientId());
            editor.putString("username", config.getUsername());
            editor.putString("password", config.getPassword());
            editor.putInt("keepAlive", config.getKeepAliveInterval());
            editor.putBoolean("cleanSession", config.isCleanSession());
            editor.putInt("qos", config.getQos());
            editor.putString("topicPrefix", config.getTopicPrefix());
            editor.putBoolean("autoReconnect", config.isAutoReconnect());
            editor.putInt("connectionTimeout", config.getConnectionTimeout());
            editor.apply();
            Log.d(TAG, "MQTT config saved");
        } catch (Exception e) {
            Log.e(TAG, "Error saving config", e);
        }
    }
    
    private MqttConfig loadConfig() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            MqttConfig config = new MqttConfig();
            
            String serverUri = prefs.getString("serverUri", null);
            if (serverUri != null) {
                config.setServerUri(serverUri);
                config.setClientId(prefs.getString("clientId", config.getClientId()));
                config.setUsername(prefs.getString("username", ""));
                config.setPassword(prefs.getString("password", ""));
                config.setKeepAliveInterval(prefs.getInt("keepAlive", MqttConfig.DEFAULT_KEEP_ALIVE));
                config.setCleanSession(prefs.getBoolean("cleanSession", MqttConfig.DEFAULT_CLEAN_SESSION));
                config.setQos(prefs.getInt("qos", MqttConfig.DEFAULT_QOS));
                config.setTopicPrefix(prefs.getString("topicPrefix", MqttConfig.DEFAULT_TOPIC_PREFIX));
                config.setAutoReconnect(prefs.getBoolean("autoReconnect", true));
                config.setConnectionTimeout(prefs.getInt("connectionTimeout", 30));
            }
            
            Log.d(TAG, "MQTT config loaded: " + config);
            return config;
        } catch (Exception e) {
            Log.e(TAG, "Error loading config, using defaults", e);
            return new MqttConfig();
        }
    }
    
    public void release() {
        Log.d(TAG, "release() called");
        disconnect();
        if (mqttClient != null) {
            mqttClient.close();
            mqttClient = null;
        }
        connectionListener = null;
        messageListener = null;
        initialized = false;
    }
}