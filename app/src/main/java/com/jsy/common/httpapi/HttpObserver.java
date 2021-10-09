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

import androidx.annotation.NonNull;
import com.jsy.common.model.HttpBaseModel;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

import java.io.Serializable;
import java.net.HttpURLConnection;

public abstract class HttpObserver<S extends Serializable, R extends HttpBaseModel<S>> implements Observer<R> {
    public static final int RET_OK = HttpURLConnection.HTTP_OK;
    public static final int HTTP_UNAUTHORIZED = HttpURLConnection.HTTP_UNAUTHORIZED;
    public static final int ERR_LOCAL = -528;
    public static final int HTTP_NOT_200 = -529;
    public static final int DATA_ERROR = -530;

    protected Disposable disposable;

    @Override
    public void onSubscribe(@NonNull Disposable disposable) {
        this.disposable = disposable;
    }

    @Override
    public void onNext(@NonNull R r) {
        dispose();
        if (HttpObserver.RET_OK == r.code) {
            onSuc(r);
        } else {
            onFail(r.code, r.message);
        }
    }

    @Override
    public void onError(@NonNull Throwable e) {
        dispose();
        onFail(ERR_LOCAL, e.getMessage());
    }

    @Override
    public void onComplete() {
        dispose();
    }

    void dispose() {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    /**
     *
     * @param r
     */
    public abstract void onSuc(R r);

    /**
     * @param code
     * @param error
     */
    public abstract void onFail(int code, String error);
}
