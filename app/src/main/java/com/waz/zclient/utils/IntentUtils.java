/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
package com.waz.zclient.utils;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.app.ShareCompat;
import androidx.core.content.FileProvider;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.jsy.secret.sub.swipbackact.utils.LogUtils;
import com.waz.utils.wrappers.AndroidURIUtil;
import com.waz.utils.wrappers.URI;
import com.waz.zclient.BuildConfig;
import com.waz.zclient.R;
import com.waz.zclient.ShareActivity;
import com.waz.zclient.controllers.notifications.ShareSavedImageActivity;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class IntentUtils {

    public static final String GOOGLEPLAY_PACKAGENAME = "com.android.vending";
    public static final String AUTHENTICATOR_PACKAGENAME = "com.google.android.apps.authenticator2";

    public static final String SECRET_SCHEME = "secret";
    public static final String PASSWORD_RESET_SUCCESSFUL_HOST_TOKEN = "password-reset-successful";
    public static final String EXTRA_LAUNCH_FROM_SAVE_IMAGE_NOTIFICATION = "EXTRA_LAUNCH_FROM_SAVE_IMAGE_NOTIFICATION";
    public static final String EXTRA_CONTENT_URI = "EXTRA_CONTENT_URI";
    public static final String LUNCH_MOD = "lunch-mod";

    private static final String GOOGLE_MAPS_INTENT_URI = "geo:0,0?q=%s,%s";
    private static final String GOOGLE_MAPS_WITH_LABEL_INTENT_URI = "geo:0,0?q=%s,%s(%s)";
    private static final String GOOGLE_MAPS_INTENT_PACKAGE = "com.google.android.apps.maps";
    private static final String GOOGLE_MAPS_WEB_LINK = "http://maps.google.com/maps?z=%d&q=loc:%f+%f+(%s)";
    private static final String IMAGE_MIME_TYPE = "image/*";

    public static final String APK_MEDIA_TYPE = "application/vnd.android.package-archive";

    public static boolean isPasswordResetIntent(@Nullable Intent intent) {
        if (intent == null) {
            return false;
        }

        Uri data = intent.getData();
        return data != null &&
            SECRET_SCHEME.equals(data.getScheme()) &&
            PASSWORD_RESET_SUCCESSFUL_HOST_TOKEN.equals(data.getHost());
    }

    public static PendingIntent getGalleryIntent(Context context, URI uri) {
        // TODO: AN-2276 - Replace with ShareCompat.IntentBuilder
        Uri androidUri = AndroidURIUtil.unwrap(uri);
        Intent galleryIntent = new Intent(Intent.ACTION_VIEW);
        galleryIntent.setDataAndTypeAndNormalize(androidUri, IMAGE_MIME_TYPE);
        galleryIntent.setClipData(new ClipData(null, new String[]{IMAGE_MIME_TYPE}, new ClipData.Item(androidUri)));
        galleryIntent.putExtra(Intent.EXTRA_STREAM, androidUri);
        galleryIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return PendingIntent.getActivity(context, 0, galleryIntent, 0);
    }

    public static PendingIntent getPendingShareIntent(Context context, URI uri) {
        Intent shareIntent = new Intent(context, ShareSavedImageActivity.class);
        shareIntent.putExtra(IntentUtils.EXTRA_LAUNCH_FROM_SAVE_IMAGE_NOTIFICATION, true);
        shareIntent.putExtra(IntentUtils.EXTRA_CONTENT_URI, uri.toString());
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return PendingIntent.getActivity(context, 0, shareIntent, 0);
    }

    public static Intent getDebugReportIntent(Context context, URI fileUri) {
        String versionName;
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            versionName = packageInfo.versionName;
        } catch (Exception e) {
            versionName = "n/a";
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("vnd.android.cursor.dir/email");
        String[] to;
        if (BuildConfig.DEBUG) {
            to = new String[]{"android@secret.com"};
        } else {
            to = new String[]{"support@secret.com"};
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_EMAIL, to);
        intent.putExtra(Intent.EXTRA_STREAM, AndroidURIUtil.unwrap(fileUri));
        intent.putExtra(Intent.EXTRA_TEXT, context.getString(R.string.debug_report__body));
        intent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.debug_report__title, versionName));
        return intent;
    }

    public static Intent getSavedImageShareIntent(Context context, URI uri) {
        Uri androidUri = AndroidURIUtil.unwrap(uri);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setClipData(new ClipData(null, new String[]{IMAGE_MIME_TYPE}, new ClipData.Item(androidUri)));
        shareIntent.putExtra(Intent.EXTRA_STREAM, androidUri);
        shareIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        shareIntent.setDataAndTypeAndNormalize(androidUri, IMAGE_MIME_TYPE);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return Intent.createChooser(shareIntent,
            context.getString(R.string.notification__image_saving__action__share));
    }

    public static boolean isLaunchFromSaveImageNotificationIntent(@Nullable Intent intent) {
        return intent != null &&
            intent.getBooleanExtra(EXTRA_LAUNCH_FROM_SAVE_IMAGE_NOTIFICATION, false) &&
            intent.hasExtra(EXTRA_CONTENT_URI);
    }

    public static Intent getGoogleMapsIntent(Context context, float lat, float lon, int zoom, String name) {
        Uri gmmIntentUri;
        if (StringUtils.isBlank(name)) {
            gmmIntentUri = Uri.parse(String.format(GOOGLE_MAPS_INTENT_URI, lat, lon));
        } else {
            gmmIntentUri = Uri.parse(String.format(GOOGLE_MAPS_WITH_LABEL_INTENT_URI, lat, lon, name));
        }
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage(GOOGLE_MAPS_INTENT_PACKAGE);
        if (mapIntent.resolveActivity(context.getPackageManager()) == null) {
            return getGoogleMapsWebFallbackIntent(lat, lon, zoom, name);
        }
        return mapIntent;
    }

    private static Intent getGoogleMapsWebFallbackIntent(float lat, float lon, int zoom, String name) {
        String urlEncodedName;
        try {
            urlEncodedName = URLEncoder.encode(name, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            urlEncodedName = name;
        }
        String url = String.format(Locale.getDefault(), GOOGLE_MAPS_WEB_LINK, zoom, lat, lon, urlEncodedName);
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return browserIntent;
    }

    public static Intent getInviteIntent(String subject, String body) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, body);
        return intent;
    }

    public static boolean isLaunchModIntent(@Nullable Intent intent) {
        if (intent == null) {
            return false;
        }
        Uri data = intent.getData();
        if (data != null && !StringUtils.isBlank(data.toString())) {
            return data.toString().contains("secret://invite?id=");
        } else {
            return false;
        }

    }

    public static String getLUNCH_MODIntentId(@Nullable Intent intent) {
        if (intent == null) {
            return null;
        }
        Uri data = intent.getData();
//        boolean isLUNCH_MODE = data != null &&
//                WIRE_SCHEME.equals(data.getScheme()) &&
//                LUNCH_MOD.equals(data.getHost());

        if (data != null) {
            return data.getQueryParameter("id");
        } else {
            return null;
        }
    }

    public static void installApk(Context context, String filePath) {
        try {
            Intent intent = getInstallApkIntent(context.getApplicationContext(), filePath);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.getApplicationContext().startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            LogUtils.i("IntentUtils", "installApk Exception:" + e.getMessage());
        }
    }

    public static Intent getInstallApkIntent(Context context, String filePath) {
        File apkFile = new File(filePath);
        Uri contentUri = getFileUri(context,apkFile);
        LogUtils.i("IntentUtils", "getInstallApkIntent contentUri:" + contentUri + ",context:" + context);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        intent.setDataAndType(contentUri, APK_MEDIA_TYPE);
        return intent;
    }


    public static boolean isAvilible(Context context, String packageName) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(packageName, 0);
            Intent launchIntent=context.getPackageManager().getLaunchIntentForPackage(packageName);
            return packageInfo != null && launchIntent!=null;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean launchAppDetail(Context context, String appPkg, String marketPkg) {
        if (TextUtils.isEmpty(appPkg)) {
            return false;
        }
        try {
            Uri uri = Uri.parse("market://details?id=" + appPkg);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            if (!TextUtils.isEmpty(marketPkg)) {
                intent.setPackage(marketPkg);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void startWebGooglePlay(Context context, String downloadPkg) {
        LogUtils.i("IntentUtils", "startWebGooglePlay downloadPkg:" + downloadPkg + ",context:" + context);
        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + downloadPkg)));
    }

    public static boolean isGooglePlayServicesAvailable(Context context) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        return ConnectionResult.SUCCESS == apiAvailability.isGooglePlayServicesAvailable(context);
    }

    public static boolean isAviliblePackage(Context context, String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return false;
        }
        final PackageManager packageManager = context.getPackageManager();
        List<PackageInfo> packageInfos = packageManager.getInstalledPackages(0);
        List<String> packageNames = new ArrayList<>();
        if (packageInfos != null) {
            for (int i = 0; i < packageInfos.size(); i++) {
                String packName = packageInfos.get(i).packageName;
                packageNames.add(packName);
            }
        }
        return packageNames.contains(packageName);
    }

    public static void shareTextInApp(Activity activity,String text){
        ShareCompat.IntentBuilder intentBuilder = ShareCompat.IntentBuilder.from(activity);
        intentBuilder.setType("text/plain");
        intentBuilder.setText(text);
        Intent intent = intentBuilder.getIntent();
        intent.setClass(activity, ShareActivity.class);
        activity.startActivity(intent);
    }

    public static boolean isInstalledFromGooglePlay(Context context){
        try {
            String installer=context.getPackageManager().getInstallerPackageName(context.getPackageName());
            return installer!=null && installer.equals(GOOGLEPLAY_PACKAGENAME);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Nullable
    public static Uri getFileUri(Context context, File file) {
        if (file == null) {
            return null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (context != null) {
                return FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName() + ".fileprovider", file);
            } else {
                return null;
            }
        } else {
            return Uri.fromFile(file);
        }
    }

    public static Intent getPictureIntent() {
        return getDocumentIntent("image/*");
    }

    public static Intent getDocumentIntent(String mimeType) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType(mimeType);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        return intent;
    }
}
