// ILocalCallbackAidl.aidl
package com.sunmi.tapro.taplink.communication.local;

// Declare any non-default types here with import statements

interface ITransactionCallback {
    void callback(String responseData);

    /**
     * 错误回调接口
     * @param code 错误码
     * @param msg 错误描述
     */
    void onError(String code, String msg);
}