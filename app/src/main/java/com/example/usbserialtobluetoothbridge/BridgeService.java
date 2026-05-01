package com.example.usbserialtobluetoothbridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
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
        Notification notification = getNotification("Bridge Service Running");
        
        final boolean hasPermission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission = ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) 
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            hasPermission = true;
        }

        if (hasPermission) {
            // Android 14+ (API 34) requires service type if declared in manifest.
            // Since minSdk is 29, we can always pass the type for better compliance.
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            if (listener != null) listener.onLog("Notification permission missing, running without foreground notification");
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
