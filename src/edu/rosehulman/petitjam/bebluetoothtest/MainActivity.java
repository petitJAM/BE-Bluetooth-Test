package edu.rosehulman.petitjam.bebluetoothtest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {

	private static final String BT = "BT";
	private static final int REQUEST_ENABLE_BT = 1;
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothDevice mBluetoothDevice;
	private Thread workerThread;
	private boolean stopWorker = false;
	protected int readBufferPosition;
	protected byte[] readBuffer;
	
	private Button mWriteButton;
	private TextView mArduinoMessagesTextView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		mWriteButton = (Button)findViewById(R.id.write_button);
		mArduinoMessagesTextView = (TextView)findViewById(R.id.from_arduino);
		

		// final BluetoothManager btManager =
		// (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
		//
		// mBluetoothAdapter = btManager.getAdapter();

		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		// Ensures Bluetooth is available on the device and it is enabled. If
		// not,
		// displays a dialog requesting user permission to enable Bluetooth.
		if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);

			Log.d(BT, "Bluetooth connection problem");
		}

		Set<BluetoothDevice> pairedDevices = mBluetoothAdapter
				.getBondedDevices();

		BluetoothDevice btd = null;

		// If there are paired devices
		if (pairedDevices.size() > 0) {
			// Loop through paired devices
			for (BluetoothDevice device : pairedDevices) {
				if (device.getName().equals("Arduino")) {
					Log.d(BT, device.getName() + "\n" + device.getAddress());
					btd = device;
				}
			}
		}

		UUID uuid = btd.getUuids()[0].getUuid();

		Log.d(BT, uuid.toString());

		try {
			BluetoothSocket sock = btd.createRfcommSocketToServiceRecord(uuid);
			sock.connect();

			final Handler handler = new Handler();

			final OutputStream mmOutputStream = sock.getOutputStream();
			final InputStream mmInputStream = sock.getInputStream();

			final byte[] msg = { 'h', 'e', 'l', 'l', 'o', ' ', 'f', 'r', 'o', 'm',
					' ', 'p', 'h', 'o', 'n', 'e' };
			
			mWriteButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					try {
						mmOutputStream.write(msg);
					} catch (IOException e) {}
				}
			});

			readBufferPosition = 0;
			readBuffer = new byte[1024];

			workerThread = new Thread(new Runnable() {
				final byte delimiter = 10;

				public void run() {
					while (!Thread.currentThread().isInterrupted()
							&& !stopWorker) {
						try {
							int bytesAvailable = mmInputStream.available();
							if (bytesAvailable > 0) {
								byte[] packetBytes = new byte[bytesAvailable];
								mmInputStream.read(packetBytes);
								for (int i = 0; i < bytesAvailable; i++) {
									byte b = packetBytes[i];
									if (b == delimiter) {
										byte[] encodedBytes = new byte[readBufferPosition];
										System.arraycopy(readBuffer, 0,
												encodedBytes, 0,
												encodedBytes.length);
										final String data = new String(
												encodedBytes, "US-ASCII");
										readBufferPosition = 0;

										handler.post(new Runnable() {
											public void run() {
												mArduinoMessagesTextView.setText(data);
												Log.d(BT, data);
											}
										});
									} else {
										readBuffer[readBufferPosition++] = b;
									}
								}
							}
						} catch (IOException ex) {
							stopWorker = true;
						}
					}
				}
			});

			workerThread.start();

		} catch (IOException e) {
		}

		// mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(uuidStr);
		//
		// (new AcceptThread()).run();
	}

	public void manageConnectedSocket(BluetoothSocket socket) {
		Log.d(BT, "Here I will do something with this connection");
	}

	private class AcceptThread extends Thread {
		private final BluetoothServerSocket mmServerSocket;

		public AcceptThread() {
			// Use a temporary object that is later assigned to mmServerSocket,
			// because mmServerSocket is final
			BluetoothServerSocket tmp = null;
			try {
				for (ParcelUuid pu : mBluetoothDevice.getUuids()) {
					Log.d(BT, pu.getUuid().toString());
				}
				UUID uuid = mBluetoothDevice.getUuids()[0].getUuid();

				// MY_UUID is the app's UUID string, also used by the client
				// code
				tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(
						mBluetoothDevice.getName(), uuid);
			} catch (IOException e) {
			}
			mmServerSocket = tmp;
		}

		public void run() {
			BluetoothSocket socket = null;
			// Keep listening until exception occurs or a socket is returned
			while (true) {
				try {
					Log.d(BT, "attempting to connect");
					socket = mmServerSocket.accept();
				} catch (IOException e) {
					break;
				}
				// If a connection was accepted
				if (socket != null) {
					Log.d(BT, "a connection was accepted");
					// Do work to manage the connection (in a separate thread)
					manageConnectedSocket(socket);
					try {
						mmServerSocket.close();
					} catch (IOException e) {
					}
					break;
				}
			}
		}

		/** Will cancel the listening socket, and cause the thread to finish */
		public void cancel() {
			try {
				mmServerSocket.close();
			} catch (IOException e) {
			}
		}
	}

}
