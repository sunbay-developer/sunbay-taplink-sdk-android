/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: /Users/sm2735/Library/Android/sdk/build-tools/35.0.0/aidl -p/Users/sm2735/Library/Android/sdk/platforms/android-35/framework.aidl -o/Users/sm2735/Documents/workspace/Taplink_SDK/lib_taplink_service/build/generated/aidl_source_output_dir/debug/out -I/Users/sm2735/Documents/workspace/Taplink_SDK/lib_taplink_service/src/main/aidl -I/Users/sm2735/Documents/workspace/Taplink_SDK/lib_taplink_service/src/debug/aidl -I/Users/sm2735/.gradle/caches/8.13/transforms/45e4b9ea5bb461b9419f77ab903f466f/transformed/core-1.16.0/aidl -I/Users/sm2735/.gradle/caches/8.13/transforms/b177c29edaa02d7feb11da194c72e114/transformed/versionedparcelable-1.1.1/aidl -d/var/folders/8g/jx5xhz0j579gscz5pg7wgnqh0000gp/T/aidl17792182156098736267.d /Users/sm2735/Documents/workspace/Taplink_SDK/lib_taplink_service/src/main/aidl/com/sunmi/tapro/taplink/service/local/ITransactionCallback.aidl
 */
package com.sunmi.tapro.taplink.service.local;
// Declare any non-default types here with import statements
public interface ITransactionCallback extends android.os.IInterface
{
  /** Default implementation for ITransactionCallback. */
  public static class Default implements com.sunmi.tapro.taplink.service.local.ITransactionCallback
  {
    @Override public void callback(java.lang.String responseData) throws android.os.RemoteException
    {
    }
    /**
     * 错误回调接口
     * @param code 错误码
     * @param msg 错误描述
     */
    @Override public void onError(java.lang.String code, java.lang.String msg) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.sunmi.tapro.taplink.service.local.ITransactionCallback
  {
    /** Construct the stub at attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.sunmi.tapro.taplink.service.local.ITransactionCallback interface,
     * generating a proxy if needed.
     */
    public static com.sunmi.tapro.taplink.service.local.ITransactionCallback asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.sunmi.tapro.taplink.service.local.ITransactionCallback))) {
        return ((com.sunmi.tapro.taplink.service.local.ITransactionCallback)iin);
      }
      return new com.sunmi.tapro.taplink.service.local.ITransactionCallback.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      if (code == INTERFACE_TRANSACTION) {
        reply.writeString(descriptor);
        return true;
      }
      switch (code)
      {
        case TRANSACTION_callback:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          this.callback(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_onError:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          java.lang.String _arg1;
          _arg1 = data.readString();
          this.onError(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.sunmi.tapro.taplink.service.local.ITransactionCallback
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      @Override public void callback(java.lang.String responseData) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(responseData);
          boolean _status = mRemote.transact(Stub.TRANSACTION_callback, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      /**
       * 错误回调接口
       * @param code 错误码
       * @param msg 错误描述
       */
      @Override public void onError(java.lang.String code, java.lang.String msg) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(code);
          _data.writeString(msg);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onError, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_callback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_onError = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "com.sunmi.tapro.taplink.service.local.ITransactionCallback";
  public void callback(java.lang.String responseData) throws android.os.RemoteException;
  /**
   * 错误回调接口
   * @param code 错误码
   * @param msg 错误描述
   */
  public void onError(java.lang.String code, java.lang.String msg) throws android.os.RemoteException;
}
