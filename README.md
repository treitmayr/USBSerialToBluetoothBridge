# USB Serial to Bluetooth Bridge

An Android application that bridges data between a USB-OTG serial adapter and a Bluetooth Classic (SPP) connection. Designed specifically for developers working with microcontrollers like the ESP32.

## Features

*   **Bidirectional Bridging**: Transparent data relay between USB and Bluetooth.
*   **Dynamic Serial Control**: Adjust Baud Rate (up to 921600), DTR, and RTS directly from the app.
*   **ESP32 Special Sequences**: Dedicated buttons to trigger **Reset** and **Bootloader** mode using standard DTR/RTS timing.
*   **Foreground Service**: Remains active in the background to prevent connection drops.
*   **Real-time Logging**: In-app terminal view showing status changes and bridge activity.

## Installation

1.  Clone the repository.
2.  Open in Android Studio or build via terminal:
    ```bash
    ./gradlew assembleDebug
    ```
3.  Install the APK located at `app/build/outputs/apk/debug/app-debug.apk`.

> **Note**: For production/release builds, you must configure a signing key in `app/build.gradle`.

## Usage

### 1. Android Setup
1.  Connect your USB-Serial adapter or ESP32 board using a **USB-OTG** cable.
2.  Open the app and grant required permissions (Bluetooth & Notifications).
3.  Tap **Connect USB Device** and allow the system USB access.
4.  Tap **Make Phone Discoverable (SPP)** to ensure your PC can find the service.

### 2. Linux Connection
To treat the Android bridge as a local serial port (`/dev/rfcomm0`):

1.  **Pair and Trust**:
    ```bash
    bluetoothctl
    [bluetooth]# scan on
    [bluetooth]# pair [PHONE_MAC]
    [bluetooth]# trust [PHONE_MAC]
    ```
2.  **Bind the device**:
    ```bash
    sudo rfcomm bind 0 [PHONE_MAC] 1
    ```
3.  **Access Serial**:
    Use any terminal emulator (e.g., `picocom -b 115200 /dev/rfcomm0` or `screen /dev/rfcomm0`).

### 3. ESP32 Controls
*   **Reset**: Pulls the EN line to reboot the chip.
*   **Bootloader**: Executes the sequence (DTR/RTS manipulation) to enter download mode for flashing.

## Troubleshooting
*   **Disconnections**: Ensure **Battery Optimization** is set to "Unrestricted" for this app and the system "Bluetooth" app.
*   **Permissions**: If Linux returns "Permission Denied", add your user to the `dialout` group: `sudo usermod -aG dialout $USER`.
