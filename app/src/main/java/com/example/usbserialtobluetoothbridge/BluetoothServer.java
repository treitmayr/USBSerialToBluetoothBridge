package com.example.usbserialtobluetoothbridge;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothServer {

    public interface Listener {
        void onBluetoothDataReceived(byte[] data);
        void onBluetoothError(String message);
        void onBluetoothStateChanged(boolean connected);
    }

    private static final String TAG = "BluetoothServer";
    private static final String NAME = "USB-Serial Bridge";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final BluetoothAdapter bluetoothAdapter;
    private final Context context;
    private final Listener listener;
    private AcceptThread acceptThread;
    private ConnectedThread connectedThread;

    private void log(String message) {
        if (listener != null) listener.onBluetoothError(message);
    }

    public BluetoothServer(Context context, Listener listener) {
        this.context = context;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.listener = listener;
    }

    public synchronized void start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) 
                    != PackageManager.PERMISSION_GRANTED) {
                log("Missing BLUETOOTH_CONNECT permission");
                return;
            }
        }

        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        if (acceptThread == null) {
            acceptThread = new AcceptThread();
            acceptThread.start();
        }
    }

    public synchronized void stop() {
        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        listener.onBluetoothStateChanged(false);
    }

    public void write(byte[] data) {
        ConnectedThread r;
        synchronized (this) {
            if (connectedThread == null) return;
            r = connectedThread;
        }
        r.write(data);
    }

    public synchronized boolean isConnected() {
        return connectedThread != null;
    }

    private synchronized void connected(BluetoothSocket socket) {
        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        connectedThread = new ConnectedThread(socket);
        connectedThread.start();
        listener.onBluetoothStateChanged(true);
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket serverSocket;

        @SuppressLint("MissingPermission")
        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "listen() failed", e);
                log("BT Listen failed: " + e.getMessage());
            }
            serverSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            while (socket == null) {
                Log.i(TAG, "accepting bluetooth connections");
                try {
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "accept() failed", e);
                    log("BT Accept failed: " + e.getMessage());
                    break;
                }

                if (socket != null) {
                    synchronized (BluetoothServer.this) {
                        connected(socket);
                    }
                } else {
                    Log.e(TAG, "accept() did not return socket");
                    log("accept() did not return socket");
                }
            }
        }

        public void cancel() {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of server failed", e);
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public ConnectedThread(BluetoothSocket socket) {
            this.socket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
                log("BT Streams error: " + e.getMessage());
            }

            inputStream = tmpIn;
            outputStream = tmpOut;
            Log.i(TAG, "ready to receive bluetooth data");
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (true) {
                try {
                    bytes = inputStream.read(buffer);
                    byte[] data = new byte[bytes];
                    System.arraycopy(buffer, 0, data, 0, bytes);
                    listener.onBluetoothDataReceived(data);
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    synchronized (BluetoothServer.this) {
                        connectedThread = null;
                        listener.onBluetoothStateChanged(false);
                        start(); // Restart accepting
                    }
                    break;
                }
            }
        }

        public void write(byte[] buffer) {
            if (outputStream == null) return;
            try {
                outputStream.write(buffer);
                outputStream.flush();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
                log("BT Write error: " + e.getMessage());
            }
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
}
