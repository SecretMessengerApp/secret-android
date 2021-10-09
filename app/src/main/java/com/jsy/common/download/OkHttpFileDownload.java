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
package com.jsy.common.download;

import android.os.Environment;
import android.text.TextUtils;

import com.jsy.common.utils.MD5Util;
import com.jsy.secret.sub.swipbackact.utils.LogUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.UnknownHostException;

import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import okhttp3.ResponseBody;
import retrofit2.HttpException;

public class OkHttpFileDownload {
    private static volatile OkHttpFileDownload instance;

    public static OkHttpFileDownload getInstance() {
        if (null == instance) {
            synchronized (OkHttpFileDownload.class) {
                if (null == instance) {
                    instance = new OkHttpFileDownload();
                }
            }
        }
        return instance;
    }

    public OkHttpFileDownload() {

    }

    public void setInstanceNull() {
        cancelDownload();
        downloadObserver = null;
        lenghtObserver = null;
        instance = null;
    }

    private LenghtObserver lenghtObserver;
    private DownloadObserver downloadObserver;

    private void cancelDownload() {
        LogUtils.i("OkHttpFileDownload", "cancelDownload:");
        if (lenghtObserver != null) {
            lenghtObserver.cancel();
        }
        if (null != downloadObserver) {
            downloadObserver.cancel();
        }

    }

    public void videoDownload(final String picUrl, FileDownloadCallBack callBack) {
        File pictureFolder = Environment.getExternalStorageDirectory();
        File appDir = new File(pictureFolder, "your_video_save_path");
        if (!appDir.exists()) {
            appDir.mkdirs();
        }
        final String filePath = appDir.getPath();
        final String fileName = System.currentTimeMillis() + ".mp4";
        fileDownload(picUrl, filePath, fileName, callBack);
    }

    public void fileDownload(final String fileUrl, final String filePath, final String fileName, final FileDownloadCallBack callBack) {
        fileDownload(fileUrl, filePath, fileName, 0, callBack);
    }

    public void fileDownloadHasLength(final String fileUrl, final String filePath, final String fileName, long totalLength, final FileDownloadCallBack callBack) {
        if (totalLength > 0) {
            fileDownload(fileUrl, filePath, fileName, totalLength, callBack);
        } else {
            fileDownload(fileUrl, filePath, fileName, true, callBack);
        }

    }

    public void fileDownload(final String fileUrl, final String filePath, final String fileName, boolean isReqLength, final FileDownloadCallBack callBack) {
        if (isReqLength) {
            cancelDownload();
            LogUtils.i("OkHttpFileDownload", "fileDownload LenghtObserver");
            final LenghtObserver lenghtObserver = getLenghtObserver(fileUrl, filePath, fileName, callBack);
            getService().downloadFileLength(fileUrl).map(new Function<ResponseBody, Long>() {
                @Override
                public Long apply(ResponseBody responseBody) throws Exception {
                    return responseBody.contentLength();
                }
            }).subscribeOn(Schedulers.io())
                .unsubscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(lenghtObserver);
        } else {
            fileDownload(fileUrl, filePath, fileName, 0, callBack);
        }
    }

    private LenghtObserver getLenghtObserver(final String fileUrl, final String filePath, final String fileName, final FileDownloadCallBack callBack) {
        if (null == lenghtObserver) {
            lenghtObserver = new LenghtObserver(fileUrl, filePath, fileName, callBack);
        } else {
            lenghtObserver.setCallBack(fileUrl, filePath, fileName, callBack);
        }
        return lenghtObserver;
    }

    class LenghtObserver implements Observer<Long> {
        private FileDownloadCallBack callBack;
        private Disposable disposable;
        private String fileUrl;
        private String filePath;
        private String fileName;

        public LenghtObserver(final String fileUrl, final String filePath, final String fileName, FileDownloadCallBack callBack) {
            setCallBack(fileUrl, filePath, fileName, callBack);
        }

        public void setCallBack(final String fileUrl, final String filePath, final String fileName, FileDownloadCallBack callBack) {
            this.callBack = callBack;
            this.fileUrl = fileUrl;
            this.filePath = filePath;
            this.fileName = fileName;
        }

        @Override
        public void onSubscribe(Disposable d) {
            LogUtils.i("OkHttpFileDownload", "LenghtObserver onSubscribe:" + d);
            disposable = d;
            if (null != callBack) {
                callBack.onDownLoadStart();
            }
        }

        @Override
        public void onNext(Long contentLength) {
            LogUtils.i("OkHttpFileDownload", "LenghtObserver onNext:" + contentLength);
            onComplete();
            fileDownload(fileUrl, filePath, fileName, contentLength, callBack);
        }

        @Override
        public void onError(Throwable e) {
            LogUtils.e("OkHttpFileDownload", "LenghtObserver isReqLength onError:" + e.getMessage() + "==e:" + e);
            onComplete();
            if (e instanceof UnknownHostException || e instanceof IOException) {
                if (null != callBack) {
                    callBack.onDownLoadFail(true, 0, 100);
                }
            } else {
                if (null != callBack) {
                    callBack.onDownLoadError(e.getMessage(), 0, 100);
                }
            }
        }

        @Override
        public void onComplete() {
            LogUtils.i("OkHttpFileDownload", "LenghtObserver onComplete:");
            disposable = null;
            lenghtObserver = null;
        }

        public void cancel() {
            if (disposable != null && !disposable.isDisposed()) {
                LogUtils.i("OkHttpFileDownload", "LenghtObserver cancelDownload:");
                disposable.dispose();
                if (null != callBack) {
                    callBack.onDownLoadFail(true, 0, 100);
                }
            }
        }
    }

    private DownloadObserver getDownloadObserver(File target, long lastDownloadSize, long totalLength, final FileDownloadCallBack callBack) {
        if (null == downloadObserver) {
            downloadObserver = new DownloadObserver(target, lastDownloadSize, totalLength, callBack);
        } else {
            downloadObserver.setCallBack(target, lastDownloadSize, totalLength, callBack);
        }
        return downloadObserver;
    }

    class DownloadObserver implements Observer<Boolean> {
        private File target;
        private FileDownloadCallBack callBack;
        private Disposable disposable;
        private long total;
        private long progress;
        private long lastDownloadSize;
        private long totalLength;
        private String mediaType;

        public DownloadObserver(File target, long lastDownloadSize, long totalLength, FileDownloadCallBack callBack) {
            setCallBack(target, lastDownloadSize, totalLength, callBack);
        }

        public void setCallBack(File target, long lastDownloadSize, long totalLength, FileDownloadCallBack callBack) {
            this.callBack = callBack;
            this.target = target;
            this.lastDownloadSize = lastDownloadSize;
            this.totalLength = totalLength;
            LogUtils.i("OkHttpFileDownload", "setCallBack lastDownloadSize:" + lastDownloadSize + ", totalLength:" + totalLength + ", target:" + target);
        }

        public void setMediaType(String mediaType) {
            this.mediaType = mediaType;
        }

        public void setDownProgress(long progress, long total) {
            this.progress = progress;
            this.total = total;
            if (null != callBack) {
                callBack.onDownLoading(progress, total);
            }
        }

        @Override
        public void onSubscribe(Disposable d) {
            LogUtils.i("OkHttpFileDownload", "DownloadObserver onSubscribe:" + d);
            disposable = d;
            if (null != callBack) {
                callBack.onDownLoadStart();
            }
        }

        @Override
        public void onNext(Boolean result) {
            LogUtils.i("OkHttpFileDownload", "DownloadObserver onNext:" + result);
            onComplete();
            if (null != callBack) {
                long size = null != target ? target.length() : 0;
                if (result) {
                    if (size > 0 && size == total) {
                        callBack.onDownLoadSuc(target, mediaType, total);
                    } else {
                        callBack.onDownLoadError("size != total,size=" + size, progress, total);
                    }
                } else {
                    if (size <= total) {
                        callBack.onDownLoadFail(false, progress, total);
                    } else {
                        callBack.onDownLoadError("size > total,size=" + size, progress, total);
                    }
                }
            }
        }

        @Override
        public void onError(Throwable e) {
            LogUtils.e("OkHttpFileDownload", "DownloadObserver onError:" + e.getStackTrace() + "==e:" + e);
            onComplete();
            if (e instanceof retrofit2.HttpException) {
                retrofit2.HttpException httpException = (HttpException) e;
                int code = httpException.code();
                if (code == 416) {
                    long size = null != target ? target.length() : 0;
                    if (lastDownloadSize == size && size > 0) {
                        callBack.onDownLoadSuc(target, mediaType, size);
                    } else {
                        if (null != callBack) {
                            callBack.onDownLoadError(e.getMessage(), progress, total);
                        }
                    }
                } else {
                    if (null != callBack) {
                        callBack.onDownLoadError(e.getMessage(), progress, total);
                    }
                }
            } else if (e instanceof UnknownHostException || e instanceof IOException) {
                if (null != callBack) {
                    callBack.onDownLoadFail(false, progress, total);
                }
            } else {
                if (null != callBack) {
                    callBack.onDownLoadError(e.getMessage(), progress, total);
                }
            }
        }

        @Override
        public void onComplete() {
            LogUtils.i("OkHttpFileDownload", "DownloadObserver onComplete path:" + (null != target ? target.getAbsolutePath() : "empty"));
            downloadObserver = null;
            disposable = null;
        }

        public void cancel() {
            if (disposable != null && !disposable.isDisposed()) {
                LogUtils.i("OkHttpFileDownload", "DownloadObserver cancelDownload:");
                disposable.dispose();
                if (null != callBack) {
                    callBack.onDownLoadFail(true, progress, total);
                }
            }
        }
    }

    private void fileDownload(final String fileUrl, String filePath, String fileName, long totalLength, final FileDownloadCallBack callBack) {
        cancelDownload();
        final File appDir = new File(filePath);
        if (!appDir.exists()) {
            appDir.mkdirs();
        }
        if (TextUtils.isEmpty(fileName)) {
            fileName = MD5Util.MD5(fileUrl);
        }
        final File target = new File(appDir, fileName);
        long lastDownloadSize = null != target && target.exists() ? target.length() : 0;
        String range = "bytes=" + lastDownloadSize + (totalLength > 0 ? "-" + totalLength : "-");
        //"bytes=" + startPos + "-"
        LogUtils.i("OkHttpFileDownload", "videoDownload range:" + range);
        final DownloadObserver downloadObserver = getDownloadObserver(target, lastDownloadSize, totalLength, callBack);
        getService().downloadFile(range, fileUrl)
            .map(new Function<ResponseBody, Boolean>() {
                @Override
                public Boolean apply(ResponseBody responseBody) throws Exception {
                    return downloadFile(responseBody, target, downloadObserver);
                }
            }).subscribeOn(Schedulers.io())
            .unsubscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(downloadObserver);
    }

    private boolean downloadFile(ResponseBody responseBody, File target, DownloadObserver downloadObserver) {
        InputStream inputStream = null;
        RandomAccessFile randomFile = null;
        try {
            inputStream = responseBody.byteStream();
            randomFile = new RandomAccessFile(target, "rw");
            long filePos = randomFile.length();
            randomFile.seek(filePos);
            long fileSizeDownloaded = filePos;
            long contentLength = responseBody.contentLength();
            String mediaType = null != responseBody.contentType() ? responseBody.contentType().toString() : "";
            if (null != downloadObserver) {
                downloadObserver.setMediaType(mediaType);
            }
            if (null != downloadObserver) {
                downloadObserver.setDownProgress(fileSizeDownloaded, contentLength + filePos);
            }
            LogUtils.i("OkHttpFileDownload", "downloadFile,filePos:" + filePos + ", contentLength:" + contentLength + ", mediaType:" + mediaType);
            int read;
            byte[] fileReader = new byte[4096];
            long startTime = System.currentTimeMillis();
            while ((read = inputStream.read(fileReader)) != -1) {
                randomFile.write(fileReader, 0, read);
                fileSizeDownloaded += read;
                if (null != downloadObserver) {
                    downloadObserver.setDownProgress(fileSizeDownloaded, contentLength + filePos);
                }
            }
            return true;
        } catch (IOException e) {
            LogUtils.e("OkHttpFileDownload", "downloadFile,IOException e:" + e.getMessage());
            return false;
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
                if (randomFile != null) {
                    randomFile.close();
                }
                if (responseBody != null) {
                    responseBody.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private DownloadInterfaceService getService() {
        return RetrofitDownloadHolder.getRetrofitInstance().create(DownloadInterfaceService.class);
    }

    public interface FileDownloadCallBack {
        void onDownLoadStart();

        void onDownLoading(long progress, long total);

        void onDownLoadSuc(File file, String mediaType, long total);

        void onDownLoadFail(boolean isCancel, long progress, long total);

        void onDownLoadError(String error, long progress, long total);
    }

}
