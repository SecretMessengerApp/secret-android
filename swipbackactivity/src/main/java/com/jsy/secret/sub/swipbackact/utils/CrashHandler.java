/**
 * Secret
 * Copyright (C) 2019 Secret
 */
package com.jsy.secret.sub.swipbackact.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import com.jsy.secret.sub.swipbackact.BuildConfig;

public final class CrashHandler {

    private static Context mContext;
    private static boolean mInitialized;
    private static final Format formatter = new SimpleDateFormat("MM-dd HH-mm-ss", Locale.getDefault());

    private static String CRASH_HEAD;
    private static String versionName;
    private static int versionCode;

    private static final UncaughtExceptionHandler DEFAULT_UNCAUGHT_EXCEPTION_HANDLER;
    private static final UncaughtExceptionHandler UNCAUGHT_EXCEPTION_HANDLER;

    static {
        DEFAULT_UNCAUGHT_EXCEPTION_HANDLER = Thread.getDefaultUncaughtExceptionHandler();
        UNCAUGHT_EXCEPTION_HANDLER = new UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(final Thread t, final Throwable e) {
                if (e == null || null == mContext || !mInitialized) {
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(0);
                    return;
                }
                LogUtils.e("CrashHandler", "error:"+e.getMessage());
                long curTime = System.currentTimeMillis();
                Date now = new Date(curTime);
                String fileName = formatter.format(now) + "-" + curTime + ".txt";
                final String fullPath = getGlobalpath() + fileName;
                if (!createOrExistsFile(fullPath)) return;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try (PrintWriter pw = new PrintWriter(new FileWriter(fullPath, false))) {
                            pw.write(CRASH_HEAD);
                            e.printStackTrace(pw);
                            Throwable cause = e.getCause();
                            while (cause != null) {
                                cause.printStackTrace(pw);
                                cause = cause.getCause();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
                if (DEFAULT_UNCAUGHT_EXCEPTION_HANDLER != null) {
                    DEFAULT_UNCAUGHT_EXCEPTION_HANDLER.uncaughtException(t, e);
                }
            }
        };
    }

    public static Context getContext() {
        if (mContext != null) return mContext;
        throw new NullPointerException("u should init first");
    }


    public static boolean isGrabCrashLog(Context context) {
        try {
            ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            boolean flag = applicationInfo.metaData.getBoolean("INTERNAL");
            LogUtils.e("CrashHandler", "==init=GET_META_DATA==" + flag + "==BuildConfig.BUILD_TYPE==" + BuildConfig.BUILD_TYPE);
            if ("release".equals(BuildConfig.BUILD_TYPE) && !flag) {
                return false;
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return true;
    }


    public static void init(Context context) {
        mInitialized = false;
        if (!BuildConfig.DEBUG && !isGrabCrashLog(context)) {
            return;
        }

        CrashHandler.mContext = context.getApplicationContext();

        try {
            PackageInfo pi = CrashHandler.getContext().getPackageManager().getPackageInfo(CrashHandler.getContext().getPackageName(), 0);
            if (pi != null) {
                versionName = pi.versionName;
                versionCode = pi.versionCode;
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        CRASH_HEAD = "\n************* Crash Log Head ****************" +
                "\nDevice Manufacturer: " + Build.MANUFACTURER +
                "\nDevice Model       : " + Build.MODEL +
                "\nAndroid Version    : " + Build.VERSION.RELEASE +
                "\nAndroid SDK        : " + Build.VERSION.SDK_INT +
                "\nApp VersionName    : " + versionName +
                "\nApp VersionCode    : " + versionCode +
                "\n************* Crash Log Head ****************\n\n";

        Thread.setDefaultUncaughtExceptionHandler(UNCAUGHT_EXCEPTION_HANDLER);
        mInitialized = true;
    }

    public static boolean createOrExistsFile(String filePath) {
        File file = new File(filePath);
        if (file.exists()) return file.isFile();
        if (!createOrExistsDir(file.getParentFile())) return false;
        try {
            return file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean createOrExistsDir(File file) {
        return file != null && (file.exists() ? file.isDirectory() : file.mkdirs());
    }

    public static String getGlobalpath() {
        String defaultDir;
        if (hasSdcard() && CrashHandler.getContext().getExternalCacheDir() != null)
            defaultDir = CrashHandler.getContext().getExternalCacheDir() + File.separator + "crash" + File.separator;
        else {
            defaultDir = CrashHandler.getContext().getCacheDir() + File.separator + "crash" + File.separator;
        }
        return defaultDir;
    }

    public static boolean hasSdcard() {
        String status = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(status);
    }
}
