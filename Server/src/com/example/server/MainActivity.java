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

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
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

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
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
				// TODO Auto-generated method stub
				tvClientMsg.setText("");
				
			}
		});
		
		new Thread(new Runnable() {

			@Override
			public void run() {
				
				try {
					// Get values from EditText
					String portStr = tvServerPort.getText().toString();
					int port = 1234; // Default port
					try {
						port = Integer.parseInt(portStr);
					} catch (NumberFormatException e) {
						// Use default port if parsing fails
					}
					
					ServerSocket socServer = new ServerSocket(port);
					Socket socClient = null;
					while (true) {
						socClient = socServer.accept();
						ServerAsyncTask serverAsyncTask = new ServerAsyncTask();
						serverAsyncTask.execute(new Socket[] { socClient });
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}).start();
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



	/**
	 * AsyncTask which handles the commiunication with clients
	 */
	class ServerAsyncTask extends AsyncTask<Socket, Void, String> {
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
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
			return result;
		}
		
		@Override
		protected void onPostExecute(String s) {
			if (s != null) {
				tvClientMsg.append("POST Payload:\n" + s + "\n\n");
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
