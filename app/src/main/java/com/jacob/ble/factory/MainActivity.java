package com.jacob.ble.factory;

import android.animation.ObjectAnimator;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import com.cvte.ble.sdk.core.BleCommand;
import com.cvte.ble.sdk.core.BleSdkManager;
import com.cvte.ble.sdk.entity.BleConnectDevice;
import com.cvte.ble.sdk.entity.BleConnectInfo;
import com.cvte.ble.sdk.exception.NotSupportBleException;
import com.cvte.ble.sdk.listener.BleConnectCallback;
import com.cvte.ble.sdk.states.BluetoothState;
import com.cvte.ble.sdk.utils.BleUtils;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.*;


public class MainActivity extends FragmentActivity {
    public static final String TAG = "MainActivity";
    public static final int SCAN_DURATION = 3000;
    public   int STOP_DURATION = 25000;
    public static final int SHUTDOWN_DURATION = 5000;
    public static final int DEVICE_COUNT = 4;
    private static final int BLUETOOTH_OPEN_REQUEST = 0x1008;
    public static final String DEVICE_IMSI = "4600400";
    private Map<String, BleConnectDevice> mAllDeviceMap = new HashMap<String, BleConnectDevice>();

    private BleSdkManager mBleManager;
    private Handler mHandler = new Handler();
    private ObjectAnimator mBleScanAnim;
    private BleTrackingView mBleTrackView;
    private TextView mTextViewBleState;
    private ImageView mImageViewOperation;
    private TextView mTextViewCount;
    private PowerManager.WakeLock mWakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        STOP_DURATION = STOP_DURATION+new Random().nextInt(1000);
        //keep screen always on
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");

        mBleTrackView = (BleTrackingView) findViewById(R.id.image_view_ble_tracking);
        mTextViewBleState = (TextView) findViewById(R.id.text_view_ble_state);
        mImageViewOperation = (ImageView) findViewById(R.id.text_view_oper);
        mTextViewCount = (TextView) findViewById(R.id.text_view_count);
        mImageViewOperation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (BleUtils.getCurrentBluetoothState(getApplicationContext()) == BluetoothState.Bluetooth_Off) {
                    Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(intent, BLUETOOTH_OPEN_REQUEST);
                    mImageViewOperation.setImageResource(R.mipmap.ic_switch_on);
                    mTextViewBleState.setText("已开启");
                } else {
                    mImageViewOperation.setImageResource(R.mipmap.ic_switch_off);
                    mTextViewBleState.setText("已关闭");
                    stopScan();
                    mBleManager.closeBluetooth();
                    cancelAnim();
                    mAllDeviceMap.clear();
                    mTextViewCount.setText("0");
                }
            }
        });


        registerReceiver(mBlueStateBroadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        try {
            mBleManager = BleSdkManager.newInstance(getApplicationContext());
            mBleManager.init();
        } catch (NotSupportBleException e) {
            e.printStackTrace();
        }

        if (BleUtils.getCurrentBluetoothState(getApplicationContext()) == BluetoothState.Bluetooth_Off) {
            mImageViewOperation.setImageResource(R.mipmap.ic_switch_off);
            mTextViewBleState.setText("已关闭");
        } else {
            mImageViewOperation.setImageResource(R.mipmap.ic_switch_on);
            mTextViewBleState.setText("已开启");
        }

        mHandler.post(mShutDownRunnable);
    }

    private BroadcastReceiver mBlueStateBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            int blueState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
            switch (blueState) {
                case BluetoothAdapter.STATE_ON:
                    startScanAnim();
                    startScan();
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        mWakeLock.acquire();
    }


    @Override
    protected void onPause() {
        super.onPause();
        mWakeLock.release();
    }

    private void startScan() {
        LogUtils.LOGE(TAG, "startScan");
        mBleManager.startScan(mBleScanCallback);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopScan();
            }
        }, SCAN_DURATION);
        printHashMap();
    }

    private void stopScan() {
        LogUtils.LOGE(TAG, "stopScan");
        mBleManager.stopScan();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                startScan();
            }
        }, STOP_DURATION);
    }


    private Runnable mShutDownRunnable = new Runnable() {
        @Override
        public void run() {
            mTextViewCount.setText(String.valueOf(mAllDeviceMap.size()));
            Set<String> keSet = mAllDeviceMap.keySet();
            Iterator iterator = keSet.iterator();
            if (mAllDeviceMap != null && mAllDeviceMap.size() > 0) {
                BleConnectDevice connectDevice = mAllDeviceMap.get(iterator.next());
                connectDevice.getGoogleBle().connect(connectDevice.getDevice(),
                        connectDevice.getBleConnectInfo(), mConnectCallBack, false);
            }
            mHandler.postDelayed(mShutDownRunnable, SHUTDOWN_DURATION);

        }
    };

    private BleConnectCallback mConnectCallBack = new BleConnectCallback() {
        @Override
        public void onConnectSuccess(BleConnectInfo bleConnectInfo, BluetoothDevice bluetoothDevice) {
            LogUtils.LOGE("TAG", "****** onConnectSuccess *******");

            BleConnectDevice connectDevice = mAllDeviceMap.get(bleConnectInfo.getSingleTag());
            String command = bleConnectInfo.getBroadCommand() + "0";
            LogUtils.LOGE(TAG, "" + command);
            if (connectDevice != null) {
                connectDevice.getGoogleBle().write(BleCommand.getVerifyCommand(command.trim()), null);
                mAllDeviceMap.remove(bleConnectInfo.getSingleTag());
                mTextViewCount.setText(String.valueOf(mAllDeviceMap.size()));
            }
        }

        @Override
        public void onConnectError(BleConnectInfo bleConnectInfo, int errorCode, String reason) {
            LogUtils.LOGE("TAG", "****** onConnectError *******");
            mAllDeviceMap.remove(bleConnectInfo.getSingleTag());
            mTextViewCount.setText(String.valueOf(mAllDeviceMap.size()));
        }

        @Override
        public void onDeviceFound(BleConnectInfo bleConnectInfo, BluetoothDevice bluetoothDevice) {
            LogUtils.LOGE("TAG", "****** onDeviceFound *******");
        }
    };
    /**
     * 蓝牙扫描设备的回调，
     */
    private BluetoothAdapter.LeScanCallback mBleScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            int startIndex = 9;
            byte[] imbtBytes = Arrays.copyOfRange(scanRecord, startIndex, startIndex + 15);
            String scanImbt = new String(imbtBytes);
            if (scanImbt.startsWith(DEVICE_IMSI) && !mAllDeviceMap.containsKey(DEVICE_IMSI)) {
//                LogUtils.LOGE(TAG, "find device");
                if (mAllDeviceMap.size() <= DEVICE_COUNT) {
                    BleConnectInfo bleConnectInfo = new TrackerConnectInfo(scanImbt, scanImbt);
                    BleConnectDevice bleConnectDevice = new BleConnectDevice(getApplicationContext(), device, bleConnectInfo);
                    mAllDeviceMap.put(scanImbt, bleConnectDevice);
                    mTextViewCount.setText(String.valueOf(mAllDeviceMap.size()));
                }

            }
        }
    };


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBleScanAnim != null) {
            mBleScanAnim.cancel();
        }
        mAllDeviceMap.clear();
    }

    /**
     * 请求开关蓝牙的回调
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BLUETOOTH_OPEN_REQUEST) {
            if (resultCode == RESULT_OK) {
                //do nothing here
            } else if (resultCode == RESULT_CANCELED) {
                mBleManager.disConnectAll();
                finish();
            }
        }

    }

    /**
     * 开始执行扫描动画 , 在这个界面一打开就开始执行
     */
    private void startScanAnim() {
        if (mBleScanAnim == null) {
            mBleScanAnim = ObjectAnimator.ofFloat(mBleTrackView, View.ROTATION, 0, 360);
            mBleScanAnim.setDuration(8000);
            mBleScanAnim.setInterpolator(new LinearInterpolator());
            mBleScanAnim.setRepeatCount(Animation.INFINITE);
        }
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                mBleScanAnim.start();
            }
        }, 600);
    }


    private void cancelAnim() {
        if (mBleScanAnim != null) {
            mBleScanAnim.cancel();
        }
    }


    private void printHashMap() {
        if (mAllDeviceMap.size() > 0) {
            Set<String> keSet = mAllDeviceMap.keySet();
            Iterator iterator = keSet.iterator();
            while (iterator.hasNext()){
                String key = (String) iterator.next();
                LogUtils.LOGE("Device:",key);
            }
        }
    }
}
