/**
 * Secret
 * Copyright (C) 2019 Secret
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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;

import com.jsy.secret.sub.swipbackact.utils.LogUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class SelfStartSetting {

    private static HashMap<String, List<String>> hashMap = new HashMap<String, List<String>>() {
        {
            put(RomUtil.ROM_MIUI, Arrays.asList(
                "com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity"
            ));

            put(RomUtil.ROM_EMUI, Arrays.asList(
                "com.huawei.systemmanager/.startupmgr.ui.StartupNormalAppListActivity"
            ));

            put(RomUtil.ROM_VIVO, Arrays.asList(
                "com.vivo.permissionmanager/.activity.BgStartUpManagerActivity"
            ));

            put(RomUtil.ROM_OPPO, Arrays.asList(
                "com.coloros.safecenter/com.coloros.privacypermissionsentry.PermissionTopActivity"
            ));

            put(RomUtil.ROM_FLYME, Arrays.asList(
                "com.meizu.safe/.permission.SmartBGActivity"
            ));

            put(RomUtil.ROM_SAMSUNG, Arrays.asList(
                "com.samsung.android.sm/.app.dashboard.SmartManagerDashBoardActivity",
                "com.samsung.android.sm_cn/.app.dashboard.SmartManagerDashBoardActivity",
                "com.samsung.android.sm/com.samsung.android.sm.ui.cstyleboard.SmartManagerDashBoardActivity",
                "com.samsung.android.sm_cn/com.samsung.android.sm.ui.cstyleboard.SmartManagerDashBoardActivity"
            ));

        }
    };

    public static boolean isSupportSelfStartSetting() {
        if (RomUtil.isMiui()) {
            return true;
        } else if (RomUtil.isEmui()) {
            return true;
        } else if (RomUtil.isVivo()) {
            return false;
        } else if (RomUtil.isOppo()) {
            return true;
        } else if (RomUtil.isFlyme()) {
            return true;
        } else return RomUtil.isSamsung();
    }

    public static void openSelfStartSetting(Context context){
        String room="";
        if(RomUtil.isMiui()){
            room=RomUtil.ROM_MIUI;
        }
        else if(RomUtil.isEmui()){
            room=RomUtil.ROM_EMUI;
        }
        else if(RomUtil.isVivo()){
            room=RomUtil.ROM_VIVO;
        }
        else if(RomUtil.isOppo()){
            room=RomUtil.ROM_OPPO;
        }
        else if(RomUtil.isFlyme()){
            room=RomUtil.ROM_FLYME;
        }
        else if(RomUtil.isSamsung()){
            room=RomUtil.ROM_SAMSUNG;
        }
        if(!TextUtils.isEmpty(room)){
            boolean has = false;
            Set<Map.Entry<String, List<String>>> entries = hashMap.entrySet();
            for (Map.Entry<String, List<String>> entry : entries) {
                String key = entry.getKey();
                List<String> list = entry.getValue();
                if (room.equals(key)) {
                    for (String str : list) {
                        LogUtils.d("SelfStartSetting", "openSelfStartSetting==" + str);
                        try {
                            Intent intent = new Intent();
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            ComponentName componentName = ComponentName.unflattenFromString(str);
                            intent.setComponent(componentName);
                            context.startActivity(intent);
                            has = true;
                            break;
                        } catch (Exception e) {
                            e.printStackTrace();
                            LogUtils.e("SelfStartSetting", "openSelfStartSetting==" + e.getMessage());
                        }
                    }
                }
            }
            if (!has) {
                openAppDetailSetting(context);
            }
        }
    }

    public static void openAppDetailSetting(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setData(Uri.fromParts("package", context.getPackageName(), null));
            context.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
