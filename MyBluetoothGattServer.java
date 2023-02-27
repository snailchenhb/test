import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

public class MyBluetoothGattServer extends BluetoothGattServerCallback {
    private static final String TAG = "MyBluetoothGattServer";
    private static final int DISCONNECT_TIMEOUT = 5000; // 5 seconds
    private static final int MSG_DISCONNECT_TIMEOUT = 1;

    private BluetoothGattServer mGattServer;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothServerSocket mServerSocket;
    private Handler mHandler;
    private Context mContext;
    private boolean mIsRunning;
    private boolean mDisconnectTimeoutEnabled;
    private BluetoothDevice mConnectedDevice;

    public MyBluetoothGattServer(Context context) {
        mContext = context;
        mBluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_DISCONNECT_TIMEOUT:
                        if (mConnectedDevice != null) {
                            disconnectDevice(mConnectedDevice);
                            mConnectedDevice = null;
                        }
                        break;
                    default:
                        super.handleMessage(message);
                        break;
                }
            }
        };
    }

    public void start() {
        if (mIsRunning) {
            Log.w(TAG, "Server already running");
            return;
        }

        mIsRunning = true;
        mDisconnectTimeoutEnabled = true;
        startGattServer();
        startAdvertising();
    }

    public void stop() {
        if (!mIsRunning) {
            Log.w(TAG, "Server not running");
            return;
        }

        mIsRunning = false;
        mDisconnectTimeoutEnabled = false;
        mHandler.removeCallbacksAndMessages(null);
        stopAdvertising();
        stopGattServer();
    }

    public boolean isRunning() {
        return mIsRunning;
    }

    public void setDisconnectTimeoutEnabled(boolean enabled) {
        mDisconnectTimeoutEnabled = enabled;
    }

    public void disconnectDevice(BluetoothDevice device) {
        Log.i(TAG, "Disconnecting from device " + device.getAddress());
        mGattServer.cancelConnection(device);
    }

    private void startAdvertising() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Log.e(TAG, "Bluetooth adapter not enabled or not available");
            return;
        }

        Log.i(TAG, "Starting advertising");
        adapter.setName("My Bluetooth Device");
        adapter.setScanMode(BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
        startGattServer();

        try {
            mServerSocket = adapter.listenUsingRfcommWithServiceRecord("My Bluetooth Server", UUID.fromString("your_uuid_here"));
        } catch (IOException e) {
            Log.e(TAG, "Failed to create server socket: " + e.getMessage());
            return;
        }

        new Thread(new Runnable() {
            @Override
