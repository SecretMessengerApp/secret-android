/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.jsy.common.utils;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.waz.zclient.R;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CheckPermissionUtils {
    public static final String[] externalPermission = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};
    public static final String[] cameraPermission = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    public static String[] needPermissions = {
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.READ_PHONE_STATE
    };

    public static String[] needPermissions28 = new String[]{
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.READ_PHONE_STATE,
        //BACKGROUND_LOCATION_PERMISSION
    };

    public static String checkSHA1(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(
                context.getPackageName(), PackageManager.GET_SIGNATURES);
            byte[] cert = info.signatures[0].toByteArray();
            MessageDigest md = MessageDigest.getInstance("SHA1");
            byte[] publicKey = md.digest(cert);
            StringBuffer hexString = new StringBuffer();
            for (int i = 0; i < publicKey.length; i++) {
                String appendString = Integer.toHexString(0xFF & publicKey[i])
                    .toUpperCase(Locale.US);
                if (appendString.length() == 1)
                    hexString.append("0");
                hexString.append(appendString);
                hexString.append(":");
            }
            String result = hexString.toString();
            return result.substring(0, result.length() - 1);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getMetaDataFromApp(Context context, String metaKey) {
        String value = "";
        try {
            ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(),
                PackageManager.GET_META_DATA);
            value = appInfo.metaData.getString(metaKey);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return value;
    }

    public static boolean checkPermissions(Activity activity, int requestCode) {
        String[] permissions = (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) ? CheckPermissionUtils.needPermissions28 : CheckPermissionUtils.needPermissions;
        return checkPermissions(activity, permissions, requestCode);
    }

    public static boolean checkPermissions(Activity activity, String[] permissions, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> needRequestPermissionList = findDeniedPermissions(activity, permissions);
            int needRequestSize = needRequestPermissionList.size();
            if (needRequestSize > 0) {
                if (requestCode >= 0) {
                    String[] array = needRequestPermissionList.toArray(new String[needRequestSize]);
                    ActivityCompat.requestPermissions(activity, array, requestCode);
                }
                return false;
            }
        }
        return true;
    }


    private static List<String> findDeniedPermissions(Activity activity, String... permissions) {
        List<String> needRequestPermissionList = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 23
            && activity.getApplicationInfo().targetSdkVersion >= 23) {
            for (String perm : permissions) {
                if (ContextCompat.checkSelfPermission(activity, perm) != PackageManager.PERMISSION_GRANTED
                    || ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)) {
                    needRequestPermissionList.add(perm);
                }
            }
        }
        return needRequestPermissionList;
    }

    public static boolean verifyPermissions(int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static void showMissingPermissionDialog(final Context context) {
        if (DoubleUtils.isFastDoubleClick()) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.secret_open_permission);
        builder.setMessage(R.string.conversation_detail_check_permissions);

        builder.setNegativeButton(R.string.secret_cancel, (dialog, which) -> {});
        builder.setPositiveButton(R.string.backup_password_dialog_ok, (dialog, which) -> SelfStartSetting.openAppDetailSetting(context));

        builder.setCancelable(false);

        builder.show();
    }

    public static double stringToDouble(String str) {
        if (TextUtils.isEmpty(str)) {
            return 0.0D;
        }
        try {
            return Double.valueOf(str);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return 0.0D;
        }
    }

    public static int getLocRegPagTime(GifDrawable resource) {
        int duration = 0;
        try {
            Field gifStateField = GifDrawable.class.getDeclaredField("state");
            gifStateField.setAccessible(true);
            Class gifStateClass = Class.forName("com.bumptech.glide.load.resource.gif.GifDrawable$GifState");
            Field gifFrameLoaderField = gifStateClass.getDeclaredField("frameLoader");
            gifFrameLoaderField.setAccessible(true);
            Class gifFrameLoaderClass = Class.forName("com.bumptech.glide.load.resource.gif.GifFrameLoader");
            Field gifDecoderField = gifFrameLoaderClass.getDeclaredField("gifDecoder");
            gifDecoderField.setAccessible(true);
            Class gifDecoderClass = Class.forName("com.bumptech.glide.gifdecoder.GifDecoder");
            Object gifDecoder = gifDecoderField.get(gifFrameLoaderField.get(gifStateField.get(resource)));
            Method getDelayMethod = gifDecoderClass.getDeclaredMethod("getDelay", int.class);
            getDelayMethod.setAccessible(true);
            int count = resource.getFrameCount();
            for (int i = 0; i < count; i++) {
                duration += (int) getDelayMethod.invoke(gifDecoder, i);
            }
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        return duration;
    }
}
