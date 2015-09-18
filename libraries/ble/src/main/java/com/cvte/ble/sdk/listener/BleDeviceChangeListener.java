package com.cvte.ble.sdk.listener;

import com.cvte.ble.sdk.entity.BleConnectDevice;

import java.util.Map;

/**
 * Package : com.cvte.ble.sdk.listener
 * Author : jacob
 * Date : 15-7-13
 * Description : 这个类是蓝牙搜索队列设备个数发生变化的广播
 */
public interface BleDeviceChangeListener {
    void onDeviceSizeChange(Map<String, BleConnectDevice> allDeviceMap);
}
