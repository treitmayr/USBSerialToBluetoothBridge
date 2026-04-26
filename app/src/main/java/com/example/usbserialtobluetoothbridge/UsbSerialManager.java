package com.example.usbserialtobluetoothbridge;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class UsbSerialManager implements SerialInputOutputManager.Listener {

    public interface Listener {
        void onUsbDataReceived(byte[] data);
        void onUsbError(String message);
        void onUsbStateChanged(boolean connected);
    }

    private static final String TAG = "UsbSerialManager";
    private final Context context;
    private final Listener listener;
    private final UsbManager usbManager;
    private UsbSerialPort usbSerialPort;
    private SerialInputOutputManager ioManager;

    public UsbSerialManager(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    }

    public List<UsbDevice> findDevices() {
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        List<UsbDevice> devices = new ArrayList<>();
        for (UsbSerialDriver driver : availableDrivers) {
            devices.add(driver.getDevice());
        }
        return devices;
    }

    public void open(UsbDevice device) {
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if (driver == null) {
            listener.onUsbError("No driver for device");
            return;
        }

        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            listener.onUsbError("Could not open device connection");
            return;
        }

        UsbSerialPort port = driver.getPorts().get(0);
        try {
            port.open(connection);
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            usbSerialPort = port;

            ioManager = new SerialInputOutputManager(port, this);
            ioManager.start();

            listener.onUsbStateChanged(true);
        } catch (IOException e) {
            Log.e(TAG, "Error opening USB port", e);
            listener.onUsbError("Error opening USB port: " + e.getMessage());
        }
    }

    public void close() {
        if (ioManager != null) {
            ioManager.stop();
            ioManager = null;
        }
        if (usbSerialPort != null) {
            try {
                usbSerialPort.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing USB port", e);
            }
            usbSerialPort = null;
        }
        listener.onUsbStateChanged(false);
    }

    public void write(byte[] data) {
        if (usbSerialPort != null) {
            try {
                usbSerialPort.write(data, 1000);
            } catch (IOException e) {
                Log.e(TAG, "Error writing to USB port", e);
                listener.onUsbError("Write error: " + e.getMessage());
            }
        }
    }

    public void setBaudRate(int baudRate) {
        if (usbSerialPort != null) {
            try {
                usbSerialPort.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            } catch (IOException e) {
                Log.e(TAG, "Error setting baud rate", e);
                listener.onUsbError("Baud rate error: " + e.getMessage());
            }
        }
    }

    public void setDTR(boolean value) {
        if (usbSerialPort != null) {
            try {
                usbSerialPort.setDTR(value);
            } catch (IOException e) {
                Log.e(TAG, "Error setting DTR", e);
            }
        }
    }

    public void setRTS(boolean value) {
        if (usbSerialPort != null) {
            try {
                usbSerialPort.setRTS(value);
            } catch (IOException e) {
                Log.e(TAG, "Error setting RTS", e);
            }
        }
    }

    public boolean isConnected() {
        return usbSerialPort != null;
    }

    public boolean getDTR() {
        if (usbSerialPort != null) {
            try {
                return usbSerialPort.getDTR();
            } catch (IOException e) {
                Log.e(TAG, "Error getting DTR", e);
            }
        }
        return false;
    }

    public boolean getRTS() {
        if (usbSerialPort != null) {
            try {
                return usbSerialPort.getRTS();
            } catch (IOException e) {
                Log.e(TAG, "Error getting RTS", e);
            }
        }
        return false;
    }

    @Override
    public void onNewData(byte[] data) {
        listener.onUsbDataReceived(data);
    }

    @Override
    public void onRunError(Exception e) {
        Log.e(TAG, "USB runtime error", e);
        listener.onUsbError("USB runtime error: " + e.getMessage());
        close();
    }
}
