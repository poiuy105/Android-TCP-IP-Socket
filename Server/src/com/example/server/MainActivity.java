package com.example.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;

import android.support.v7.app.ActionBarActivity;
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

public class MainActivity extends ActionBarActivity {
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
				PrintWriter out = new PrintWriter(mySocket.getOutputStream(),
						true);

				out.println("Welcome to \""+Server_Name+"\" Server");

				BufferedReader br = new BufferedReader(
						new InputStreamReader(is));

				result = br.readLine();

				mySocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return result;
		}

		@Override
		protected void onPostExecute(String s) {

			tvClientMsg.append(s+"\n");
			
		}
	}
}
