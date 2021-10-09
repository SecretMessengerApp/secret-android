/**
 * Secret
 * Copyright (C) 2018 Secret
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
package com.jsy.secret.sub.swipbackact;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public class ActivityContainner {

    private static List<SwipBacActivity> baseActivities = new ArrayList<>();

    public static List<SwipBacActivity> getBaseActivities() {
        return baseActivities;
    }

    public static final int getActivityCount() {
        return baseActivities.size();
    }

    public static final boolean hasActivityActive() {
        if (getActivityCount() <= 0) {
            return false;
        }
        String topActivity = getTopActivity(baseActivities.get(0).getApplicationContext());
        for (SwipBacActivity swipBacActivity : baseActivities) {
            if (swipBacActivity.getClass().getName().equals(topActivity)) {
                return true;
            }
        }
        return false;
    }

    public static final boolean activityIsInContainner(String actName) {
        for (SwipBacActivity swipBacActivity : baseActivities) {
            if (swipBacActivity.getClass().getName().equals(actName)) {
                return true;
            }
        }
        return false;
    }

    public static final void add(SwipBacActivity swipBacActivity) {
        baseActivities.add(swipBacActivity);
    }

    public static final void remove(SwipBacActivity swipBacActivity) {
        baseActivities.remove(swipBacActivity);
    }

    public static final void finishAndRemoveAll() {
        for (SwipBacActivity swipBacActivity : baseActivities) {
            swipBacActivity.finish();
        }
    }


    public static final void finishAndRemoveAllBeside(String[] classNames) {
        List<SwipBacActivity> toFinishList = new ArrayList<>();
        for (SwipBacActivity swipBacActivity : baseActivities) {
            boolean needIgnore = false;
            for (String className : classNames) {
                if (swipBacActivity.getClass().getName().equals(className)) {
                    needIgnore = true;
                }
            }
            if (!needIgnore) {
                toFinishList.add(swipBacActivity);
            }
        }
        baseActivities.removeAll(toFinishList);
        for (SwipBacActivity swipBacActivity : toFinishList) {
            swipBacActivity.finish();
        }
    }


    public static final void finishAndRemoveAllBeside(String className) {
        for (SwipBacActivity swipBacActivity : baseActivities) {
            if (!swipBacActivity.getClass().getName().equals(className)) {
                swipBacActivity.finish();
            }
        }
    }

    public static Activity getPenultimateActivity(Activity currentActivity) {
        Activity activity = null;
        try {
            if (baseActivities.size() > 1) {
                activity = baseActivities.get(baseActivities.size() - 2);
                if (currentActivity.equals(activity)) {
                    int index = baseActivities.indexOf(currentActivity);
                    if (index > 0) {
                        activity = baseActivities.get(index - 1);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return activity;
    }

    public static final boolean isTopActivity(String activityStr) {
        if (activityIsInContainner(activityStr)) {
            Activity topActivity = getTopActivity();
            return null == topActivity ? false : topActivity.getClass().getName().equals(activityStr);
        } else {
            return false;
        }
    }

    public static final Activity getTopActivity() {
        int count = getActivityCount();
        if (count > 0) {
            return baseActivities.get(count - 1);
        } else {
            return null;
        }
    }

    public static final boolean isTopActivity(Activity activity) {
        String topActivity = getTopActivity(activity.getApplicationContext());
        return activity.getClass().getName().equals(topActivity) || (getActivityCount() > 0 && activity.getClass().getName().equals(baseActivities.get(0).getClass().getName()));
    }

    public static final String getTopActivity(Context context) {
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningTaskInfo> runningTaskInfos = manager.getRunningTasks(1);
            if (runningTaskInfos != null && runningTaskInfos.size() > 0) {
                return runningTaskInfos.get(0).topActivity.getClassName();
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static final boolean appOnForeground(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
        if (appProcesses == null) {
            return false;
        }
        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            if (appProcess.processName.equals(context.getPackageName()) && appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                return true;
            }
        }
        return false;
    }
}
