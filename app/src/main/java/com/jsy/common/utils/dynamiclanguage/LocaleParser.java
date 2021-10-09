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

import android.content.res.Configuration;
import android.content.res.Resources;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.os.ConfigurationCompat;
import androidx.core.os.LocaleListCompat;

import java.util.Arrays;
import java.util.Locale;

public final class LocaleParser {

    static final String[] LANGUAGES = {"en_US", "ko_KR", "zh_CN", "zh_TW", "ja_JP", "fr_FR", "it_IT", "ru_RU", "th_TH"};

    private LocaleParser() {
    }

    /**
     * Given a language, gets the best choice from the apps list of supported languages and the
     * Systems set of languages.
     */
    public static Locale findBestMatchingLocaleForLanguage(@Nullable String language) {
        if (TextUtils.equals(language, "default")) {
            return findBestSystemLocale();
        } else {
            final Locale locale = LanguageString.parseLocale(language);
            if (appSupportsTheExactLocale(locale)) {
                return locale;
            } else {
                return Locale.ENGLISH;
            }
        }
    }

    private static boolean appSupportsTheExactLocale(@Nullable Locale locale) {
        if (locale == null) {
            return false;
        }
        return Arrays.asList(LANGUAGES).contains(locale.toString());
    }

    /**
     * Get the first preferred language the app supports.
     */
    private static Locale findBestSystemLocale() {
        final Configuration config = Resources.getSystem().getConfiguration();

        //final Locale firstMatch = ConfigurationCompat.getLocales(config)
        //    .getFirstMatch(LANGUAGES);
        //
        //if (firstMatch != null) {
        //    return firstMatch;
        //}
        //
        //return Locale.ENGLISH;

        return getLocale(config);
    }

    static Locale getLocale(Configuration config){
        LocaleListCompat locales = ConfigurationCompat.getLocales(config);
        if (locales.isEmpty()) {
            return Locale.ENGLISH;
        }else {
            return locales.get(0);
        }
    }
}
