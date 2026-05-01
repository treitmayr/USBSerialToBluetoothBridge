package com.example.usbserialtobluetoothbridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public class BridgeService extends Service implements UsbSerialManager.Listener, BluetoothServer.Listener {

    public interface Listener {
        void onStatusChanged();
        void onLog(String message);
    }

    private static final String CHANNEL_ID = "BridgeServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    private UsbSerialManager usbSerialManager;
    private BluetoothServer bluetoothServer;
    private Listener listener;
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        BridgeService getService() {
            return BridgeService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        usbSerialManager = new UsbSerialManager(this, this);
        bluetoothServer = new BluetoothServer(this, this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) 
                    == PackageManager.PERMISSION_GRANTED) {
                startForeground(NOTIFICATION_ID, getNotification("Bridge Service Running"));
            } else {
                if (listener != null) listener.onLog("Notification permission missing, running without foreground notification");
            }
        } else {
            startForeground(NOTIFICATION_ID, getNotification("Bridge Service Running"));
        }
        bluetoothServer.start();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        usbSerialManager.close();
        bluetoothServer.stop();
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Bridge Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private Notification getNotification(String contentText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("USB-Bluetooth Bridge")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setContentIntent(pendingIntent)
                .build();
    }

    public UsbSerialManager getUsbSerialManager() {
        return usbSerialManager;
    }

    public BluetoothServer getBluetoothServer() {
        return bluetoothServer;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    // UsbSerialManager.Listener
    @Override
    public void onUsbDataReceived(byte[] data) {
        bluetoothServer.write(data);
    }

    @Override
    public void onUsbError(String message) {
        if (listener != null) listener.onLog("USB Error: " + message);
    }

    @Override
    public void onUsbStateChanged(boolean connected) {
        if (listener != null) {
            listener.onStatusChanged();
            listener.onLog("USB " + (connected ? "Connected" : "Disconnected"));
        }
    }

    // BluetoothServer.Listener
    @Override
    public void onBluetoothDataReceived(byte[] data) {
        usbSerialManager.write(data);
    }

    @Override
    public void onBluetoothError(String message) {
        if (listener != null) listener.onLog("BT Error: " + message);
    }

    @Override
    public void onBluetoothStateChanged(boolean connected) {
        if (listener != null) {
            listener.onStatusChanged();
            listener.onLog("Bluetooth " + (connected ? "Connected" : "Disconnected"));
        }
    }
}
