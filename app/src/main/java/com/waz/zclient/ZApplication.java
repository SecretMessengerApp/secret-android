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
package com.waz.zclient;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Process;

import androidx.annotation.Nullable;

import com.alibaba.android.arouter.launcher.ARouter;
import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.jakewharton.threetenabp.AndroidThreeTen;
import com.jsy.common.model.circle.CircleConstant;
import com.jsy.common.model.db.OrmliteDbHelper;
import com.jsy.secret.sub.swipbackact.utils.CrashHandler;
import com.jsy.secret.sub.swipbackact.utils.LogUtils;
import com.waz.model.AccentColor;
import com.waz.service.assets.AssetServiceParams;
import com.waz.zclient.controllers.IControllerFactory;
import com.waz.zclient.ui.text.TypefaceFactory;
import com.waz.zclient.ui.text.TypefaceLoader;
import com.waz.zclient.utils.ServerConfig;
import com.waz.zclient.utils.WireLoggerTree;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NativeLoader;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

public class ZApplication extends WireApplication implements ServiceContainer {

    private static ZApplication application;

    private static final String FONT_FOLDER = "fonts";

    private TypefaceLoader typefaceloader = new TypefaceLoader() {

        private Map<String, Typeface> typefaceMap = new HashMap<>();

        @Override
        public Typeface getTypeface(String name) {
            if (name == null || "".equals(name)) {
                return null;
            }

            if (typefaceMap.containsKey(name)) {
                return typefaceMap.get(name);
            }

            try {
                Typeface typeface;
                if (name.equals(getString(R.string.wire__glyphs)) ||
                        name.equals(getString(R.string.wire__typeface__redacted))) {
                    typeface = Typeface.createFromAsset(getAssets(), FONT_FOLDER + File.separator + name);
                } else if (name.equals(getString(R.string.wire__typeface__thin))) {
                    typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL);
                } else if (name.equals(getString(R.string.wire__typeface__light))) {
                    typeface = Typeface.create("sans-serif-light", Typeface.NORMAL);
                } else if (name.equals(getString(R.string.wire__typeface__regular))) {
                    typeface = Typeface.create("sans-serif", Typeface.NORMAL);
                } else if (name.equals(getString(R.string.wire__typeface__medium))) {
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL);
                } else if (name.equals(getString(R.string.wire__typeface__bold))) {
                    typeface = Typeface.create("sans-serif", Typeface.BOLD);
                } else {
                    Timber.e("Couldn't load typeface: %s", name);
                    return Typeface.DEFAULT;
                }

                typefaceMap.put(name, typeface);
                return typeface;
            } catch (Throwable t) {
                Timber.e(t, "Couldn't load typeface: %s", name);
                return null;
            }
        }
    };

    static {
        AssetServiceParams.setSaveImageDirName("Secret");
    }


    public static ZApplication getInstance() {
        return application;
    }

    public static ZApplication from(@Nullable Activity activity) {
        return activity != null ? (ZApplication) activity.getApplication() : null;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  LifeCycle
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    private boolean shouldInit() {
        ActivityManager am = ((ActivityManager) getSystemService(Context.ACTIVITY_SERVICE));
        List<ActivityManager.RunningAppProcessInfo> processInfos = am.getRunningAppProcesses();
        String mainProcessName = getPackageName();
        int myPid = Process.myPid();
        for (ActivityManager.RunningAppProcessInfo info : processInfos) {
            if (info.pid == myPid && mainProcessName.equals(info.processName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onCreate() {
        if(!shouldInit())return;
        ServerConfig.initServerConfig(this);
        super.onCreate();
        application = this;
        LogUtils.init(application);
        LogUtils.i(getClass().getSimpleName(),"ZApplication==onCreate==start");
        AndroidThreeTen.init(this);
        TypefaceFactory.getInstance().init(typefaceloader);

        NativeLoader.initNativeLibs(application);
        AndroidUtilities.init((application));

        // refresh
        AccentColor.setColors(AccentColor.loadArray(getApplicationContext(), R.array.original_accents_color));
        initRouter();
        CircleConstant.setPackageName(this);

        // refresh
        AccentColor.setColors(AccentColor.loadArray(getApplicationContext(), R.array.original_accents_color));

//        CerUtils.fileToByteHex(this,false,R.raw.accounttest_isecret_im);
//        CerUtils.fileToByteHex(this,false,R.raw.accounttest_isecret_im_server);

//        CerUtils.fileToByteHex(this,false,R.raw.account_new_isecret_im);

        CrashHandler.init(this);
        this.registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacksImpl());
        LogUtils.i(getClass().getSimpleName(), "ZApplication==onCreate==end");
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        releaseOrmliteDbHelper();
    }

    public Context getZApplication() {
        return ZApplication.this.getApplicationContext();
    }

    public static void setLogLevels() {
        Timber.uprootAll();
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        } else {
            Timber.plant(new WireLoggerTree());
        }
    }

    @Override
    public IControllerFactory getControllerFactory() {
        return controllerFactory();
    }

    public void initRouter() {
        if (BuildConfig.DEBUG) {
            ARouter.openLog();
            ARouter.openDebug();
            ARouter.printStackTrace();
        }
        ARouter.init(this);
        LogUtils.d("ZApplication", "initRouter ARouter isDebug:" + BuildConfig.DEBUG + ",class:" + this);
    }

    private OrmliteDbHelper ormliteDbHelper = null;

    public OrmliteDbHelper getOrmliteDbHelper() {
        if (ormliteDbHelper == null) {
            ormliteDbHelper = OpenHelperManager.getHelper(this, OrmliteDbHelper.class);
        }
        return ormliteDbHelper;
    }

    private void releaseOrmliteDbHelper() {
        if (ormliteDbHelper != null) {
            ormliteDbHelper.close();
        }
        ormliteDbHelper = null;
    }
}
