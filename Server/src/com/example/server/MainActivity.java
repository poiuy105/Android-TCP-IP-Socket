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
import android.net.Uri;
import android.os.Build;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
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
		
		// Check and request floating window permission for first start
		SharedPreferences prefs = getSharedPreferences("ServerPrefs", MODE_PRIVATE);
		int startCount = prefs.getInt("startCount", 0);
		
		if (startCount == 0) {
			checkFloatingWindowPermission();
		}
		
		if (startCount == 0) {
			// First start - show full UI
			setContentView(R.layout.activity_main);
			tvClientMsg = (TextView) findViewById(R.id.textViewClientMessage);
			tvServerIP = (EditText) findViewById(R.id.textViewServerIP);
			tvServerPort = (EditText) findViewById(R.id.textViewServerPort);
			// Set default values - auto detect IP address
			String detectedIp = getDeviceIpAddress();
			tvServerIP.setText(detectedIp);
			tvServerPort.setText("1234");
			
			// 电视设备UI优化
			// 增加字体大小和控件大小，适配电视屏幕
			tvClientMsg.setTextSize(20);
			tvServerIP.setTextSize(20);
			tvServerPort.setTextSize(20);
			
			// 为遥控器导航添加焦点管理
			tvServerIP.setFocusable(true);
			tvServerIP.setFocusableInTouchMode(true);
			tvServerPort.setFocusable(true);
			tvServerPort.setFocusableInTouchMode(true);
			
			// 添加遥控器按键支持
			tvServerIP.setOnKeyListener(new OnKeyListener() {
				@Override
				public boolean onKey(View v, int keyCode, KeyEvent event) {
					if (event.getAction() == KeyEvent.ACTION_DOWN) {
						if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
							tvServerPort.requestFocus();
							return true;
						}
					}
					return false;
				}
			});
			
			tvServerPort.setOnKeyListener(new OnKeyListener() {
				@Override
				public boolean onKey(View v, int keyCode, KeyEvent event) {
					if (event.getAction() == KeyEvent.ACTION_DOWN) {
						if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
							tvServerIP.requestFocus();
							return true;
						} else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
							Button btnConfirmPort = (Button) findViewById(R.id.buttonConfirmPort);
							if (btnConfirmPort != null) {
								btnConfirmPort.requestFocus();
							}
							return true;
						}
					}
					return false;
				}
			});
			
			// Setup port confirm button
			Button btnConfirmPort = (Button) findViewById(R.id.buttonConfirmPort);
			if (btnConfirmPort != null) {
				// 电视设备按钮优化
				btnConfirmPort.setTextSize(18);
				btnConfirmPort.setFocusable(true);
				
				btnConfirmPort.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						String portStr = tvServerPort.getText().toString().trim();
						if (!portStr.isEmpty()) {
							try {
								int newPort = Integer.parseInt(portStr);
								if (newPort > 0 && newPort <= 65535) {
									// Save port to preferences
									SharedPreferences.Editor editor = prefs.edit();
									editor.putInt("serverPort", newPort);
									editor.apply();
									
									// Restart service with new port
									Intent restartIntent = new Intent(MainActivity.this, ServerService.class);
									restartIntent.putExtra("port", newPort);
									if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
										startForegroundService(restartIntent);
									} else {
										startService(restartIntent);
									}
									
									Log.d("MainActivity", "Port changed to: " + newPort);
								} else {
									tvServerPort.setError("Port must be 1-65535");
								}
							} catch (NumberFormatException e) {
								tvServerPort.setError("Invalid port number");
							}
						}
					}
				});
				
				// 按钮焦点导航
				btnConfirmPort.setOnKeyListener(new OnKeyListener() {
					@Override
					public boolean onKey(View v, int keyCode, KeyEvent event) {
						if (event.getAction() == KeyEvent.ACTION_DOWN) {
							if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
								tvServerPort.requestFocus();
								return true;
							} else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
								Button clearBtn = (Button) findViewById(R.id.button1);
								if (clearBtn != null) {
									clearBtn.requestFocus();
								}
								return true;
							}
						}
						return false;
					}
				});
			}
			
			clear = (Button)findViewById(R.id.button1);
			if (clear != null) {
				// 电视设备按钮优化
				clear.setTextSize(18);
				clear.setFocusable(true);
				
				clear.setOnClickListener(new OnClickListener() {
					
					@Override
					public void onClick(View v) {
						tvClientMsg.setText("");
					}
				});
				
				// 按钮焦点导航
				clear.setOnKeyListener(new OnKeyListener() {
					@Override
					public boolean onKey(View v, int keyCode, KeyEvent event) {
						if (event.getAction() == KeyEvent.ACTION_DOWN) {
							if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
								Button btnConfirmPort = (Button) findViewById(R.id.buttonConfirmPort);
								if (btnConfirmPort != null) {
									btnConfirmPort.requestFocus();
								}
								return true;
							}
						}
						return false;
					}
				});
			}
			
			// 设置初始焦点
			tvServerIP.requestFocus();
		} else {
			// Not first start - finish activity without showing UI
			Log.d("MainActivity", "Not first start, finishing activity");
			// Start service and finish immediately
			Intent serviceIntent = new Intent(this, ServerService.class);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				startForegroundService(serviceIntent);
			} else {
				startService(serviceIntent);
			}
			finish();
			return;
		}
		
		// Increment start count
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt("startCount", startCount + 1);
		editor.apply();
		
		// Start the server service (only for first start)
		if (startCount == 0) {
			Intent serviceIntent = new Intent(this, ServerService.class);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				startForegroundService(serviceIntent);
			} else {
				startService(serviceIntent);
			}
			Log.d("MainActivity", "ServerService started");
		}
		
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
	public String getDeviceIpAddress() {
		String ipAddress = "127.0.0.1";
		try {
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements()) {
				NetworkInterface networkInterface = interfaces.nextElement();
				// Skip loopback and disabled interfaces
				if (networkInterface.isLoopback() || !networkInterface.isUp()) {
					continue;
				}
				
				Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
				while (addresses.hasMoreElements()) {
					InetAddress inetAddress = addresses.nextElement();
					// Get IPv4 address only
					if (!inetAddress.isLoopbackAddress() && inetAddress.getHostAddress().indexOf(':') < 0) {
						ipAddress = inetAddress.getHostAddress();
						Log.d("MainActivity", "Found IP: " + ipAddress + " on interface: " + networkInterface.getName());
						// Prefer WiFi interface
						if (networkInterface.getName().toLowerCase().contains("wlan") ||
						    networkInterface.getName().toLowerCase().contains("wifi")) {
							return ipAddress;
						}
					}
				}
			}
		} catch (SocketException e) {
			Log.e("MainActivity", "Error getting IP address", e);
		}
		return ipAddress;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}




	
	private void checkFloatingWindowPermission() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (!Settings.canDrawOverlays(this)) {
				Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
					Uri.parse("package:" + getPackageName()));
				startActivityForResult(intent, 1001);
			}
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == 1001) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
				if (Settings.canDrawOverlays(this)) {
					Log.d("MainActivity", "Floating window permission granted");
				} else {
					Log.d("MainActivity", "Floating window permission denied");
				}
			}
		}
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
