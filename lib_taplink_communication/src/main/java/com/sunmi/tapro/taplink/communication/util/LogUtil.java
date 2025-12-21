package com.sunmi.tapro.taplink.communication.util;

import android.util.Log;

import java.util.Locale;

/**
 * Output various levels of log to console
 * Message character length is unlimited, default Log is limited, excess parts are not output
 * Output log header has links, can click to link to execution code file location
 * Can directly output msg, using default Tag
 * Supports setting LEVEL, levels below LEVEL will not print log
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

    //Maximum length of Log output
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
     * Log printing with links
     */
//    private static void logWithLink(int logPriority, String TAG, String msg) {
//
//        LogWrapperLoop(logPriority, TAG, logStr);
//    }

    /**
     * Loop print log, solve the problem of msg being too long and not printing
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
        int index = 0;//Position of output character
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

