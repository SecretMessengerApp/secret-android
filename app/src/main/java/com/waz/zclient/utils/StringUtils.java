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
package com.waz.zclient.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.widget.TextView;

import com.jsy.common.config.PictureMimeType;
import com.waz.utils.wrappers.AndroidURI;
import com.waz.utils.wrappers.AndroidURIUtil;
import com.waz.utils.wrappers.URI;
import com.waz.zclient.R;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Locale;

public class StringUtils {

    private static Paint paint = new Paint();

    public static boolean isBlank(CharSequence cs) {
        int strLen;
        if (cs == null || (strLen = cs.length()) == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isNotBlank(CharSequence charSequence) {
        return !isBlank(charSequence);
    }

    public static String capitalise(String string) {
        if (isBlank(string)) {
            return string;
        }
        return string.substring(0, 1).toUpperCase(Locale.getDefault()) + string.substring(1);
    }

    public static String formatTimeSeconds(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }

    public static String formatTimeMilliSeconds(long totalMilliSeconds) {
        long totalSeconds = totalMilliSeconds / 1000;
        return formatTimeSeconds(totalSeconds);
    }

    public static URI normalizeUri(URI uri) {
        if (uri == null) {
            return uri;
        }
        URI normalized = uri.normalizeScheme();
        if (normalized.getAuthority() != null) {
            normalized = new AndroidURI(AndroidURIUtil.unwrap(normalized)
                .buildUpon()
                .encodedAuthority(normalized.getAuthority().toLowerCase(Locale.getDefault()))
                .build());
        }
        return AndroidURIUtil.parse(trimLinkPreviewUrls(normalized));
    }

    public static String trimLinkPreviewUrls(URI uri) {
        if (uri == null) {
            return "";
        }
        String str = uri.toString();
        str = stripPrefix(str, "http://");
        str = stripPrefix(str, "https://");
        str = stripPrefix(str, "www\\.");
        str = stripSuffix(str, "/");
        return str;
    }

    public static String stripPrefix(String str, String prefixRegularExpression) {
        String regex = "^" + prefixRegularExpression;
        String[] matches = str.split(regex);
        if (matches.length >= 2) {
            return matches[1];
        }
        return str;
    }

    public static String stripSuffix(String str, String suffixRegularExpression) {
        String regex = suffixRegularExpression + "$";
        String[] matches = str.split(regex);
        if (matches.length > 0) {
            return matches[0];
        }
        return str;
    }

    public static boolean isRTL() {
        return isRTL(Locale.getDefault());
    }

    public static boolean isRTL(Locale locale) {
        final int directionality = Character.getDirectionality(locale.getDisplayName().charAt(0));
        return directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
               directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC;
    }

    public static String formatHandle(String username) {
        if (StringUtils.isBlank(username)) {
            return "";
        }
        return "@" + username;
    }

    public static String truncate(String base, int limit) {
        return base.substring(0, Math.min(limit, base.length()));
    }

    public static class TextDrawing {
        private final Bitmap bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ALPHA_8);
        private final Canvas canvas = new Canvas(bitmap);
        private final ByteBuffer buffer = ByteBuffer.allocate(bitmap.getByteCount());

        public void set(String text) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            canvas.drawText(text, 0, 50 / 2, paint);
            buffer.rewind();
            bitmap.copyPixelsToBuffer(buffer);
        }

        @Override
        public boolean equals(Object o) {
            return o != null && (o instanceof TextDrawing) && Arrays.equals(buffer.array(), ((TextDrawing) o).buffer.array());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(buffer.array());
        }
    }

    public static void tempTextFont(TextView tv, int mimeType) {
        String text = tv.getText().toString().trim();
        String str = mimeType == PictureMimeType.ofAudio() ?
            tv.getContext().getString(R.string.picture_empty_audio_title)
            : tv.getContext().getString(R.string.picture_empty_title);
        String sumText = str + text;
        Spannable placeSpan = new SpannableString(sumText);
        placeSpan.setSpan(new RelativeSizeSpan(0.8f), str.length(), sumText.length(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        tv.setText(placeSpan);
    }

    public static void modifyTextViewDrawable(TextView v, Drawable drawable, int index) {
        drawable.setBounds(0, 0, drawable.getMinimumWidth(), drawable.getMinimumHeight());
        if (index == 0) {
            v.setCompoundDrawables(drawable, null, null, null);
        } else if (index == 1) {
            v.setCompoundDrawables(null, drawable, null, null);
        } else if (index == 2) {
            v.setCompoundDrawables(null, null, drawable, null);
        } else {
            v.setCompoundDrawables(null, null, null, drawable);
        }
    }

    /**
     * <p>Checks if CharSequence contains a search CharSequence, handling {@code null}.
     * This method uses {@link String#indexOf(String)} if possible.</p>
     *
     * <p>A {@code null} CharSequence will return {@code false}.</p>
     *
     * <pre>
     * StringUtils.contains(null, *)     = false
     * StringUtils.contains(*, null)     = false
     * StringUtils.contains("", "")      = true
     * StringUtils.contains("abc", "")   = true
     * StringUtils.contains("abc", "a")  = true
     * StringUtils.contains("abc", "z")  = false
     * </pre>
     *
     * @param seq  the CharSequence to check, may be null
     * @param searchSeq  the CharSequence to find, may be null
     * @return true if the CharSequence contains the search CharSequence,
     *  false if not or {@code null} string input
     * @since 2.0
     * @since 3.0 Changed signature from contains(String, String) to contains(CharSequence, CharSequence)
     */
    public static boolean contains(final CharSequence seq, final CharSequence searchSeq) {
        if (seq == null || searchSeq == null) {
            return false;
        }
        return CharSequenceUtils.indexOf(seq, searchSeq, 0) >= 0;
    }

    /**
     * <p>Checks if CharSequence contains a search CharSequence irrespective of case,
     * handling {@code null}. Case-insensitivity is defined as by
     * {@link String#equalsIgnoreCase(String)}.
     *
     * <p>A {@code null} CharSequence will return {@code false}.</p>
     *
     * <pre>
     * StringUtils.containsIgnoreCase(null, *) = false
     * StringUtils.containsIgnoreCase(*, null) = false
     * StringUtils.containsIgnoreCase("", "") = true
     * StringUtils.containsIgnoreCase("abc", "") = true
     * StringUtils.containsIgnoreCase("abc", "a") = true
     * StringUtils.containsIgnoreCase("abc", "z") = false
     * StringUtils.containsIgnoreCase("abc", "A") = true
     * StringUtils.containsIgnoreCase("abc", "Z") = false
     * </pre>
     *
     * @param str  the CharSequence to check, may be null
     * @param searchStr  the CharSequence to find, may be null
     * @return true if the CharSequence contains the search CharSequence irrespective of
     * case or false if not or {@code null} string input
     * @since 3.0 Changed signature from containsIgnoreCase(String, String) to containsIgnoreCase(CharSequence, CharSequence)
     */
    public static boolean containsIgnoreCase(final CharSequence str, final CharSequence searchStr) {
        if (str == null || searchStr == null) {
            return false;
        }
        final int len = searchStr.length();
        final int max = str.length() - len;
        for (int i = 0; i <= max; i++) {
            if (CharSequenceUtils.regionMatches(str, true, i, searchStr, 0, len)) {
                return true;
            }
        }
        return false;
    }
}
