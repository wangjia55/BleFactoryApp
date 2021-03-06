package com.cvte.ble.sdk.core;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.cvte.ble.sdk.entity.BleConnectInfo;
import com.cvte.ble.sdk.listener.BleConnectCallback;
import com.cvte.ble.sdk.listener.BleReadCallback;
import com.cvte.ble.sdk.listener.BleRssiCallback;
import com.cvte.ble.sdk.listener.BleWriteCallback;
import com.cvte.ble.sdk.states.BluetoothState;
import com.cvte.ble.sdk.states.ConnectState;
import com.cvte.ble.sdk.states.ErrorStatus;
import com.cvte.ble.sdk.utils.BleLogUtils;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 蓝牙连接过程的核心类
 */
@TargetApi(18)
public class GoogleBle {

    private static final String TAG = "google gatt";
    private static final int MSG_DATA_RECEIVE = 0x11224;
    private static final int MSG_CONNECTION_CHECK = 0x11225;
    private static final int MSG_CONNECT_ERROR = 0x11226;
    private static final int MSG_CONNECT_SUCCESS = 0x11227;
    private static final int MSG_RSSI_READ_SUCCESS = 0x11228;
    private static final int MSG_RSSI_READ_ERROR = 0x11229;
    private static final int MSG_READ_DATA_RECEIVE = 0x11230;
    private static final int MSG_READ_DATA_ERROR = 0x11231;
    private static final int CONNECTION_CHECK_TIME = 25 * 1000;

    private static final String PARAM_RSSI = "rssi";
    private static final String PARAM_BYTE = "byte";
    private static final String PARAM_ERROR_CODE = "code";
    private static final String PARAM_ERROR_REASON = "reason";

    private Context mContext;
    private BleConnectInfo mBleConnectInfo;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothManager mBluetoothManager;
    private BleRssiCallback mBleRssiCallback;
    private BleReadCallback mBleReadCallback;
    private BleWriteCallback mBleWriteCallback;
    private BleConnectCallback mBleConnectCallback;
    private BluetoothGattCharacteristic mReadCharacteristic;
    private BluetoothGattCharacteristic mWriteCharacteristic;
    private ConnectState mConnectState = ConnectState.Disconnect;
    private BluetoothState mBluetoothState = BluetoothState.Bluetooth_Off;

    private final Queue<byte[]> sWriteQueue = new ConcurrentLinkedQueue<byte[]>();
    private boolean sIsWriting = false;
    private GoogleHandler mHandler;

    public GoogleBle(Context context) {
        BleLogUtils.LOGD(TAG, "google ble init");
        mContext = context;
        mHandler = new GoogleHandler(mContext.getApplicationContext().getMainLooper());
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = mBluetoothManager.getAdapter();
            mContext.registerReceiver(mBlueStateBroadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
            if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
                mBluetoothState = BluetoothState.Bluetooth_On;
            }
        }
    }


    private BroadcastReceiver mBlueStateBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            int blueState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
            switch (blueState) {
                case BluetoothAdapter.STATE_OFF:
                    mBluetoothState = BluetoothState.Bluetooth_Off;
                    notifyBluetoothOff();
                    break;
                case BluetoothAdapter.STATE_ON:
                    mBluetoothState = BluetoothState.Bluetooth_On;
                    break;
                default:
                    break;
            }
        }
    };

    private void notifyBluetoothOff() {

        if (mConnectState != ConnectState.Disconnect) {
            disconnect();
            if (mBleConnectCallback != null) {
                mBleConnectCallback.onConnectError(mBleConnectInfo, ErrorStatus.BLUETOOTH_NO_OPEN, "bluetooth is no open");
            }
        }
    }

    private class GoogleHandler extends Handler {
        public GoogleHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG_CONNECTION_CHECK:
                    connectFail();
                    break;
                case MSG_DATA_RECEIVE:
                    callOnWriteCallback(msg);
                    break;
                case MSG_READ_DATA_RECEIVE:
                    callOnReadDataCallBack(msg);
                    break;
                case MSG_READ_DATA_ERROR:
                    callOnReadErrorCallBack(msg);
                    break;
                case MSG_CONNECT_ERROR:
                    callOnConnectError(msg);
                    break;
                case MSG_CONNECT_SUCCESS:
                    callOnConnectSuccess();
                    break;
                case MSG_RSSI_READ_SUCCESS:
                    callOnRssiReadSuccess(msg);
                    break;
                case MSG_RSSI_READ_ERROR:
                    callOnRssiReadError(msg);
                default:
                    break;
            }
        }
    }

    private void callOnReadErrorCallBack(Message msg) {
        if (mBleReadCallback != null) {
            Bundle bundle = msg.getData();
            int errorCode = bundle.getInt(PARAM_ERROR_CODE);
            String reason = bundle.getString(PARAM_ERROR_REASON);
            mBleReadCallback.onReadFail(errorCode, reason);
        }
    }


    private void callOnRssiReadError(Message msg) {
        if (mBleRssiCallback != null) {
            Bundle bundle = msg.getData();
            int errorCode = bundle.getInt(PARAM_ERROR_CODE);
            String reason = bundle.getString(PARAM_ERROR_REASON);
            mBleRssiCallback.onBleRssiReadError(errorCode, reason);
        }
    }

    private void callOnRssiReadSuccess(Message msg) {
        if (mBleRssiCallback != null) {
            Bundle bundle = msg.getData();
            int rssi = bundle.getInt(PARAM_RSSI);
            mBleRssiCallback.onBleRssiRead(rssi);
        }
    }

    private void callOnConnectSuccess() {
        if (mBleConnectCallback != null) {
            mBleConnectCallback.onConnectSuccess(mBleConnectInfo, mBluetoothDevice);
        }
    }

    private void callOnConnectError(Message msg) {
        Bundle bundle = msg.getData();
        int error = bundle.getInt(PARAM_ERROR_CODE);
        String reason = bundle.getString(PARAM_ERROR_REASON);
        if (mBleConnectCallback != null) {
            mBleConnectCallback.onConnectError(mBleConnectInfo, error, reason);
        }
    }


    private void connectFail() {
        if (mConnectState != ConnectState.Connected) {
            disconnect();
            if (mBleConnectCallback != null) {
                mBleConnectCallback.onConnectError(mBleConnectInfo, ErrorStatus.CONNECT_TIME_OUT, "connect time out");
            }
        }
    }

    private void callOnWriteCallback(Message msg) {
        Bundle dataReceiveBundle = msg.getData();
        byte[] rawData = dataReceiveBundle.getByteArray(PARAM_BYTE);
        if (mBleWriteCallback != null) {
            mBleWriteCallback.onWriteSuccess(rawData);
        }
    }


    private void callOnReadDataCallBack(Message msg) {
        Bundle dataReceiveBundle = msg.getData();
        byte[] rawData = dataReceiveBundle.getByteArray(PARAM_BYTE);
        if (mBleReadCallback != null) {
            mBleReadCallback.onReadSuccess(rawData);
        }
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            BleLogUtils.LOGD(TAG, "connection state change " + "gatt status " + status + " bluetoothProfile new State " + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                BleLogUtils.LOGD(TAG, "connected to GATT server and discovery service");
                mBluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                BleLogUtils.LOGD(TAG, "connection state change disconnect" + "gatt status " + status + " bluetoothProfile new State " + newState);
                disconnect();
                mHandler.removeMessages(MSG_CONNECTION_CHECK);
                sendConnectErrorMessage(ErrorStatus.CONNECT_STATE_FAIL, "fail at onConnectionStateChange status " + status + " newState " + newState);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(mBleConnectInfo.getServiceUUID());
                if (service != null) {
                    BleLogUtils.LOGD(TAG, "service discovery ");
                    if (mBleConnectInfo.getReadCharacteristicUUID() != null) {
                        mReadCharacteristic = service.getCharacteristic(mBleConnectInfo.getReadCharacteristicUUID());
                        setCharacteristicNotification(mReadCharacteristic);
                    }
                    if (mBleConnectInfo.getWriteCharacteristicUUID() != null) {
                        mWriteCharacteristic = service.getCharacteristic(mBleConnectInfo.getWriteCharacteristicUUID());
                    }
                }
                mHandler.removeMessages(MSG_CONNECTION_CHECK);
                mConnectState = ConnectState.Connected;
                sendConnectSuccessMessage();

            } else if (status == BluetoothGatt.GATT_FAILURE) {
                BleLogUtils.LOGD(TAG, "onServicesDiscovered fail: " + status);
                disconnect();
                mHandler.removeMessages(MSG_CONNECTION_CHECK);
                sendConnectErrorMessage(ErrorStatus.CONNECT_STATE_FAIL, "fail at onServicesDiscovered status " + status);

            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            BleLogUtils.LOGD(TAG, "onCharacteristicWrite: " + status);
            sIsWriting = false;
            nextWrite();
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            BleLogUtils.LOGD(TAG, "onCharacteristicRead: " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                sendReadDataSuccessMessage(characteristic.getValue());
            } else {
                sendReadDataErrorMessage(status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            BleLogUtils.LOGD(TAG, "onCharacteristicChanged");
            sendRawData(characteristic.getValue());
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                sendRssiReadSuccessMessage(rssi);
            } else {
                sendRssiReadErrorMessage(status);
            }
        }
    };

    private void sendReadDataErrorMessage(int status) {
        Message message = Message.obtain();
        message.what = MSG_READ_DATA_ERROR;
        Bundle bundle = new Bundle();
        bundle.putInt(PARAM_ERROR_CODE, ErrorStatus.GATT_FAIL);
        bundle.putString(PARAM_ERROR_REASON, "status is not gatt success, status " + status);
        message.setData(bundle);
        mHandler.sendMessage(message);
    }

    private void sendReadDataSuccessMessage(byte[] value) {
        Message message = Message.obtain();
        Bundle bundle = new Bundle();
        bundle.putByteArray(PARAM_BYTE, value);
        message.what = MSG_READ_DATA_RECEIVE;
        message.setData(bundle);
        mHandler.sendMessage(message);
    }

    private void sendRssiReadErrorMessage(int status) {
        Message message = Message.obtain();
        message.what = MSG_RSSI_READ_ERROR;
        Bundle bundle = new Bundle();
        bundle.putInt(PARAM_ERROR_CODE, ErrorStatus.GATT_FAIL);
        bundle.putString(PARAM_ERROR_REASON, "status is not gatt success, status " + status);
        message.setData(bundle);
        mHandler.sendMessage(message);
    }

    private void sendRssiReadSuccessMessage(int rssi) {
        Message message = Message.obtain();
        message.what = MSG_RSSI_READ_SUCCESS;
        Bundle bundle = new Bundle();
        bundle.putInt(PARAM_RSSI, rssi);
        message.setData(bundle);
        mHandler.sendMessage(message);
    }

    private void sendConnectSuccessMessage() {
        mHandler.sendEmptyMessage(MSG_CONNECT_SUCCESS);
    }

    private void sendConnectErrorMessage(int error, String reason) {
        Message message = Message.obtain();
        message.what = MSG_CONNECT_ERROR;
        Bundle bundle = new Bundle();
        bundle.putInt(PARAM_ERROR_CODE, error);
        bundle.putString(PARAM_ERROR_REASON, reason);
        message.setData(bundle);
        mHandler.sendMessage(message);
    }


    public void dispose() {
        disconnect();
        if (mBleConnectCallback != null) {
            mBleConnectCallback.onConnectError(mBleConnectInfo, ErrorStatus.DISCONNECT_BY_USER, "Disconnect handly");
        }
        mContext.unregisterReceiver(mBlueStateBroadcastReceiver);
        mBluetoothManager = null;
        mBluetoothGatt = null;
        mConnectState = ConnectState.Disconnect;
    }

    public void connect(BluetoothDevice device, BleConnectInfo bleConnectInfo,
                        BleConnectCallback bleConnectCallback, final boolean isAuto) {
        mBleConnectCallback = bleConnectCallback;
        mBluetoothDevice = device;
        mBleConnectInfo = bleConnectInfo;
        if (mBluetoothState == BluetoothState.Bluetooth_Off) {
            disconnect();
            if (mBleConnectCallback != null) {
                mBleConnectCallback.onConnectError(mBleConnectInfo, ErrorStatus.BLUETOOTH_NO_OPEN, "bluetooth is no open");
            }
            return;
        }

        if (mConnectState != ConnectState.Disconnect) {
            if (mBleConnectCallback != null) {
                if (mConnectState == ConnectState.Connecting) {
                    mBleConnectCallback.onConnectError(mBleConnectInfo, ErrorStatus.ALREADY_CONNECTING, "current state is connecting");
                }

                if (mConnectState == ConnectState.Connected) {
                    mBleConnectCallback.onConnectError(mBleConnectInfo, ErrorStatus.ALREADY_CONNECTED, "current state is connecting");
                }
            }
            return;
        }
        BleLogUtils.LOGD("bleManager", "real connect");

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mBluetoothGatt = mBluetoothDevice.connectGatt(mContext, isAuto, mGattCallback);
                mConnectState = ConnectState.Connecting;
            }
        });

        mHandler.sendEmptyMessageDelayed(MSG_CONNECTION_CHECK, CONNECTION_CHECK_TIME);
    }

    private void disconnect() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
        mConnectState = ConnectState.Disconnect;
    }

    protected void sendRawData(byte[] value) {
        Message message = Message.obtain();
        Bundle bundle = new Bundle();
        bundle.putByteArray(PARAM_BYTE, value);
        message.what = MSG_DATA_RECEIVE;
        message.setData(bundle);
        mHandler.sendMessage(message);
    }

    private void setCharacteristicNotification(BluetoothGattCharacteristic characteristic) {
        mBluetoothGatt.setCharacteristicNotification(characteristic, true);
        if (mBleConnectInfo.getCharacteristicDescriptorUUID() != null) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(mBleConnectInfo.getCharacteristicDescriptorUUID());
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
    }


    public synchronized void write(byte[] bytes, BleWriteCallback bleWriteCallback) {
        mBleWriteCallback = bleWriteCallback;
        if (mBluetoothState == BluetoothState.Bluetooth_Off) {
            if (mBleWriteCallback != null) {
                mBleWriteCallback.onWriteFail(ErrorStatus.BLUETOOTH_NO_OPEN, "bluetooth is no open");
            }
            return;
        }

        if (mConnectState != ConnectState.Connected) {
            if (mBleWriteCallback != null) {
                mBleWriteCallback.onWriteFail(ErrorStatus.STATE_DISCONNECT, "current state is not connected");
            }
            return;
        }

        if (sWriteQueue.isEmpty() && !sIsWriting) {
            doWrite(bytes);
        } else {
            sWriteQueue.add(bytes);
        }
    }

    private synchronized void nextWrite() {
        if (!sWriteQueue.isEmpty() && !sIsWriting) {
            doWrite(sWriteQueue.poll());
        }
    }

    private synchronized void doWrite(byte[] bytes) {
        if (mBluetoothState == BluetoothState.Bluetooth_Off) {
            if (mBleWriteCallback != null) {
                mBleWriteCallback.onWriteFail(ErrorStatus.BLUETOOTH_NO_OPEN, "bluetooth is no open");
            }
            return;
        }

        if (mConnectState != ConnectState.Connected) {
            if (mBleWriteCallback != null) {
                mBleWriteCallback.onWriteFail(ErrorStatus.STATE_DISCONNECT, "current state is not connected");
            }
            return;
        }

        if (mBluetoothGatt != null && mWriteCharacteristic != null) {
            mWriteCharacteristic.setValue(bytes);
            boolean success = mBluetoothGatt.writeCharacteristic(mWriteCharacteristic);
            BleLogUtils.LOGD(TAG, "success write:" + success);
        } else {
            if (mBleWriteCallback != null) {
                mBleWriteCallback.onWriteFail(ErrorStatus.GATT_NULL, "bluetooth gatt is null or write characteristic is null");
            }
            BleLogUtils.LOGD(TAG, "write date error. connected state: ");
        }
    }

    public void readRssi() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.readRemoteRssi();
        }
    }

    public void readData(BleReadCallback bleReadCallback) {
        mBleReadCallback = bleReadCallback;
        if (mBluetoothState == BluetoothState.Bluetooth_Off) {
            if (mBleReadCallback != null) {
                mBleReadCallback.onReadFail(ErrorStatus.BLUETOOTH_NO_OPEN, "bluetooth is no open");
            }
            return;
        }

        if (mConnectState != ConnectState.Connected) {
            if (mBleReadCallback != null) {
                mBleReadCallback.onReadFail(ErrorStatus.STATE_DISCONNECT, "current state is not connected");
            }
            return;
        }
        if (mBluetoothGatt != null && mReadCharacteristic != null) {
            mBluetoothGatt.readCharacteristic(mReadCharacteristic);
        } else {
            if (mBleReadCallback != null) {
                mBleReadCallback.onReadFail(ErrorStatus.GATT_NULL, "bluetooth gatt is null or read characteristic is null");
            }
        }
    }


    public ConnectState getConnectState() {
        return mConnectState;
    }

    public void setBleRssiCallback(BleRssiCallback bleRssiCallback) {
        this.mBleRssiCallback = bleRssiCallback;
    }

    public void setConnectCallback(BleConnectCallback bleConnectCallback) {
        mBleConnectCallback = bleConnectCallback;
    }
}
