// ILocalAidl.aidl
package com.sunmi.tapro.taplink.service.local;

// Declare any non-default types here with import statements
import com.sunmi.tapro.taplink.service.local.ITransactionCallback;

interface ITransaction {
    void onTransaction(String requestData,ITransactionCallback callback);
    Map<String,String> getExtraInfos();
}