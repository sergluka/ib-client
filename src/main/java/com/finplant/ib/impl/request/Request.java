package com.finplant.ib.impl.request;

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.reactivex.ObservableEmitter;

public class Request<T> {

    private static final Logger log = LoggerFactory.getLogger(Request.class);

    private final ObservableEmitter<T> emitter;
    private final RequestKey key;
    private final Consumer<Integer> registrationFn;
    private final Consumer<Integer> unregistrationFn;
    private Object userData;

    Request(ObservableEmitter<T> emitter,
            RequestKey key,
            Consumer<Integer> registrationFn,
            Consumer<Integer> unregistrationFn,
            Object userData) {

        this.emitter = emitter;
        this.key = key;
        this.registrationFn = registrationFn;
        this.unregistrationFn = unregistrationFn;
        this.userData = userData;
    }

    @Override
    public String toString() {
        final StringBuffer buffer = new StringBuffer("{");
        if (key.getId() != null) {
            buffer.append("id=").append(key.getId()).append(", ");
        }
        buffer.append("type=").append(key.getType());
        buffer.append('}');
        return buffer.toString();
    }

    void register() {
        register(key.getId());
    }

    void register(Integer id) {
        if (registrationFn != null) {
            registrationFn.accept(id);
        }
    }

    public void unregister() {
        try {
            if (unregistrationFn != null) {
                unregistrationFn.accept(key.getId());
                log.info("Has been unregistered from {}", this);
            }
        } catch (Exception e) {
            log.error("Error unregister request {}: {}", this, e.getMessage(), e);
        }
    }

    public void onNext(T data) {
        emitter.onNext(data);
    }

    public void onComplete() {
        emitter.onComplete();
    }

    public void onError(Throwable throwable) {
        emitter.onError(throwable);
    }

    public Object getUserData() {
        return userData;
    }
}
