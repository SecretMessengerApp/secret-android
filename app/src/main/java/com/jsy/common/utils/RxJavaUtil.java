/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.jsy.common.utils;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;


public class RxJavaUtil {

    public static <T> void run(final OnRxAndroidListener<T> onRxAndroidListener) {

        Observable.create(new ObservableOnSubscribe<T>() {
            @Override
            public void subscribe(@NonNull ObservableEmitter<T> e) {
                try{
                    T t = onRxAndroidListener.doInBackground();
                    e.onNext(t);
                }catch (Exception ex){
                    e.onError(ex);
                }
                e.onComplete();

            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .safeSubscribe(new Observer<T>() {
                    Disposable d = null;

                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        this.d = d;
                    }

                    @Override
                    public void onNext(@NonNull T result) {
                        onRxAndroidListener.onFinish(result);
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        onRxAndroidListener.onError(e);
                    }

                    @Override
                    public void onComplete() {
                        if (d != null && !d.isDisposed()) {
                            d.dispose();
                        }
                    }
                });
    }


    public interface OnRxAndroidListener<T> {

        T doInBackground();

        void onFinish(T result);

        void onError(Throwable e);
    }

}
