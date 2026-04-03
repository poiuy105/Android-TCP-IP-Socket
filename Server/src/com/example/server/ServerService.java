package com.example.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.util.Log;

public class ServerService extends Service {
    private static final String TAG = "ServerService";
    private ServerSocket serverSocket;
    private TextToSpeech textToSpeech;
    private boolean isRunning = false;
    private int serverPort = 1234;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        
        // Initialize TextToSpeech
        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    Log.d(TAG, "TextToSpeech initialized successfully");
                }
            }
        });
        
        // Start server in a separate thread
        startServer();
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
                                
                                // Speak the text
                                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params, null);
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