// ILocalAidl.aidl
package com.sunmi.tapro.taplink.communication.local;

// Declare any non-default types here with import statements
import com.sunmi.tapro.taplink.communication.local.ITransactionCallback;

interface ITransaction {
    void onTransaction(String requestData,ITransactionCallback callback);
    Map<String,String> getExtraInfos();
}