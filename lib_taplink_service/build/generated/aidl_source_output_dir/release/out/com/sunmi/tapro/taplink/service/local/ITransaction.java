/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: /Users/sm2735/Library/Android/sdk/build-tools/35.0.0/aidl -p/Users/sm2735/Library/Android/sdk/platforms/android-35/framework.aidl -o/Users/sm2735/Documents/workspace/Taplink_SDK/lib_taplink_service/build/generated/aidl_source_output_dir/release/out -I/Users/sm2735/Documents/workspace/Taplink_SDK/lib_taplink_service/src/main/aidl -I/Users/sm2735/Documents/workspace/Taplink_SDK/lib_taplink_service/src/release/aidl -I/Users/sm2735/.gradle/caches/8.13/transforms/91b23838c9e40978d9f486e8992ca44c/transformed/core-1.16.0/aidl -I/Users/sm2735/.gradle/caches/8.13/transforms/593a66360090a5aead9bdbb186821316/transformed/versionedparcelable-1.1.1/aidl -d/var/folders/8g/jx5xhz0j579gscz5pg7wgnqh0000gp/T/aidl5733743710928338902.d /Users/sm2735/Documents/workspace/Taplink_SDK/lib_taplink_service/src/main/aidl/com/sunmi/tapro/taplink/service/local/ITransaction.aidl
 */
package com.sunmi.tapro.taplink.service.local;
public interface ITransaction extends android.os.IInterface
{
  /** Default implementation for ITransaction. */
  public static class Default implements com.sunmi.tapro.taplink.service.local.ITransaction
  {
    @Override public void onTransaction(java.lang.String requestData, com.sunmi.tapro.taplink.service.local.ITransactionCallback callback) throws android.os.RemoteException
    {
    }
    @Override public java.util.Map<java.lang.String,java.lang.String> getExtraInfos() throws android.os.RemoteException
    {
      return null;
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.sunmi.tapro.taplink.service.local.ITransaction
  {
    /** Construct the stub at attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.sunmi.tapro.taplink.service.local.ITransaction interface,
     * generating a proxy if needed.
     */
    public static com.sunmi.tapro.taplink.service.local.ITransaction asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.sunmi.tapro.taplink.service.local.ITransaction))) {
        return ((com.sunmi.tapro.taplink.service.local.ITransaction)iin);
      }
      return new com.sunmi.tapro.taplink.service.local.ITransaction.Stub.Proxy(obj);
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
        case TRANSACTION_onTransaction:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          com.sunmi.tapro.taplink.service.local.ITransactionCallback _arg1;
          _arg1 = com.sunmi.tapro.taplink.service.local.ITransactionCallback.Stub.asInterface(data.readStrongBinder());
          this.onTransaction(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_getExtraInfos:
        {
          java.util.Map<java.lang.String,java.lang.String> _result = this.getExtraInfos();
          reply.writeNoException();
          if (_result == null) {
            reply.writeInt(-1);
          } else {
            reply.writeInt(_result.size());
            _result.forEach((k, v) -> {
              reply.writeString(k);
              reply.writeString(v);
            });
          }
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.sunmi.tapro.taplink.service.local.ITransaction
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
      @Override public void onTransaction(java.lang.String requestData, com.sunmi.tapro.taplink.service.local.ITransactionCallback callback) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(requestData);
          _data.writeStrongInterface(callback);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onTransaction, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public java.util.Map<java.lang.String,java.lang.String> getExtraInfos() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.util.Map<java.lang.String,java.lang.String> _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getExtraInfos, _data, _reply, 0);
          _reply.readException();
          {
            int N = _reply.readInt();
            _result = N < 0 ? null : new java.util.HashMap<>();
            java.util.stream.IntStream.range(0, N).forEach(i -> {
              String k = _reply.readString();
              java.lang.String v;
              v = _reply.readString();
              _result.put(k, v);
            });
          }
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
    }
    static final int TRANSACTION_onTransaction = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_getExtraInfos = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "com.sunmi.tapro.taplink.service.local.ITransaction";
  public void onTransaction(java.lang.String requestData, com.sunmi.tapro.taplink.service.local.ITransactionCallback callback) throws android.os.RemoteException;
  public java.util.Map<java.lang.String,java.lang.String> getExtraInfos() throws android.os.RemoteException;
}
