package com.example.usbserialtobluetoothbridge;

import android.Manifest;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String ACTION_USB_PERMISSION = "com.example.usbserialtobluetoothbridge.USB_PERMISSION";

    private BridgeService bridgeService;
    private boolean isBound = false;

    private TextView usbStatus, btStatus, tvLog;
    private Spinner baudSpinner;
    private SwitchCompat switchDTR, switchRTS;
    private Button btnRefreshUsb, btnReset, btnBootloader;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null && isBound) {
                            bridgeService.getUsbSerialManager().open(device);
                            log("USB Permission granted for " + device.getDeviceName());
                            updateStatus();
                        }
                    } else {
                        log("USB Permission denied for " + device.getDeviceName());
                    }
                }
            }
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BridgeService.LocalBinder binder = (BridgeService.LocalBinder) service;
            bridgeService = binder.getService();
            isBound = true;
            bridgeService.setListener(new BridgeService.Listener() {
                @Override
                public void onStatusChanged() {
                    runOnUiThread(() -> {
                        updateStatus();
                        syncSwitches();
                    });
                }

                @Override
                public void onLog(String message) {
                    log(message);
                }
            });
            log("Service bound");
            updateStatus();
            syncSwitches();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            log("Service unbound");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            float density = v.getContext().getResources().getDisplayMetrics().density;
            int margin = (int) (16 * density);
            v.setPadding(
                systemBars.left + margin,
                systemBars.top + margin,
                systemBars.right + margin,
                systemBars.bottom + margin
            );
            return insets;
        });

        usbStatus = findViewById(R.id.usbStatus);
        btStatus = findViewById(R.id.btStatus);
        tvLog = findViewById(R.id.tvLog);
        baudSpinner = findViewById(R.id.baudSpinner);
        switchDTR = findViewById(R.id.switchDTR);
        switchRTS = findViewById(R.id.switchRTS);
        btnRefreshUsb = findViewById(R.id.btnRefreshUsb);
        btnReset = findViewById(R.id.btnReset);
        btnBootloader = findViewById(R.id.btnBootloader);

        setupBaudSpinner();
        setupListeners();
        checkPermissions();

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(usbReceiver, filter);
        }

        Intent intent = new Intent(this, BridgeService.class);
        startService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private void setupBaudSpinner() {
        Integer[] bauds = {9600, 19200, 38400, 57600, 115200, 230400, 460800, 921600};
        ArrayAdapter<Integer> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, bauds);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        baudSpinner.setAdapter(adapter);
        baudSpinner.setSelection(4); // 115200

        baudSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isBound) {
                    int baud = (int) parent.getItemAtPosition(position);
                    bridgeService.getUsbSerialManager().setBaudRate(baud);
                    log("Baud rate set to " + baud);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupListeners() {
        btnRefreshUsb.setOnClickListener(v -> discoverUsbDevices());

        switchDTR.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isBound) bridgeService.getUsbSerialManager().setDTR(isChecked);
        });

        switchRTS.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isBound) bridgeService.getUsbSerialManager().setRTS(isChecked);
        });

        btnReset.setOnClickListener(v -> resetEsp32());
        btnBootloader.setOnClickListener(v -> bootloaderEsp32());

        findViewById(R.id.btnMakeDiscoverable).setOnClickListener(v -> makeDiscoverable());
    }

    private void makeDiscoverable() {
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        startActivity(discoverableIntent);
        log("Requesting discoverability...");
    }

    private void discoverUsbDevices() {
        if (!isBound) return;
        UsbSerialManager usb = bridgeService.getUsbSerialManager();
        if (usb.isConnected()) {
            usb.close();
            log("USB Disconnected manually");
            updateStatus();
            return;
        }

        List<UsbDevice> devices = usb.findDevices();
        if (devices.isEmpty()) {
            log("No USB Serial devices found");
            Toast.makeText(this, "No USB Serial devices found", Toast.LENGTH_SHORT).show();
        } else {
            UsbDevice device = devices.get(0); // For simplicity, pick first
            requestUsbPermission(device);
        }
    }

    private void requestUsbPermission(UsbDevice device) {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (usbManager.hasPermission(device)) {
            bridgeService.getUsbSerialManager().open(device);
            updateStatus();
        } else {
            PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
            usbManager.requestPermission(device, permissionIntent);
        }
    }

    private void resetEsp32() {
        if (!isBound) return;
        log("Resetting ESP32...");
        UsbSerialManager usb = bridgeService.getUsbSerialManager();
        Handler handler = new Handler(Looper.getMainLooper());

        handler.post(() -> {
            // 1. Idle
            usb.setDTR(true);
            usb.setRTS(true);

            handler.postDelayed(() -> {
                // 2. Assert RESET low
                usb.setDTR(false);

                handler.postDelayed(() -> {
                    // 3. Release RESET
                    usb.setDTR(true);
                    log("Reset done");
                    syncSwitches();
                }, 100);
            }, 100);
        });
    }


    private void bootloaderEsp32() {
        if (!isBound) return;
        log("Entering Bootloader...");
        UsbSerialManager usb = bridgeService.getUsbSerialManager();
        Handler handler = new Handler(Looper.getMainLooper());
        
        handler.post(() -> {
            // 1. Idle
            usb.setDTR(true);
            usb.setRTS(true);
            
            handler.postDelayed(() -> {
                // 2. Assert RESET low
                usb.setDTR(false);
                
                handler.postDelayed(() -> {
                    // 3. Release RESET (will stay low due to capacitor), pull IO0 Low
                    usb.setRTS(false);
                    usb.setDTR(true);

                    handler.postDelayed(() -> {
                        // 4. Release IO0
                        usb.setRTS(true);
                        log("Bootloader mode should be active");
                        syncSwitches();
                    }, 100);
                }, 100);
            }, 100);
        });
    }

    private void checkPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
        } else {
            permissions.add(Manifest.permission.BLUETOOTH);
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN);
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        List<String> toRequest = new ArrayList<>();
        for (String p : permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                toRequest.add(p);
            }
        }

        if (!toRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                log("Permissions granted");
            } else {
                log("Permissions denied");
                Toast.makeText(this, "Permissions required for Bluetooth", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void syncSwitches() {
        if (!isBound) return;
        UsbSerialManager usb = bridgeService.getUsbSerialManager();
        if (!usb.isConnected()) return;
        
        boolean dtr = usb.getDTR();
        boolean rts = usb.getRTS();
        
        runOnUiThread(() -> {
            switchDTR.setOnCheckedChangeListener(null);
            switchRTS.setOnCheckedChangeListener(null);
            switchDTR.setChecked(dtr);
            switchRTS.setChecked(rts);
            setupListeners(); // Re-bind listeners
        });
    }

    private void updateStatus() {
        if (!isBound) return;
        boolean usbConnected = bridgeService.getUsbSerialManager().isConnected();
        boolean btConnected = bridgeService.getBluetoothServer().isConnected();

        usbStatus.setText(usbConnected ? "Connected" : "Disconnected");
        usbStatus.setTextColor(usbConnected ? 0xFF00AA00 : 0xFFFF0000);
        btnRefreshUsb.setText(usbConnected ? "Disconnect USB Device" : "Connect USB Device");

        btStatus.setText(btConnected ? "Connected" : "Disconnected");
        btStatus.setTextColor(btConnected ? 0xFF00AA00 : 0xFFFF0000);
    }

    private void log(String message) {
        runOnUiThread(() -> {
            tvLog.append(message + "\n");
            final View scroll = (View) tvLog.getParent();
            scroll.post(() -> {
                if (scroll instanceof android.widget.ScrollView) {
                    ((android.widget.ScrollView) scroll).fullScroll(View.FOCUS_DOWN);
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(usbReceiver);
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
        super.onDestroy();
    }
}
