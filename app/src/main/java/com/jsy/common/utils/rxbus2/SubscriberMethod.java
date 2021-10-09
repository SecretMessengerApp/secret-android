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
package com.jsy.common.utils.rxbus2;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 *
 * Created by gorden on 2016/7/23.
 */
public class SubscriberMethod {
    public Method method;
    public ThreadMode threadMode;
    public Class<?> eventType;
    public Object subscriber;
    public int code;

    public SubscriberMethod(Object subscriber, Method method, Class<?> eventType, int code, ThreadMode threadMode) {
        this.method = method;
        this.threadMode = threadMode;
        this.eventType = eventType;
        this.subscriber = subscriber;
        this.code = code;
    }

    public void invoke(Object o){
        try {
            Class[] parameterType = method.getParameterTypes();
            if(parameterType != null && parameterType.length == 1){
                method.invoke(subscriber, o);
            }else if(parameterType == null || parameterType.length == 0){
                method.invoke(subscriber);
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }
}
