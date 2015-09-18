package com.cvte.ble.sdk.listener;

/**
 * Package : com.cvte.ble.sdk.listener
 * Author : jacob
 * Date : 15-7-10
 * Description : 这个类是向蓝牙设备读数据的接口
 */
public interface BleReadCallback {
    void onReadSuccess(byte[] bytes);

    void onReadFail(int errorCode, String reason);
}
