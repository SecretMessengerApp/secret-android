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
package com.jsy.common.utils.dynamiclanguage;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;

import com.jsy.secret.sub.swipbackact.utils.LogUtils;
import com.waz.zclient.utils.SpUtils;

import java.util.Locale;

/**
 * Updates a context with an alternative language.
 */
public final class DynamicLanguageContextWrapper {

    public static Context updateContext(Context context) {
        return updateContext(context, SpUtils.getLanguage(context));
    }

    private static Context updateContext(Context context, String language) {
        final Resources resources = context.getResources();
        final Configuration config = resources.getConfiguration();
        final Locale oldLocale = LocaleParser.getLocale(config);
        final Locale newLocale = LocaleParser.findBestMatchingLocaleForLanguage(language);
        LogUtils.d("JACK8","oldLocale:"+oldLocale+","+"newLocale:"+newLocale);
        if (oldLocale.equals(newLocale)) {
            LogUtils.d("JACK8","oldLocale==newLocale");
            return context;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            LocaleList defaultList =new LocaleList(newLocale);
            LocaleList.setDefault(defaultList);
            config.setLocales(defaultList);
        }
        Locale.setDefault(newLocale);
        config.setLocale(newLocale);
        Context newContext = context.createConfigurationContext(config);
        resources.updateConfiguration(config, resources.getDisplayMetrics());
        return newContext;

    }
}
