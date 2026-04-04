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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.app.Notification.Builder;

public class ServerService extends Service {
    private static final String TAG = "ServerService";
    private ServerSocket serverSocket;
    private TextToSpeech textToSpeech;
    private boolean isRunning = false;
    private static final int serverPort = 1234;
    
    private FloatingWindowService floatingWindowService;
    private boolean isFloatingWindowBound = false;
    private TTSState currentTTSState = TTSState.IDLE;
    
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
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        
        // Create notification channel for foreground service
        createNotificationChannel();
        
        // Start foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification());
        
        // Initialize TextToSpeech
        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    Log.d(TAG, "TextToSpeech initialized successfully");
                    setupTTSListener();
                }
            }
        });
        
        // Bind to FloatingWindowService
        Intent floatingWindowIntent = new Intent(this, FloatingWindowService.class);
        startService(floatingWindowIntent);
        bindService(floatingWindowIntent, floatingWindowConnection, Context.BIND_AUTO_CREATE);
        
        // Start server in a separate thread
        startServer();
    }
    
    private void setupTTSListener() {
        if (textToSpeech != null) {
            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    Log.d(TAG, "TTS started");
                }
                
                @Override
                public void onDone(String utteranceId) {
                    Log.d(TAG, "TTS completed");
                    // Hide floating window after a short delay
                    new android.os.Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            hideFloatingWindow();
                        }
                    }, 2000);
                }
                
                @Override
                public void onError(String utteranceId) {
                    Log.e(TAG, "TTS error");
                    hideFloatingWindow();
                }
            });
        }
    }
    
    private void showFloatingWindow(String text) {
        if (isFloatingWindowBound && floatingWindowService != null) {
            floatingWindowService.updateText(text);
            floatingWindowService.showWindow();
        }
    }
    
    private void hideFloatingWindow() {
        if (isFloatingWindowBound && floatingWindowService != null) {
            floatingWindowService.hideWindow();
        }
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "TCP Server Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background service for TCP server");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            notificationIntent, 
            PendingIntent.FLAG_IMMUTABLE
        );
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("TCP Server")
                .setContentText("Server running on port 1234")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
        } else {
            return new Notification.Builder(this)
                .setContentTitle("TCP Server")
                .setContentText("Server running on port 1234")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
        }
    }
    
    private void startServer() {
        isRunning = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(serverPort);
                    Log.d(TAG, "Server started on port " + serverPort);
                    
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
                }
            }
        }).start();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
        
        isRunning = false;
        
        // Unbind from FloatingWindowService
        if (isFloatingWindowBound) {
            unbindService(floatingWindowConnection);
            isFloatingWindowBound = false;
        }
        
        // Shutdown server socket
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing server socket", e);
            }
        }
        
        // Shutdown TextToSpeech
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    private class ServerAsyncTask extends AsyncTask<Socket, Void, String> {
        @Override
        protected String doInBackground(Socket... params) {
            String result = null;
            Socket mySocket = params[0];
            try {
                InputStream is = mySocket.getInputStream();
                OutputStream os = mySocket.getOutputStream();
                
                // Parse HTTP request headers using InputStream
                HttpRequestInfo requestInfo = parseHttpRequest(is);
                
                // Read POST payload if it's a POST request
                if (requestInfo.isPost && requestInfo.contentLength > 0) {
                    result = readPostPayload(is, requestInfo.contentLength);
                }
                
                // Send HTTP response
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
                
                // Check if payload is JSON and contains tts: true
                try {
                    JSONObject jsonObject = new JSONObject(s);
                    if (jsonObject.has("tts") && jsonObject.getBoolean("tts")) {
                        if (jsonObject.has("txt")) {
                            String text = jsonObject.getString("txt");
                            if (!text.isEmpty() && textToSpeech != null) {
                                // Get volume (0-100, default 40)
                                int volume = 40;
                                if (jsonObject.has("volume")) {
                                    try {
                                        volume = jsonObject.getInt("volume");
                                        // Clamp volume to 0-100 range
                                        volume = Math.max(0, Math.min(100, volume));
                                    } catch (Exception e) {
                                        // Invalid volume, use default
                                    }
                                }
                                
                                // Get channel (left, right, stereo)
                                String channel = "stereo";
                                if (jsonObject.has("channel")) {
                                    channel = jsonObject.getString("channel").toLowerCase();
                                    // Validate channel value
                                    if (!channel.equals("left") && !channel.equals("right") && !channel.equals("stereo")) {
                                        channel = "stereo";
                                    }
                                }
                                
                                // Calculate volume (0.0-1.0)
                                float volumeFloat = volume / 100.0f;
                                
                                // Calculate pan (-1.0 left, 0.0 center, 1.0 right)
                                float pan = 0.0f;
                                switch (channel) {
                                    case "left":
                                        pan = -1.0f;
                                        break;
                                    case "right":
                                        pan = 1.0f;
                                        break;
                                    default: // stereo
                                        pan = 0.0f;
                                        break;
                                }
                                
                                // Set TTS parameters
                                android.os.Bundle params = new android.os.Bundle();
                                params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volumeFloat);
                                params.putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, pan);
                                
                                // Generate unique utterance ID
                                String utteranceId = "tts_" + System.currentTimeMillis();
                                
                                // Show floating window
                                showFloatingWindow(text);
                                
                                // Speak the text
                                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
                            }
                        }
                    }
                } catch (JSONException e) {
                    // Not a valid JSON, ignore
                    Log.d(TAG, "Received non-JSON payload");
                }
            }
        }
        
        /**
         * Parse HTTP request headers directly from InputStream
         */
        private HttpRequestInfo parseHttpRequest(InputStream is) throws IOException {
            HttpRequestInfo info = new HttpRequestInfo();
            StringBuilder headerLine = new StringBuilder();
            int b;
            boolean firstLine = true;
            
            while ((b = is.read()) != -1) {
                if (b == '\r') {
                    // Check for \r\n
                    int next = is.read();
                    if (next == '\n') {
                        String line = headerLine.toString();
                        headerLine.setLength(0);
                        
                        if (line.isEmpty()) {
                            // Empty line indicates end of headers
                            break;
                        }
                        
                        if (firstLine) {
                            // Check if it's a POST request
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
                        // Not \r\n, add back \r and the next character
                        headerLine.append('\r');
                        headerLine.append((char) next);
                    }
                } else {
                    headerLine.append((char) b);
                }
            }
            
            return info;
        }
        
        /**
         * Read POST payload from InputStream
         */
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
        
        /**
         * Send HTTP response
         */
        private void sendHttpResponse(OutputStream os) throws IOException {
            String response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/plain; charset=UTF-8\r\n" +
                    "Content-Length: 0\r\n" +
                    "Connection: close\r\n" +
                    "\r\n";
            os.write(response.getBytes("UTF-8"));
            os.flush();
        }
        
        /**
         * HTTP request information
         */
        private class HttpRequestInfo {
            boolean isPost = false;
            int contentLength = 0;
        }
    }
}