package com.cvte.ble.sdk.exception;

import android.util.Log;

/**
 * Package : com.cvte.kidtracker.ui.bluetooth
 * Author : jacob
 * Date : 15-7-30
 * Description : 这个类是不支持Ble的异常类
 */
public class NotSupportBleException extends Exception {
    public NotSupportBleException(String s) {
        super(s);
    }

    @Override
    public void printStackTrace() {
        Log.e("BleSdkManager","This device not support Ble !" );
    }
}
