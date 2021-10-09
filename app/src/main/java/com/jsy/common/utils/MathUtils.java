/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.jsy.common.utils;
import java.math.BigDecimal;

public class MathUtils {
    /**
     * @return the value, if it is inside [min, max]
     *         min if the value is smaller then min
     *         max if the value is bigger then max
     */
    public static long clamp(long value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    /**
     * @return the value, if it is inside [min, max]
     *         min if the value is smaller then min
     *         max if the value is bigger then max
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(value, max));
    }

    public static boolean floatEqual(float val1, float val2) {
        return Float.compare(val2, val1) == 0;
    }

    public static Double add(Double val1, Double val2, int scale) {
        if (null == val1) {
            val1 = new Double(0);
        }
        if (null == val2) {
            val2 = new Double(0);
        }
        return new BigDecimal(Double.toString(val1)).add(new BigDecimal(Double.toString(val2)))
            .setScale(scale, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    public static Double subtract(Double val1, Double val2, int scale) {
        if (null == val1) {
            val1 = new Double(0);
        }
        if (null == val2) {
            val2 = new Double(0);
        }
        return new BigDecimal(Double.toString(val1)).subtract(new BigDecimal(Double.toString(val2)))
            .setScale(scale, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    public static String multiply(String val1, String val2, int scale) {

        return new BigDecimal(val1).multiply(new BigDecimal(val2))
            .setScale(scale, BigDecimal.ROUND_HALF_UP).toPlainString();
    }

    public static String divide(String val1, String val2, int scale) {

        return new BigDecimal(val1).divide(new BigDecimal(val2))
            .setScale(scale, BigDecimal.ROUND_HALF_UP).toPlainString();
    }
}
