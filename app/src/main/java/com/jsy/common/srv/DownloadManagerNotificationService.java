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
package com.jsy.common.srv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import androidx.core.app.NotificationCompat;
import android.text.TextUtils;
import android.widget.RemoteViews;

import com.jsy.common.download.OkHttpFileDownload;
import com.jsy.common.model.DownLoadNotifyModel;
import com.jsy.common.model.circle.CircleConstant;
import com.jsy.secret.sub.swipbackact.utils.LogUtils;
import com.waz.zclient.R;
import com.waz.zclient.utils.IntentUtils;
import com.waz.zclient.utils.MainActivityUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DownloadManagerNotificationService extends Service {

    private static String TAG = DownloadManagerNotificationService.class.getSimpleName();

    private static int NOTIFICATION_ID = 1;
    public static final int NO_NOTIFICATION = -1;

    public static int createNotificationId() {
        return ++NOTIFICATION_ID % Integer.MAX_VALUE;
    }

    public static final int DOWN_SRV_DOWN_APP_START = 50000;
    public static final int DOWN_SRV_DOWN_APP_ING = 50010;
    public static final int DOWN_SRV_DOWN_APP_MUTUTABLE = 50020;
    public static final int DOWN_SRV_DOWN_APP_OVER_RANGE = 50030;
    public static final int DOWN_SRV_DOWN_APP_FAIL = 50040;
    public static final int DOWN_SRV_DOWN_APP_FINISH = 50050;

    private static final int TASK_NUM = 1;
    private NotificationManager notificationManager;

    private NotificationHandler notificationHandler;
    private final static Map<String, String> downloadMap = Collections.synchronizedMap(new LinkedHashMap<String, String>());
    private static Map<String, Float> downloadNotificationProgress = new HashMap<>();
    private static Map<String, RemoteViews> notificationViews = new HashMap<>();

    private static Map<String, List<Handler>> uiHandlers = new HashMap<>();
    private static final List<DownLoadNotifyModel> downloadingNotificationModels = new ArrayList<>();
    private static final List<DownLoadNotifyModel> waitingDownloadNotificationModels = new ArrayList<>();

    private static volatile long lastNotificationMs = 0;
    public static final long NOTIFICATION_REFRESH_MS = 500;

    public static final long APK_MIN_SIZE = 1 * 1024 * 1024;

    private final String EMPTY_PROGRESS = "0.00%";

    private boolean isStartService = false;
    private long lastTime;

    public interface ISub {
        DownloadManagerNotificationService getService();
    }

    public class DownloadManagerNotificationServiceImpl extends Binder implements ISub {
        @Override
        public DownloadManagerNotificationService getService() {
            return DownloadManagerNotificationService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        isStartService = false;
        return new DownloadManagerNotificationServiceImpl();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationHandler = new NotificationHandler(Looper.myLooper());
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isStartService = true;
        DownLoadNotifyModel input = null != intent ? (DownLoadNotifyModel) intent.getParcelableExtra(DownLoadNotifyModel.TAG) : null;
        downNewFile(input, null);
        return Service.START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isStartService = false;
        uiHandlers.clear();
        downloadingNotificationModels.clear();
        downloadNotificationProgress.clear();
        waitingDownloadNotificationModels.clear();
        notificationViews.clear();
    }

    public void cancelCurDown() {
        OkHttpFileDownload.getInstance().setInstanceNull();
        downloadingNotificationModels.clear();
    }

    public boolean isDownloading(String url) {
        if (url == null) {
            return false;
        }
        for (int i = downloadingNotificationModels.size() - 1; i >= 0; i--) {
            if (url.equals(downloadingNotificationModels.get(i).getUrl())) {
                return true;
            }
        }
        return false;
    }

    public boolean isWaitDownloading(String url) {
        if (url == null) {
            return false;
        }
        for (int i = waitingDownloadNotificationModels.size() - 1; i >= 0; i--) {
            if (url.equals(waitingDownloadNotificationModels.get(i).getUrl())) {
                return true;
            }
        }
        return false;
    }

    public synchronized void putUiHandler(String url, Handler handler) {
        if (url != null && handler != null) { // add
            if (uiHandlers.get(url) == null) {
                uiHandlers.put(url, new ArrayList<Handler>());
            }
            uiHandlers.get(url).add(handler);
        }
    }

    public synchronized void removeUiHandler(String url, Handler handler) {
        if (url != null && handler != null) {
            if (uiHandlers.containsKey(url)) { // remove
                uiHandlers.get(url).remove(handler);
                if (uiHandlers.get(url).size() == 0) {
                    uiHandlers.remove(url);
                }
            }
        } else if (url != null) {
            removeUiHandler(url);
        } else if (handler != null) {
            removeUiHandler(handler);
        }
    }


    /**
     * @param url
     */
    private synchronized void removeUiHandler(String url) {
        uiHandlers.remove(url);
    }

    /**
     * @param handler
     */
    private synchronized void removeUiHandler(Handler handler) {
        Set<String> keys = uiHandlers.keySet();
        Iterator<String> iterator = keys.iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            List<Handler> handlers = uiHandlers.get(key);
            for (int i = handlers.size() - 1; i >= 0; i--) {
                if (handlers.get(i).equals(handler)) {
                    handlers.remove(handler);
                }
            }
            if (handlers.size() == 0) {
                uiHandlers.remove(key);
            }
        }
    }

    public void downNewFile(DownLoadNotifyModel input, final Handler handler) {
        LogUtils.i(TAG, "downNewFile input:" + input + ", handler:" + handler + ", isStartService:" + isStartService + ", downloadingNotificationModels.size:" + downloadingNotificationModels.size());
        if (null == input) {
            return;
        }
        if (isDownloading(input.getUrl())) {
            LogUtils.i(TAG, "downNewFile isDownloading(input.getUrl()):true, input.getUrl():" + input.getUrl());
            if (handler != null) {
                DownLoadNotifyModel notificationModel = DownLoadNotifyModel.createNotificationModel(input, 0,
                    1, EMPTY_PROGRESS, DOWN_SRV_DOWN_APP_MUTUTABLE, "DOWN_SRV_DOWN_APP_MUTUTABLE");
                Message message = handler.obtainMessage(notificationModel.getWhat(), notificationModel);
                handler.sendMessage(message);
            } else {
                //...
            }
        } else if (downloadingNotificationModels.size() >= TASK_NUM) {
            LogUtils.i(TAG, "downNewFile downloadingNotificationModels.size():" + downloadingNotificationModels.size() + ", input.getUrl():" + input.getUrl());
            if (handler != null) {
                DownLoadNotifyModel notificationModel = DownLoadNotifyModel.createNotificationModel(input, 0,
                    1, EMPTY_PROGRESS, DOWN_SRV_DOWN_APP_OVER_RANGE, "DOWN_SRV_DOWN_APP_OVER_RANGE");
                Message message = handler.obtainMessage(notificationModel.getWhat(), notificationModel);
                handler.sendMessage(message);
            } else {
                //...
            }
            addDownLoadQueue(input, handler);
        } else {
            if (waitingDownloadNotificationModels.isEmpty()) {
                startDownFile(input, handler);
            } else {
                addDownLoadQueue(input, handler);
                waitingDownFile();
            }
        }
    }

    private void addDownLoadQueue(final DownLoadNotifyModel input, final Handler handler) {
        if (isWaitDownloading(input.getUrl())) {
            return;
        }
        putUiHandler(input.getUrl(), handler);
        downloadNotificationProgress.put(input.getUrl(), Float.valueOf(0));
        waitingDownloadNotificationModels.add(input);
    }

    private void startDownFile(final DownLoadNotifyModel input, final Handler handler) {
        putUiHandler(input.getUrl(), handler);
        downloadNotificationProgress.put(input.getUrl(), Float.valueOf(0));
        downloadingNotificationModels.add(input);
        downOKHttpFile(input);
    }

    private void waitingDownFile() {
        if (waitingDownloadNotificationModels.isEmpty() || downloadingNotificationModels.size() >= TASK_NUM) {
            return;
        }
        final DownLoadNotifyModel input = waitingDownloadNotificationModels.remove(0);
        if (null == input) {
            waitingDownFile();
            return;
        }
        downloadingNotificationModels.add(input);
        downOKHttpFile(input);
    }

    private void downOKHttpFile(final DownLoadNotifyModel input) {
        final String fileUrl = input.getUrl();
        final String filePath = CircleConstant.DOWNLOAD_PATH;
        final String fileName = input.getMd5FileName();
        LogUtils.i(TAG, "downOKHttpFile,fileUrl:" + fileUrl);
        OkHttpFileDownload.getInstance().fileDownload(fileUrl, filePath, fileName, true, new OkHttpFileDownload.FileDownloadCallBack() {
            @Override
            public void onDownLoadStart() {
                LogUtils.i(TAG, "downOKHttpFile onDownLoadStart");
                DownLoadNotifyModel startnotificationModel = DownLoadNotifyModel.createNotificationModel(input, 0,
                    100, EMPTY_PROGRESS, DOWN_SRV_DOWN_APP_START, null);
                notifyNotification(startnotificationModel);
                dispachUiHandlers(startnotificationModel);
            }

            @Override
            public void onDownLoading(long progress, long total) {
                float percent = progress * 100F / total;
                long currentTime = System.currentTimeMillis();
                float floatValue = downloadNotificationProgress.containsKey(input.getUrl()) ? downloadNotificationProgress.get(input.getUrl()).floatValue() : 0;
                if (percent - floatValue >= 0.1F && currentTime - lastNotificationMs > NOTIFICATION_REFRESH_MS) {
                    lastNotificationMs = currentTime;
                    downloadNotificationProgress.put(input.getUrl(), percent);
                    String subtext = String.format("%.2f%%", percent);
                    DownLoadNotifyModel notificationModel = DownLoadNotifyModel.createNotificationModel(input, (int) progress,
                        (int) total, subtext, DOWN_SRV_DOWN_APP_ING, null);
                    notifyNotification(notificationModel);
                    dispachUiHandlers(notificationModel);
                }
            }

            @Override
            public void onDownLoadSuc(File file, String mediaType, long total) {
                LogUtils.i(TAG, "downOKHttpFile onDownLoadSuc total" + total);
                DownLoadNotifyModel notificationModel = DownLoadNotifyModel.createNotificationModel(input, 100,
                    100, "100%", DOWN_SRV_DOWN_APP_FINISH, null);
                if (!TextUtils.isEmpty(mediaType)) {
                    notificationModel.setMediaType(mediaType);
                }
                notifyNotification(notificationModel);
                dispachUiHandlers(notificationModel);
                removeNotificationModel(notificationModel);
                waitingDownFile();
            }

            @Override
            public void onDownLoadFail(boolean isCancel, long progress, long total) {
                LogUtils.i(TAG, "downOKHttpFile onDownLoadFail progress:" + progress + ", total:" + total);
//                File apkFile = new File(filePath, fileName);
//                if (apkFile != null && apkFile.exists()) {
//                    apkFile.delete();
//                }
                DownLoadNotifyModel notificationModel = DownLoadNotifyModel.createNotificationModel(input, (int) progress,
                    (int) (total > 0 ? total : 1), EMPTY_PROGRESS, DOWN_SRV_DOWN_APP_FAIL, "");
                notifyNotification(notificationModel);
                dispachUiHandlers(notificationModel);
                removeNotificationModel(notificationModel);
            }

            @Override
            public void onDownLoadError(String error, long progress, long total) {
                LogUtils.i(TAG, "downOKHttpFile onDownLoadError progress:" + progress + ", total:" + total + ", error:" + error);
                File apkFile = new File(filePath, fileName);
                if (apkFile != null && apkFile.exists()) {
                    apkFile.delete();
                }
                DownLoadNotifyModel notificationModel = DownLoadNotifyModel.createNotificationModel(input, (int) progress,
                    (int) (total > 0 ? total : 1), EMPTY_PROGRESS, DOWN_SRV_DOWN_APP_FAIL, error);
                notifyNotification(notificationModel);
                dispachUiHandlers(notificationModel);
                removeNotificationModel(notificationModel);
            }
        });
    }

    private synchronized void notifyNotification(DownLoadNotifyModel notificationModel) {
        Message message = notificationHandler.obtainMessage(notificationModel.getWhat(), notificationModel);
        notificationHandler.sendMessage(message);
    }

    private synchronized void dispachUiHandlers(DownLoadNotifyModel notificationModel) {
        if (uiHandlers.containsKey(notificationModel.getUrl()) && uiHandlers.get(notificationModel.getUrl()) != null) {
            List<Handler> handlers = uiHandlers.get(notificationModel.getUrl());
            for (int i = handlers.size() - 1; i >= 0; i--) {
                Message message = handlers.get(i).obtainMessage(notificationModel.getWhat(), notificationModel);
                handlers.get(i).sendMessage(message);
            }
        }
    }

    private synchronized void removeNotificationModel(DownLoadNotifyModel notificationModel) {
        if (uiHandlers.containsKey(notificationModel.getUrl()) && uiHandlers.get(notificationModel.getUrl()) != null) {
            uiHandlers.remove(notificationModel.getUrl());
        }

        for (int i = downloadingNotificationModels.size() - 1; i >= 0; i--) {
            if (downloadingNotificationModels.get(i).getUrl().equals(notificationModel.getUrl())) {
                downloadingNotificationModels.remove(i);
            }
        }
        notificationViews.remove(notificationModel.getUrl());
        downloadNotificationProgress.remove(notificationModel.getUrl());
    }

    private void applyChannel26(DownLoadNotifyModel notificationModel) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(notificationModel.getChannelId(), notificationModel.getChannelId(), NotificationManager.IMPORTANCE_LOW);
            channel.enableVibration(false);
            channel.setShowBadge(false);
            channel.enableLights(false);
            channel.setBypassDnd(true);
            channel.setSound(null, null);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private NotificationCompat.Builder createCommonBuidlerWithRemoteView(DownLoadNotifyModel notificationModel) {
        applyChannel26(notificationModel);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, notificationModel.getChannelId());
        builder.setSmallIcon(R.drawable.ic_menu_logo);
        builder.setTicker(notificationModel.getContentTitle());
        builder.setAutoCancel(notificationModel.isAutoCancel());
        if (!TextUtils.isEmpty(notificationModel.getBigText())) {
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(notificationModel.getBigText()));
        }
        builder.setSubText(notificationModel.getSubText());
        if (notificationModel.getProgress() == notificationModel.getLength() && apkFileExists(notificationModel.getMd5FileName())) {
            builder.setContentIntent(notificationModel.getDownloadSuccessPendingIntent());
        }
        builder.setWhen(System.currentTimeMillis());
        RemoteViews contentView;
        if (notificationViews.get(notificationModel.getUrl()) != null) {
            contentView = notificationViews.get(notificationModel.getUrl());
        } else {
            contentView = new RemoteViews(getPackageName(), R.layout.lay_download_notification_progress);
            notificationViews.put(notificationModel.getUrl(), contentView);
        }
        contentView.setImageViewResource(R.id.ivIcon, notificationModel.getNotificationIconResId());
        contentView.setTextViewText(R.id.tvTitle, notificationModel.getContentTitle());
        contentView.setTextViewText(R.id.tvProgress, notificationModel.getSubText());

        LogUtils.d(TAG, "notificationModel progress:" + notificationModel.getProgress() + "  length:" + notificationModel.getLength());
        contentView.setProgressBar(R.id.pbProgress, notificationModel.getLength(), notificationModel.getProgress(), false);

        builder.setCustomContentView(contentView);
        return builder;
    }


    private void updateDownloadingNotification(DownLoadNotifyModel notificationModel) {
        if (null != notificationModel && notificationModel.getNotificationId() != NO_NOTIFICATION) {
            NotificationCompat.Builder builder = createCommonBuidlerWithRemoteView(notificationModel);
            Notification notification = builder.build();
            notification.flags = notificationModel.getFlags();
            notificationManager.notify(notificationModel.getNotificationId(), notification);
        }
    }

    public void cancelNotification(DownLoadNotifyModel notificationModel) {
        if (null != notificationModel && notificationModel.getNotificationId() != NO_NOTIFICATION) {
            notificationViews.remove(notificationModel.getUrl());
            notificationManager.cancel(notificationModel.getNotificationId());
        }
    }

    private void downloadDoneOperate(DownLoadNotifyModel notificationModel) {
        if (null != notificationModel) {
            LogUtils.i(TAG, "NotificationHandler DownloadDone installApk:" + notificationModel.isApk() + ", url:" + notificationModel.getUrl());
            if (notificationModel.isApk() || IntentUtils.APK_MEDIA_TYPE.equals(notificationModel.getMediaType())) {
                IntentUtils.installApk(this, getApkFilePath(notificationModel.getMd5FileName()));
            }
            cancelNotification(notificationModel);
        }
        if (isStartService && downloadingNotificationModels.isEmpty() && waitingDownloadNotificationModels.isEmpty()) {
            stopSelf();
        }
    }

    /**
     * @param md5FileName
     * @return
     */
    public static String getApkFilePath(String md5FileName) {
        return CircleConstant.DOWNLOAD_PATH + File.separator + md5FileName;
    }

    /**
     * @param md5FileName
     * @return
     */
    public static boolean apkFileExists(String md5FileName) {
        File apkFile = new File(getApkFilePath(md5FileName));
        return apkFile.exists() && apkFile.length() > 0;
    }

    class NotificationHandler extends Handler {

        public NotificationHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg != null) {
                DownLoadNotifyModel notificationModel = (DownLoadNotifyModel) msg.obj;
                switch (msg.what) {
                    case DOWN_SRV_DOWN_APP_START:
                        LogUtils.i(TAG, "NotificationHandler Download start");
                    case DOWN_SRV_DOWN_APP_ING:
                        updateDownloadingNotification(notificationModel);
                        break;
                    case DOWN_SRV_DOWN_APP_FINISH:
                        LogUtils.i(TAG, "NotificationHandler DownloadDone");
                        updateDownloadingNotification(notificationModel);
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastTime >= NOTIFICATION_REFRESH_MS) {
                            downloadDoneOperate(notificationModel);
                            lastTime = currentTime;
                        }
                        break;
                    case DOWN_SRV_DOWN_APP_FAIL: {
                        LogUtils.i(TAG, "NotificationHandler Download fail");
                        cancelNotification(notificationModel);
                    }
                    break;
                }
            }
        }
    }

    public static void startDownLoadService(Context context, String downloadName, String downloadUrl) {
        startDownLoadService(context, downloadName, downloadUrl, false);
    }

    public static void startDownLoadService(Context context, String downloadName, String downloadUrl, boolean isApk) {
        LogUtils.i(TAG, "startDownLoadService downloadName:" + downloadName + ", downloadUrl:" + downloadUrl + ", isApk:" + isApk);
        DownLoadNotifyModel notificationModel = DownLoadNotifyModel.createNotificationModel(downloadUrl, isApk,
            MainActivityUtils.getMd5(downloadUrl.replace("\\", "")),
            DownloadManagerNotificationService.createNotificationId(),
            TextUtils.isEmpty(downloadName) ? context.getResources().getString(R.string.app_name) : downloadName,
            R.drawable.ic_launcher_wire);
        notificationModel.setApk(isApk);
        notificationModel.setDownloadSuccessPendingIntent(PendingIntent.getActivity(context, 0,
            IntentUtils.getInstallApkIntent(context, getApkFilePath(notificationModel.getMd5FileName())), 0));
        Intent intent = new Intent(context, DownloadManagerNotificationService.class);
        intent.putExtra(DownLoadNotifyModel.TAG, notificationModel);
        context.startService(intent);
    }
}
