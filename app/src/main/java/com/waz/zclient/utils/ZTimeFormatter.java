/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
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
package com.waz.zclient.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import androidx.annotation.Nullable;
import android.text.format.DateFormat;

import com.jsy.common.utils.dynamiclanguage.LocaleParser;
import com.waz.zclient.R;
import com.waz.zclient.ZApplication;

import org.threeten.bp.Duration;
import org.threeten.bp.LocalDateTime;
import org.threeten.bp.ZoneId;
import org.threeten.bp.format.DateTimeFormatter;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

public class ZTimeFormatter {

    public static String getSeparatorTime(@Nullable Context context, LocalDateTime now, LocalDateTime then, boolean is24HourFormat, ZoneId timeZone, boolean epocIsJustNow) {
        return getSeparatorTime(context, now, then, is24HourFormat, timeZone, epocIsJustNow, true);
    }

    public static String getSeparatorTime(@Nullable Context context, LocalDateTime now, LocalDateTime then, boolean is24HourFormat, ZoneId timeZone, boolean epocIsJustNow, boolean showWeekday) {
        return getSeparatorTime(context, now, then, is24HourFormat, timeZone, epocIsJustNow, showWeekday, false);
    }

    private static String getSeparatorTime(@Nullable Context context, LocalDateTime now, LocalDateTime then, boolean is24HourFormat, ZoneId timeZone, boolean epocIsJustNow, boolean showWeekday, boolean defaultLocale) {
        if (context == null) {
            return "";
        }
        Resources res;
        if (defaultLocale) {
            res = getEnglishResources(context);
        } else {
            res = context.getResources();
        }

        final boolean isLastTwoMins = now.minusMinutes(2).isBefore(then) || (epocIsJustNow && then.atZone(timeZone).toInstant().toEpochMilli() == 0);
        final boolean isLastSixtyMins = now.minusMinutes(60).isBefore(then);

        if (isLastTwoMins) {
            return res.getString(R.string.timestamp__just_now);
        } else if (isLastSixtyMins) {
            int minutes = (int) Duration.between(then, now).toMinutes();
            return res.getQuantityString(R.plurals.timestamp__x_minutes_ago, minutes, minutes);
        }

        final String time = is24HourFormat ? res.getString(R.string.timestamp_pattern__24h_format) :
                            res.getString(R.string.timestamp_pattern__12h_format);
        final boolean isSameDay = now.toLocalDate().atStartOfDay().isBefore(then);
        final boolean isThisYear = now.getYear() == then.getYear();
        final String pattern;
        if (isSameDay) {
            pattern = time;
        } else if (isThisYear) {
            if (showWeekday) {
                pattern = res.getString(R.string.timestamp_pattern__date_and_time__no_year, time);
            } else {
                pattern = res.getString(R.string.timestamp_pattern__date_and_time__no_year_no_weekday, time);
            }
        } else {
            if (showWeekday) {
                pattern = res.getString(R.string.timestamp_pattern__date_and_time__with_year, time);
            } else {
                pattern = res.getString(R.string.timestamp_pattern__date_and_time__with_year_no_weekday, time);
            }
        }
        try {
            Locale locale = LocaleParser.findBestMatchingLocaleForLanguage(SpUtils.getLanguage(ZApplication.getInstance()));
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(pattern);
            dateTimeFormatter = dateTimeFormatter.withLocale(locale);
            return dateTimeFormatter.format(then.atZone(timeZone));
        } catch (Exception e) {
            if (!defaultLocale) {
                return getSeparatorTime(context, now, then, is24HourFormat, timeZone, epocIsJustNow, showWeekday, true);
            } else {
                return "";
            }
        }
    }

    private static Resources getEnglishResources(Context context) {
        Configuration conf = context.getResources().getConfiguration();
        conf = new Configuration(conf);
        conf.setLocale(Locale.ENGLISH);
        Context localizedContext = context.createConfigurationContext(conf);
        return localizedContext.getResources();
    }

    public static String getSingleMessageTime(Context context, Date date) {
        return getSingleMessageTime(context, date, false);
    }

    private static String getSingleMessageTime(Context context, Date date, boolean defaultLocale) {
        Resources resources = defaultLocale ? getEnglishResources(context) : context.getResources();

        GregorianCalendar gcCurrent = new GregorianCalendar();
        gcCurrent.setTime(new Date());
        int currentYear = gcCurrent.get(GregorianCalendar.YEAR);
        int currentMonth = gcCurrent.get(GregorianCalendar.MONTH)+1;
        int currentDay = gcCurrent.get(GregorianCalendar.DAY_OF_MONTH);

        GregorianCalendar gcSrc = new GregorianCalendar();
        gcSrc.setTime(date);
        int srcYear = gcSrc.get(GregorianCalendar.YEAR);
        int srcMonth = gcSrc.get(GregorianCalendar.MONTH)+1;
        int srcDay = gcSrc.get(GregorianCalendar.DAY_OF_MONTH);

        String pattern;
        if(currentYear == srcYear){
            if (currentMonth == srcMonth && currentDay == srcDay) {
                boolean is24HourFormat = DateFormat.is24HourFormat(context);
                pattern = is24HourFormat ? resources.getString(R.string.timestamp_pattern__24h_format) :
                    resources.getString(R.string.timestamp_pattern__12h_format);
            } else {
                pattern = resources.getString(R.string.timestamp_pattern__date_format);
            }
        } else {
            pattern = resources.getString(R.string.timestamp_pattern__year_format);
        }
        try {
            Locale locale = LocaleParser.findBestMatchingLocaleForLanguage(SpUtils.getLanguage(ZApplication.getInstance()));
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(pattern);
            dateTimeFormatter = dateTimeFormatter.withLocale(locale);
            return dateTimeFormatter.format(DateConvertUtils.asLocalDateTime(date).atZone(ZoneId.systemDefault()));
        } catch (Exception e) {
            if (!defaultLocale) {
                return getSingleMessageTime(context, date, true);
            } else {
                return "";
            }
        }
    }

}
