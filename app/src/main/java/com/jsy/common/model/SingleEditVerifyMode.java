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
package com.jsy.common.model;

import com.google.gson.Gson;
import java.io.Serializable;

public class SingleEditVerifyMode implements Serializable {
    public SingleEditVerifyBean msgData;
    public String msgType;

    public static SingleEditVerifyMode parseJson(String messgeContent) {
        return new Gson().fromJson(messgeContent, SingleEditVerifyMode.class);
    }

    class SingleEditVerifyBean implements Serializable{
        private String reply;
        private boolean isOpen;
    }
}
