/**
 * Secret
 * Copyright (C) 2021 Secret
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.jsy.secret.sub.swipbackact.router;

import android.content.Context;

import com.alibaba.android.arouter.facade.template.IProvider;
import com.jsy.secret.sub.swipbackact.SwipBacActivity;

public interface BaseToMainActivityRouter extends IProvider {
    void onBaseActivityCreate(SwipBacActivity activity, boolean isTheme);

    void onBaseActivityStart(SwipBacActivity activity);

    void onBaseActivityResume(SwipBacActivity activity);

    Context getActivityNewBase(Context newBase);

    void onBaseActivityPause(SwipBacActivity baseActivity);

    void onBaseActivityStop(SwipBacActivity activity);

    void onBaseActivityDestroy(SwipBacActivity activity, boolean isTheme);
}
