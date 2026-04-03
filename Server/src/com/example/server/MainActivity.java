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
				PrintWriter out = new PrintWriter(new OutputStreamWriter(os, "UTF-8"), true);
				BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));

				// Parse HTTP request headers
				HttpRequestInfo requestInfo = parseHttpRequest(br);

				// Read POST payload if it's a POST request
				if (requestInfo.isPost && requestInfo.contentLength > 0) {
					result = readPostPayload(is, requestInfo.contentLength);
				} else {
					// For non-POST requests, read first line
					result = br.readLine();
				}

				// Send HTTP response
				sendHttpResponse(out);

				mySocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (Exception e) {
				// Handle any other exceptions
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
		 * Parse HTTP request headers
		 */
		private HttpRequestInfo parseHttpRequest(BufferedReader br) throws IOException {
			HttpRequestInfo info = new HttpRequestInfo();
			String line;

			// Read first line (request line)
			if ((line = br.readLine()) != null) {
				// Check if it's a POST request
				if (line.startsWith("POST ")) {
					info.isPost = true;
				}
			}

			// Read remaining headers
			while ((line = br.readLine()) != null && !line.isEmpty()) {
				if (line.toLowerCase().startsWith("content-length:")) {
					try {
						info.contentLength = Integer.parseInt(line.substring(16).trim());
					} catch (NumberFormatException e) {
						// Invalid Content-Length, use 0
						info.contentLength = 0;
					}
				}
			}

			return info;
		}

		/**
		 * Read POST payload from InputStream
		 * Content-Length is in bytes, so we need to read bytes and convert to String
		 */
		private String readPostPayload(InputStream is, int contentLength) throws IOException {
			// Read exact number of bytes specified by Content-Length
			byte[] buffer = new byte[contentLength];
			int totalRead = 0;
			
			while (totalRead < contentLength) {
				int read = is.read(buffer, totalRead, contentLength - totalRead);
				if (read == -1) break;
				totalRead += read;
			}
			
			// Convert bytes to String using UTF-8 encoding
			return new String(buffer, 0, totalRead, "UTF-8");
		}

		/**
		 * Send HTTP response
		 */
		private void sendHttpResponse(PrintWriter out) {
			out.println("HTTP/1.1 200 OK");
			out.println("Content-Type: text/plain; charset=UTF-8");
			out.println("Content-Length: 0");
			out.println("Connection: close");
			out.println();
			out.flush();
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
