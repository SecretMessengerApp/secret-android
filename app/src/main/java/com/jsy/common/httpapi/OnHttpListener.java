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
package com.jsy.common.httpapi;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.util.List;

public abstract class OnHttpListener <R extends Serializable> {

    private Class<R> clazz;

    public OnHttpListener() {
        ParameterizedType type = (ParameterizedType) this.getClass()
                .getGenericSuperclass();
        this.clazz = (Class<R>) type.getActualTypeArguments()[0];
    }

    public OnHttpListener(Class<R> clazz) {
        this.clazz = clazz;
    }

    public Class<R> getClazz() {
        return clazz;
    }

    /**
     * request failure
     *
     * @param code
     * @param err
     */
    public abstract void onFail(int code, String err);

    /**
     * request successful
     * <p>
     * Mutually exclusive with {@link #onSuc(List, String) onSuc(List, String)}
     *
     * @param r
     * @param orgJson
     */
    public abstract void onSuc(R r, String orgJson) ;

    /**
     * request successful
     * <p>
     * Mutually exclusive with {@link #onSuc(Serializable, String)  onSuc(Serializable, String)}
     *
     * @param r
     * @param orgJson
     */
    public abstract void onSuc(List<R> r, String orgJson) ;


    public void onComplete() {
    }

}
