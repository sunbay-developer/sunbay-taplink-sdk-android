package com.sunmi.tapro.taplink.communication.cable.vsp;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.util.Log;

import com.hoho.android.usbserial.driver.CommonUsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialPort.ControlLine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * 商米 VSP 专用串口驱动，移植自 sunmi-ecr-service VSPSerialDriver。
 *
 * <p>商米 VSP 设备为多接口 CDC-ACM 设备，标准 CdcAcmSerialDriver 按接口顺序选择，
 * 会选到错误的接口，导致连接成功但无法收发数据。
 * 本驱动在构造时通过接口名 "CDC ACM Data" 定位正确的数据接口，保证打开正确端口。
 *
 * <p>openInt() 使用 usb-serial-for-android 3.7.0 的无参签名，
 * 父类 open() 在调用本方法前已将 UsbDeviceConnection 赋值到 mConnection。
 */
public class VSPSerialDriver implements UsbSerialDriver {

    private static final String TAG = "VSPSerialDriver";

    private final UsbDevice mDevice;
    private final List<UsbSerialPort> mPorts;

    public VSPSerialDriver(UsbDevice device) {
        this.mDevice = device;
        this.mPorts = new ArrayList<>();

        int controlInterfaceCount = 0;
        int dataInterfaceCount = 0;
        // 名称为 "CDC ACM Data" 的数据接口排序位置（从 1 开始，0 表示未找到）
        int vspDataInterfaceIndex = 0;

        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            if (iface.getInterfaceClass() == 2) {   // CDC Control
                controlInterfaceCount++;
            }
            if (iface.getInterfaceClass() == 10) {  // CDC Data
                dataInterfaceCount++;
                if ("CDC ACM Data".equalsIgnoreCase(iface.getName())) {
                    vspDataInterfaceIndex = dataInterfaceCount;
                }
            }
        }

        for (int i = 0; i < Math.min(controlInterfaceCount, dataInterfaceCount); i++) {
            if (vspDataInterfaceIndex != 0) {
                // 有明确命名的接口，固定指向该数据接口
                mPorts.add(new CdcAcmSerialPort(mDevice, vspDataInterfaceIndex, controlInterfaceCount));
            } else {
                mPorts.add(new CdcAcmSerialPort(mDevice, i));
            }
        }

        // 保底：未识别到接口对时退回单接口模式
        if (mPorts.isEmpty()) {
            Log.w(TAG, "No interface pair found, falling back to single-interface mode");
            mPorts.add(new CdcAcmSerialPort(mDevice, -1));
        }

        Log.d(TAG, "VSPSerialDriver created: " + device.getDeviceName()
                + ", ports=" + mPorts.size()
                + ", vspDataInterfaceIndex=" + vspDataInterfaceIndex);
    }

    @Override
    public UsbDevice getDevice() {
        return mDevice;
    }

    @Override
    public List<UsbSerialPort> getPorts() {
        return mPorts;
    }

    // ─────────────────────────────────────────────────────────────────────────

    public class CdcAcmSerialPort extends CommonUsbSerialPort {

        private UsbInterface mControlInterface;
        private UsbInterface mDataInterface;
        private UsbEndpoint mControlEndpoint;
        private int mControlIndex;

        /** controlPortNumber < 0 表示单接口模式 */
        private final int controlPortNumber;

        private boolean mRts = false;
        private boolean mDtr = false;

        private static final int USB_RT_ACM             = 0x21;
        private static final int SET_LINE_CODING        = 0x20;
        private static final int SET_CONTROL_LINE_STATE = 0x22;
        private static final int SEND_BREAK             = 0x23;

        public CdcAcmSerialPort(UsbDevice device, int portNumber) {
            super(device, portNumber);
            this.controlPortNumber = -1;
        }

        public CdcAcmSerialPort(UsbDevice device, int portNumber, int controlPortNumber) {
            super(device, portNumber);
            this.controlPortNumber = controlPortNumber;
        }

        @Override
        public UsbSerialDriver getDriver() {
            return VSPSerialDriver.this;
        }

        /**
         * usb-serial-for-android 3.7.0 无参签名。
         * 父类 open() 调用本方法前已将连接赋值到 mConnection。
         */
        @Override
        protected void openInt() throws IOException {
            if (mPortNumber == -1) {
                Log.d(TAG, "Single-interface mode");
                openSingleInterface();
            } else {
                Log.d(TAG, "Multi-interface mode, portNumber=" + mPortNumber
                        + " controlPortNumber=" + controlPortNumber);
                openInterface();
            }
        }

        /** 单接口模式：控制和数据共享同一接口 */
        private void openSingleInterface() throws IOException {
            mControlIndex     = 0;
            mControlInterface = mDevice.getInterface(0);
            mDataInterface    = mDevice.getInterface(0);

            if (!mConnection.claimInterface(mControlInterface, true)) {
                throw new IOException("Could not claim shared control/data interface");
            }

            for (int i = 0; i < mControlInterface.getEndpointCount(); i++) {
                UsbEndpoint ep = mControlInterface.getEndpoint(i);
                if      (ep.getDirection() == 0x80 && ep.getType() == 3) mControlEndpoint = ep;
                else if (ep.getDirection() == 0x80 && ep.getType() == 2) mReadEndpoint    = ep;
                else if (ep.getDirection() == 0    && ep.getType() == 2) mWriteEndpoint   = ep;
            }

            if (mControlEndpoint == null) {
                throw new IOException("No control endpoint in single-interface mode");
            }
        }

        /**
         * 多接口模式：按 portNumber / controlPortNumber 匹配控制接口和数据接口。
         * portNumber 是数据接口排序位置（从 1 开始），在构造函数中由接口名确定。
         */
        private void openInterface() throws IOException {
            Log.d(TAG, "Claiming interfaces, total=" + mDevice.getInterfaceCount());

            int controlCount  = 0;
            int dataCount     = 0;
            mControlInterface = null;
            mDataInterface    = null;

            for (int i = 0; i < mDevice.getInterfaceCount(); i++) {
                UsbInterface iface = mDevice.getInterface(i);
                if (iface.getInterfaceClass() == 2) {
                    controlCount++;
                    if (controlCount == controlPortNumber) {
                        mControlIndex     = i;
                        mControlInterface = iface;
                    }
                }
                if (iface.getInterfaceClass() == 10) {
                    dataCount++;
                    if (dataCount == mPortNumber) {
                        mDataInterface = iface;
                    }
                }
            }

            if (mControlInterface == null) {
                throw new IOException("Control interface not found (controlPortNumber=" + controlPortNumber + ")");
            }
            if (!mConnection.claimInterface(mControlInterface, true)) {
                throw new IOException("Could not claim control interface");
            }

            mControlEndpoint = mControlInterface.getEndpoint(0);
            if (mControlEndpoint.getDirection() != 0x80 || mControlEndpoint.getType() != 3) {
                throw new IOException("Invalid control endpoint");
            }

            if (mDataInterface == null) {
                throw new IOException("Data interface not found (mPortNumber=" + mPortNumber + ")");
            }
            if (!mConnection.claimInterface(mDataInterface, true)) {
                throw new IOException("Could not claim data interface");
            }

            for (int i = 0; i < mDataInterface.getEndpointCount(); i++) {
                UsbEndpoint ep = mDataInterface.getEndpoint(i);
                if (ep.getDirection() == 0x80 && ep.getType() == 2) mReadEndpoint  = ep;
                if (ep.getDirection() == 0    && ep.getType() == 2) mWriteEndpoint = ep;
            }

            Log.d(TAG, "Control iface=" + mControlInterface + ", Data iface=" + mDataInterface);
        }

        @Override
        protected void closeInt() {
            try { mConnection.releaseInterface(mControlInterface); } catch (Exception ignored) {}
            try { mConnection.releaseInterface(mDataInterface);    } catch (Exception ignored) {}
        }

        private int sendAcmControlMessage(int request, int value, byte[] buf) throws IOException {
            int len = mConnection.controlTransfer(
                    USB_RT_ACM, request, value, mControlIndex,
                    buf, buf != null ? buf.length : 0, 5000);
            if (len < 0) {
                throw new IOException("controlTransfer failed: request=0x" + Integer.toHexString(request));
            }
            return len;
        }

        @Override
        public void setParameters(int baudRate, int dataBits, int stopBits, int parity) throws IOException {
            if (baudRate <= 0)                throw new IllegalArgumentException("Invalid baudRate: " + baudRate);
            if (dataBits < 5 || dataBits > 8) throw new IllegalArgumentException("Invalid dataBits: " + dataBits);

            byte stopBitsByte;
            switch (stopBits) {
                case 1:  stopBitsByte = 0; break;
                case 2:  stopBitsByte = 2; break;
                case 3:  stopBitsByte = 1; break;
                default: throw new IllegalArgumentException("Invalid stopBits: " + stopBits);
            }

            byte parityByte;
            switch (parity) {
                case 0:  parityByte = 0; break;
                case 1:  parityByte = 1; break;
                case 2:  parityByte = 2; break;
                case 3:  parityByte = 3; break;
                case 4:  parityByte = 4; break;
                default: throw new IllegalArgumentException("Invalid parity: " + parity);
            }

            byte[] msg = {
                (byte)(baudRate & 0xFF),
                (byte)((baudRate >>  8) & 0xFF),
                (byte)((baudRate >> 16) & 0xFF),
                (byte)((baudRate >> 24) & 0xFF),
                stopBitsByte, parityByte, (byte)dataBits
            };
            sendAcmControlMessage(SET_LINE_CODING, 0, msg);
        }

        @Override public boolean getDTR() { return mDtr; }
        @Override public void setDTR(boolean value) throws IOException { mDtr = value; setDtrRts(); }
        @Override public boolean getRTS() { return mRts; }
        @Override public void setRTS(boolean value) throws IOException { mRts = value; setDtrRts(); }

        private void setDtrRts() throws IOException {
            sendAcmControlMessage(SET_CONTROL_LINE_STATE, (mRts ? 2 : 0) | (mDtr ? 1 : 0), null);
        }

        @Override
        public EnumSet<ControlLine> getControlLines() {
            EnumSet<ControlLine> set = EnumSet.noneOf(ControlLine.class);
            if (mRts) set.add(ControlLine.RTS);
            if (mDtr) set.add(ControlLine.DTR);
            return set;
        }

        @Override
        public EnumSet<ControlLine> getSupportedControlLines() {
            return EnumSet.of(ControlLine.RTS, ControlLine.DTR);
        }

        @Override
        public void setBreak(boolean value) throws IOException {
            sendAcmControlMessage(SEND_BREAK, value ? 0xFFFF : 0, null);
        }
    }
}
