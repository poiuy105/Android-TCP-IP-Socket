package com.example.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.HashMap;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.EditText;

public class MainActivity extends Activity {
	private TextView tvClientMsg;
	private EditText tvServerIP, tvServerPort;
	private int SERVER_PORT;
	private String SERVER_IP;
	private String Server_Name = "Kingspp";
	Button clear;
	private TextToSpeech textToSpeech;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// Check if this is the first start
		SharedPreferences prefs = getSharedPreferences("ServerPrefs", MODE_PRIVATE);
		int startCount = prefs.getInt("startCount", 0);
		
		if (startCount == 0) {
			// First start - show full UI
			setContentView(R.layout.activity_main);
			tvClientMsg = (TextView) findViewById(R.id.textViewClientMessage);
			tvServerIP = (EditText) findViewById(R.id.textViewServerIP);
			tvServerPort = (EditText) findViewById(R.id.textViewServerPort);
			// Set default values
			tvServerIP.setText("127.0.0.1");
			tvServerPort.setText("1234");
			
			clear = (Button)findViewById(R.id.button1);
			clear.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					tvClientMsg.setText("");
				}
			});
		} else {
			// Not first start - go to background
			Log.d("MainActivity", "Not first start, going to background");
			moveTaskToBack(true);
		}
		
		// Increment start count
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt("startCount", startCount + 1);
		editor.apply();
		
		// Start the server service
		Intent serviceIntent = new Intent(this, ServerService.class);
		startService(serviceIntent);
		Log.d("MainActivity", "ServerService started");
		
		// Initialize TextToSpeech (only for UI mode)
		if (startCount == 0) {
			textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
				@Override
				public void onInit(int status) {
					if (status == TextToSpeech.SUCCESS) {
						Log.d("TTS", "TextToSpeech initialized successfully");
					}
				}
			});
		}
		
		// Server is now running in ServerService
		Log.d("MainActivity", "Server service started, UI ready");
	}

	/**
	 * Get ip address of the device
	 */
	public void getDeviceIpAddress() {
		// Hardcode IP address to 127.0.0.1
		tvServerIP.setText("127.0.0.1");
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}




	
	@Override
	protected void onDestroy() {
		if (textToSpeech != null) {
			textToSpeech.stop();
			textToSpeech.shutdown();
		}
		super.onDestroy();
	}
}
