package com.sunmi.tapro.taplink.service.util;

import android.util.Log;

import java.util.Locale;

/**
 * 输出各种级别的log到控制台
 * msg字符长度不受限制，默认的Log是受限制的，超过的部分不输出
 * 输出的log头有链接，可以点击链接到执行代码文件位置
 * 可以直接输出msg,使用默认Tag
 * 支持设置 LEVEL，小于LEVEL级别以下不再打印log
 */
public class LogUtil {
    private static final String TAG = "LogUtil";
    public static int LEVEL = Log.VERBOSE;

    public static void setDebuggable(boolean isDebuggable) {
        LEVEL = isDebuggable ? Log.VERBOSE : Log.INFO;
    }

    public static void setLevel(int level) {
        LEVEL = level;
    }

    public static void v(String TAG, String msg) {
        String logWithLink = getLogWithLink(msg);
        if (LEVEL <= Log.VERBOSE) {
            LogWrapperLoop(Log.VERBOSE, TAG, logWithLink);
        }
    }

    public static void d(String TAG, String msg) {
        String logWithLink = getLogWithLink(msg);
        if (LEVEL <= Log.DEBUG) {
            LogWrapperLoop(Log.DEBUG, TAG, logWithLink);
        }
    }

    public static void i(String TAG, String msg) {
        String logWithLink = getLogWithLink(msg);
        if (LEVEL <= Log.INFO) {
            LogWrapperLoop(Log.INFO, TAG, logWithLink);
        }
    }

    public static void w(String TAG, String msg) {
        String logWithLink = getLogWithLink(msg);
        if (LEVEL <= Log.WARN) {
            LogWrapperLoop(Log.WARN, TAG, logWithLink);
        }
    }

    public static void e(String TAG, String msg) {
        String logWithLink = getLogWithLink(msg);
        if (LEVEL <= Log.ERROR) {
            LogWrapperLoop(Log.ERROR, TAG, logWithLink);
        }
    }

    //Log输出的最大长度
    private static final int LOG_MAX_LENGTH = 2000;//2000

    private static String getLogWithLink(String msg) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        int index = 4;
        String className = stackTrace[index].getFileName();
        String methodName = stackTrace[index].getMethodName();
        int lineNumber = stackTrace[index].getLineNumber();
        methodName = methodName.substring(0, 1).toUpperCase(Locale.getDefault()) + methodName.substring(1);
        return "[ (" + className + ":" + lineNumber + ")#" + methodName + " ] " +
                msg;
    }

    /**
     * 带有链接的log打印
     */
//    private static void logWithLink(int logPriority, String TAG, String msg) {
//
//        LogWrapperLoop(logPriority, TAG, logStr);
//    }

    /**
     * 循环打印log，解决msg过长不打印问题
     */
    @SuppressWarnings("DanglingJavadoc")
    private static void LogWrapperLoop(int logPriority, String TAG, String msg) {
        int msgLength = msg.length();
        /**
         * adb  lgh=3
         *  ^
         *  |   index=1
         * print a
         */
        int index = 0;//输出字符的位置
        while (index < msgLength) {
            if (index + LOG_MAX_LENGTH > msgLength) {
                LogWrapper(logPriority, TAG, msg.substring(index, msgLength));
            } else {
                LogWrapper(logPriority, TAG, msg.substring(index, index + LOG_MAX_LENGTH));
            }
            index += LOG_MAX_LENGTH;
        }
    }

    private static void LogWrapper(int logPriority, String TAG, String msg) {
        switch (logPriority) {
            case Log.VERBOSE:
                Log.v(TAG, (msg));
                break;
            case Log.DEBUG:
                Log.d(TAG, (msg));
                break;
            case Log.INFO:
                Log.i(TAG, (msg));
                break;
            case Log.WARN:
                Log.w(TAG, (msg));
                break;
            case Log.ERROR:
                Log.e(TAG, (msg));
                break;
            default:
                break;
        }
    }


}

