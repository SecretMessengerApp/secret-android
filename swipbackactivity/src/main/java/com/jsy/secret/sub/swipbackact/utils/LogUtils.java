/**
 * Secret
 * Copyright (C) 2019 Secret
 */
package com.jsy.secret.sub.swipbackact.utils;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.jsy.secret.sub.swipbackact.BuildConfig;

public class LogUtils {

    public static String TAG_DEFAULT = "SECRET";

    private static Context context;

    public static void init(Context ctx){
        context=ctx.getApplicationContext();
    }

    public static void v(String msg) {
        if(!BuildConfig.DEBUG){
            return;
        }
        v(TAG_DEFAULT, msg);
    }

    public static void v(Class classs, String msg) {
        if(!BuildConfig.DEBUG){
            return;
        }
        v(classs.getSimpleName(), classs.getSimpleName() + "==" + msg);
    }

    public static void v(String tag, String msg) {
        if(!BuildConfig.DEBUG){
            return;
        }
        log(Log.VERBOSE,tag, msg);
    }

    public static void d(String msg) {
        if(!BuildConfig.DEBUG){
            return;
        }
        d(TAG_DEFAULT, msg);
    }

    public static void d(Class classs, String msg) {
        if(!BuildConfig.DEBUG){
            return;
        }
        d(classs.getSimpleName(), classs.getSimpleName() + "==" + msg);
    }

    public static void d(String tag, String msg) {
        if(!BuildConfig.DEBUG){
            return;
        }
        log(Log.DEBUG,tag, msg);
    }

    public static void i(String msg) {
        if(!BuildConfig.DEBUG){
            return;
        }
        i(TAG_DEFAULT, msg);
    }

    public static void i(Class classs, String msg) {
        if(!BuildConfig.DEBUG){
            return;
        }
        i(classs.getSimpleName(), classs.getSimpleName() + "==" + msg);
    }

    public static void i(String tag, String msg) {
        if(!BuildConfig.DEBUG){
            return;
        }
        log(Log.INFO,tag, msg);
    }


    public static void w(String msg) {
        if(!BuildConfig.DEBUG){
            return;
        }
        w(TAG_DEFAULT, msg);
    }

    public static void w(Class classs, String msg) {
        if(!BuildConfig.DEBUG){
            return;
        }
        w(classs.getSimpleName(), classs.getSimpleName() + "==" + msg);
    }

    public static void w(String tag, String msg) {
        if(!BuildConfig.DEBUG){
            return;
        }
        log(Log.WARN,tag, msg);
    }

    public static void e(String msg) {
        if(!BuildConfig.DEBUG){
            return;
        }
        e(TAG_DEFAULT, msg);
    }

    public static void e(Class classs, String msg) {
        if(!BuildConfig.DEBUG){
            return;
        }
        e(classs.getSimpleName(), classs.getSimpleName() + "==" + msg);
    }

    public static void e(String tag, String msg) {
        if(!BuildConfig.DEBUG){
            return;
        }
        log(Log.ERROR,tag, msg);
    }

    private static void log(int level,String tag,String msg){
            if(TextUtils.isEmpty(msg)){
                return;
            }
            if(level==Log.VERBOSE){
                Log.v(tag,msg);
            }
            else if(level==Log.DEBUG){
                Log.d(tag,msg);
            }
            else if(level==Log.INFO){
                Log.i(tag,msg);
            }
            else if(level==Log.WARN){
                Log.w(tag,msg);
            }
            else if(level==Log.ERROR){
                Log.e(tag,msg);
            }

    }




}
