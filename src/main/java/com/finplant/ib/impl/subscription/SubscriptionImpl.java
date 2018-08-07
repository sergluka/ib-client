package com.finplant.ib.impl.subscription;

import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.reactivex.ObservableEmitter;

public class SubscriptionImpl<Param, RegResult> {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionImpl.class);

    private final ObservableEmitter<Param> emitter;
    private final SubscriptionsRepository.Key key;
    private final Function<Integer, RegResult> registrationFn;
    private final Consumer<Integer> unregistrationFn;

    SubscriptionImpl(ObservableEmitter<Param> emitter,
                     SubscriptionsRepository.Key key,
                     Function<Integer, RegResult> registrationFn,
                     Consumer<Integer> unregistrationFn) {
        this.emitter = emitter;
        this.key = key;
        this.registrationFn = registrationFn;
        this.unregistrationFn = unregistrationFn;
    }

    Integer getId() {
        return key.id;
    }

    RegResult subscribe() {
        return subscribe(key.id);
    }


    RegResult subscribe(Integer id) {
        if (registrationFn != null) {
            return registrationFn.apply(id);
        }
        return null;
    }

    public void unsubscribe() {
        try {
            if (unregistrationFn != null) {
                unregistrationFn.accept(key.id);
            }
            log.info("Has been unsubscribed from {}", this);
        } catch (Exception e) {
            log.error("Error unsubscribe for subscription: {}. {}", this, e.getMessage());
        }
    }

    public void onNext(Param data) {
        emitter.onNext(data);
    }

    public void onError(Throwable throwable) {
        emitter.onError(throwable);
    }

    @Override
    public String toString() {
        final StringBuffer buffer = new StringBuffer("{");
        buffer.append("id=").append(key.id);
        buffer.append(", type=").append(key.type);
        buffer.append('}');
        return buffer.toString();
    }
}
