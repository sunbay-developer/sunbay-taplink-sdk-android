package com.sunmi.tapro.taplink.communication.util;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;

import java.lang.reflect.Method;
import java.util.Arrays;

public class InnerUtil {

    public static String bytes2HexStr(byte[] src) {
        StringBuilder builder = new StringBuilder();
        if (src == null || src.length <= 0) {
            return "";
        }
        char[] buffer = new char[2];
        for (int i = 0; i < src.length; i++) {
            buffer[0] = Character.forDigit((src[i] >>> 4) & 0x0F, 16);
            buffer[1] = Character.forDigit(src[i] & 0x0F, 16);
            builder.append(buffer);
        }
        return builder.toString().toUpperCase();
    }

    public static byte[] hexStr2bytes(String hex) {
        int len = (hex.length() / 2);
        byte[] result = new byte[len];
        char[] achar = hex.toUpperCase().toCharArray();
        for (int i = 0; i < len; i++) {
            int pos = i * 2;
            result[i] = (byte) (hexChar2byte(achar[pos]) << 4 | hexChar2byte(achar[pos + 1]));
        }
        return result;
    }

    private static int hexChar2byte(char c) {
        switch (c) {
            case '0':
                return 0;
            case '1':
                return 1;
            case '2':
                return 2;
            case '3':
                return 3;
            case '4':
                return 4;
            case '5':
                return 5;
            case '6':
                return 6;
            case '7':
                return 7;
            case '8':
                return 8;
            case '9':
                return 9;
            case 'a':
            case 'A':
                return 10;
            case 'b':
            case 'B':
                return 11;
            case 'c':
            case 'C':
                return 12;
            case 'd':
            case 'D':
                return 13;
            case 'e':
            case 'E':
                return 14;
            case 'f':
            case 'F':
                return 15;
            default:
                return -1;
        }
    }

    public static boolean isSmartPad() {
        String mode = Build.MODEL.toLowerCase();
        return mode.matches("p2_smartpad(-.+)?") || mode.matches("p2smartpad(-.+)?");
    }

    public static String printBundle(Bundle bundle) {
        if (bundle == null) {
            return "Bundle is null";
        }

        StringBuilder sb = new StringBuilder();
        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            if (value instanceof String) {
                sb.append(key).append(" : ").append((String) value).append("\n");
            } else if (value instanceof Integer) {
                sb.append(key).append(" : ").append(value).append("\n");
            } else if (value instanceof Boolean) {
                sb.append(key).append(" : ").append(value).append("\n");
            } else if (value instanceof Double) {
                sb.append(key).append(" : ").append(value).append("\n");
            } else if (value instanceof Float) {
                sb.append(key).append(" : ").append(value).append("\n");
            } else if (value instanceof Long) {
                sb.append(key).append(" : ").append(value).append("\n");
            } else if (value instanceof Bundle) {
                sb.append(key).append(" : Bundle\n").append(printBundle((Bundle) value)).append("\n");
            } else if (value instanceof String[]) {
                sb.append(key).append(" : ").append(Arrays.toString((String[]) value)).append("\n");
            } else if (value instanceof int[]) {
                sb.append(key).append(" : ").append(Arrays.toString((int[]) value)).append("\n");
            } else if (value instanceof boolean[]) {
                sb.append(key).append(" : ").append(Arrays.toString((boolean[]) value)).append("\n");
            } else if (value instanceof double[]) {
                sb.append(key).append(" : ").append(Arrays.toString((double[]) value)).append("\n");
            } else if (value instanceof float[]) {
                sb.append(key).append(" : ").append(Arrays.toString((float[]) value)).append("\n");
            } else if (value instanceof long[]) {
                sb.append(key).append(" : ").append(Arrays.toString((long[]) value)).append("\n");
            } else {
                sb.append(key).append(" : ").append(value).append("\n");
            }
        }
        return sb.toString();
    }

    public static String getSN() {
        try {
            String serial = null;
            @SuppressLint("PrivateApi") Class<?> c = Class.forName("android.os.SystemProperties");
            Method get = c.getMethod("get", String.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    serial = (String) get.invoke(c, "ro.sunmi.serial");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return serial;
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                serial = Build.getSerial();
                return serial;
            } else {
                // Android 8 and below use same method as Build.SERIAL
                //return Build.SERIAL;
                try {
                    serial = (String) get.invoke(c, "ro.serialno");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return serial;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "0123456789";
        }
    }




}
