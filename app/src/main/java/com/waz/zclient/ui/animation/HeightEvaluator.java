/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
package com.waz.zclient.ui.animation;

import android.animation.IntEvaluator;
import androidx.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup;

public class HeightEvaluator extends IntEvaluator {

    private View v;
    public HeightEvaluator(View v) {
        this.v = v;
    }

    @NonNull
    @Override
    public Integer evaluate(float fraction, Integer startValue, Integer endValue) {
        int num = super.evaluate(fraction, startValue, endValue);
        ViewGroup.LayoutParams params = v.getLayoutParams();
        params.height = num;
        v.setLayoutParams(params);
        return num;
    }
}
