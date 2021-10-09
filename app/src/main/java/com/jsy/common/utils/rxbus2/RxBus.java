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


import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

import java.lang.reflect.Method;
import java.util.*;


/**
 * RxBus
 * Created by gorden on 2016/5/12.
 * update 2017/3/1
 */
@SuppressWarnings("unused")
public class RxBus {
    public static final String LOG_BUS = "RXBUS_LOG";
    private static volatile RxBus defaultInstance;

    private Map<Class, List<Disposable>> subscriptionsByEventType = new HashMap<>();

    private Map<Object, List<Class>> eventTypesBySubscriber = new HashMap<>();

    private Map<Class, List< SubscriberMethod>> subscriberMethodByEventType = new HashMap<>();

    private final Subject<Object> bus;

    private RxBus() {
        this.bus = PublishSubject.create().toSerialized();
    }

    public static RxBus getDefault() {
        RxBus rxBus = defaultInstance;
        if (defaultInstance == null) {
            synchronized (RxBus.class) {
                rxBus = defaultInstance;
                if (defaultInstance == null) {
                    rxBus = new RxBus();
                    defaultInstance = rxBus;
                }
            }
        }
        return rxBus;
    }

    public  <T> Flowable<T> toObservable(Class<T> eventType) {
        return bus.toFlowable(BackpressureStrategy.BUFFER).ofType(eventType);
    }

    private <T> Flowable<T> toObservable(final int code, final Class<T> eventType) {
        return bus.toFlowable(BackpressureStrategy.BUFFER).ofType(Message.class)
                .filter(new Predicate<Message>() {
                    @Override
                    public boolean test(Message o) throws Exception {
                        return o.getCode() == code && eventType.isInstance(o.getObject());
                    }
                }).map(new Function<Message, Object>() {
                    @Override
                    public Object apply(Message o) throws Exception {
                        return o.getObject();
                    }
                }).cast(eventType);
    }

    public void register(Object subscriber) {
        Class<?> subClass = subscriber.getClass();
        Method[] methods = subClass.getDeclaredMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent( Subscribe.class)) {
                Class[] parameterType = method.getParameterTypes();
                if (parameterType != null && parameterType.length == 1) {

                    Class eventType = parameterType[0];

                    addEventTypeToMap(subscriber, eventType);
                    Subscribe sub = method.getAnnotation(Subscribe.class);
                    int code = sub.code();
                    ThreadMode threadMode = sub.threadMode();

                     SubscriberMethod subscriberMethod = new  SubscriberMethod(subscriber, method, eventType, code, threadMode);
                    addSubscriberToMap(eventType, subscriberMethod);

                    addSubscriber(subscriberMethod);
                } else if (parameterType == null || parameterType.length == 0) {

                    Class eventType = BusData.class;

                    addEventTypeToMap(subscriber, eventType);
                     Subscribe sub = method.getAnnotation( Subscribe.class);
                    int code = sub.code();
                     ThreadMode threadMode = sub.threadMode();

                     SubscriberMethod subscriberMethod = new  SubscriberMethod(subscriber, method, eventType, code, threadMode);
                    addSubscriberToMap(eventType, subscriberMethod);

                    addSubscriber(subscriberMethod);

                }
            }
        }
    }

    private void addEventTypeToMap(Object subscriber, Class eventType) {
        List<Class> eventTypes = eventTypesBySubscriber.get(subscriber);
        if (eventTypes == null) {
            eventTypes = new ArrayList<>();
            eventTypesBySubscriber.put(subscriber, eventTypes);
        }

        if (!eventTypes.contains(eventType)) {
            eventTypes.add(eventType);
        }
    }

    private void addSubscriberToMap(Class eventType, SubscriberMethod subscriberMethod) {
        List<SubscriberMethod> subscriberMethods = subscriberMethodByEventType.get(eventType);
        if (subscriberMethods == null) {
            subscriberMethods = new ArrayList<>();
            subscriberMethodByEventType.put(eventType, subscriberMethods);
        }

        if (!subscriberMethods.contains(subscriberMethod)) {
            subscriberMethods.add(subscriberMethod);
        }
    }

    private void addSubscriptionToMap(Class eventType, Disposable disposable) {
        List<Disposable> disposables = subscriptionsByEventType.get(eventType);
        if (disposables == null) {
            disposables = new ArrayList<>();
            subscriptionsByEventType.put(eventType, disposables);
        }

        if (!disposables.contains(disposable)) {
            disposables.add(disposable);
        }
    }

    @SuppressWarnings("unchecked")
    private void addSubscriber(final  SubscriberMethod subscriberMethod) {
        Flowable flowable;
        if (subscriberMethod.code == -1) {
            flowable = toObservable(subscriberMethod.eventType);
        } else {
            flowable = toObservable(subscriberMethod.code, subscriberMethod.eventType);
        }
        Disposable subscription = postToObservable(flowable, subscriberMethod)
                .subscribe(new Consumer<Object>() {
                    @Override
                    public void accept(Object o) throws Exception {
                        callEvent(subscriberMethod, o);
                    }
                });

        addSubscriptionToMap(subscriberMethod.subscriber.getClass(), subscription);
    }

    private Flowable postToObservable(Flowable observable,  SubscriberMethod subscriberMethod) {
        Scheduler scheduler;
        switch (subscriberMethod.threadMode) {
            case MAIN:
                scheduler = AndroidSchedulers.mainThread();
                break;

            case NEW_THREAD:
                scheduler = Schedulers.newThread();
                break;

            case CURRENT_THREAD:
                scheduler = Schedulers.trampoline();
                break;
            default:
                throw new IllegalStateException("Unknown thread mode: " + subscriberMethod.threadMode);
        }
        return observable.observeOn(scheduler);
    }

    private void callEvent( SubscriberMethod method, Object object) {
        Class eventClass = object.getClass();
        List< SubscriberMethod> methods = subscriberMethodByEventType.get(eventClass);
        if (methods != null && methods.size() > 0) {
            for ( SubscriberMethod subscriberMethod : methods) {
                 Subscribe sub = subscriberMethod.method.getAnnotation( Subscribe.class);
                int c = sub.code();
                if (c == method.code && method.subscriber.equals(subscriberMethod.subscriber) && method.method.equals(subscriberMethod.method)) {
                    subscriberMethod.invoke(object);
                }

            }
        }
    }

    public synchronized boolean isRegistered(Object subscriber) {
        return eventTypesBySubscriber.containsKey(subscriber);
    }

    public void unregister(Object subscriber) {
        List<Class> subscribedTypes = eventTypesBySubscriber.get(subscriber);
        if (subscribedTypes != null) {
            for (Class<?> eventType : subscribedTypes) {
                unSubscribeByEventType(subscriber.getClass());
                unSubscribeMethodByEventType(subscriber, eventType);
            }
            eventTypesBySubscriber.remove(subscriber);
        }
    }

    private void unSubscribeByEventType(Class eventType) {
        List<Disposable> disposables = subscriptionsByEventType.get(eventType);
        if (disposables != null) {
            Iterator<Disposable> iterator = disposables.iterator();
            while (iterator.hasNext()) {
                Disposable disposable = iterator.next();
                if (disposable != null && !disposable.isDisposed()) {
                    disposable.dispose();
                    iterator.remove();
                }
            }
        }
    }

    private void unSubscribeMethodByEventType(Object subscriber, Class eventType) {
        List< SubscriberMethod> subscriberMethods = subscriberMethodByEventType.get(eventType);
        if (subscriberMethods != null) {
            Iterator< SubscriberMethod> iterator = subscriberMethods.iterator();
            while (iterator.hasNext()) {
                 SubscriberMethod subscriberMethod = iterator.next();
                if (subscriberMethod.subscriber.equals(subscriber)) {
                    iterator.remove();
                }
            }
        }
    }

    public void send(int code, Object o) {
        bus.onNext(new Message(code, o));
    }

    public void post(Object o) {
        bus.onNext(o);
    }

    public void send(int code) {
        bus.onNext(new Message(code, new BusData()));
    }

    private class Message {
        private int code;
        private Object object;

        public Message() {
        }

        private Message(int code, Object o) {
            this.code = code;
            this.object = o;
        }

        private int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        private Object getObject() {
            return object;
        }

        public void setObject(Object object) {
            this.object = object;
        }
    }
}
