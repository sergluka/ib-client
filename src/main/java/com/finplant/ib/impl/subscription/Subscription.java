package com.finplant.ib.impl.subscription;

import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.reactivex.ObservableEmitter;

public class Subscription<Param, RegResult> {

    private static final Logger log = LoggerFactory.getLogger(Subscription.class);

    private final ObservableEmitter<Param> emitter;
    private final SubscriptionKey key;
    private final Consumer<Integer> registrationFn;
    private final Consumer<Integer> unregistrationFn;

    Subscription(ObservableEmitter<Param> emitter,
                 SubscriptionKey key,
                 Consumer<Integer> registrationFn,
                 Consumer<Integer> unregistrationFn) {
        this.emitter = emitter;
        this.key = key;
        this.registrationFn = registrationFn;
        this.unregistrationFn = unregistrationFn;
    }

    Integer getId() {
        return key.getId();
    }

    RegResult subscribe() {
        return subscribe(key.getId());
    }

    RegResult subscribe(Integer id) {
        if (registrationFn != null) {
            registrationFn.accept(id);
        }
        return null;
    }

    public void unsubscribe() {
        try {
            if (unregistrationFn != null) {
                unregistrationFn.accept(key.getId());
                log.info("Has been unsubscribed from {}", this);
            }
        } catch (Exception e) {
            log.error("Error unsubscribe for subscription: {}. {}", this, e.getMessage());
        }
    }

    public void onNext(Param data) {
        emitter.onNext(data);
    }

    public void onComplete() {
        emitter.onComplete();
    }

    public void onError(Throwable throwable) {
        emitter.onError(throwable);
    }

    @Override
    public String toString() {
        final StringBuffer buffer = new StringBuffer("{");
        buffer.append("id=").append(key.getId());
        buffer.append(", type=").append(key.getType());
        buffer.append('}');
        return buffer.toString();
    }
}
