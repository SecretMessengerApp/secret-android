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
package com.jsy.common.utils;

import android.os.Handler;
import android.os.Looper;

public class MainHandler extends Handler {

    private static volatile MainHandler instance;

    public static MainHandler getInstance(){
        if(instance==null){
            synchronized (MainHandler.class) {
                if (instance == null) {
                    instance= new MainHandler();
                }
            }
        }
        return instance;
    }

    private MainHandler() {
        super(Looper.getMainLooper());
    }

    public void runOnUiThread(Runnable runnable) {
        if (Looper.getMainLooper()== Looper.myLooper()) {
            runnable.run();
        } else {
           post(runnable);
        }
    }
}
