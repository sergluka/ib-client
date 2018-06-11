package lv.sergluka.ib.impl.subscription;

import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.reactivex.ObservableEmitter;

public class SubscriptionImpl2<Param, RegResult> implements IbSubscription {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionImpl2.class);

    private final ObservableEmitter<Param> emitter;
    private final SubscriptionsRepository.Key key;
    private final Function<Integer, RegResult> registrationFn;
    private final Consumer<Integer> unregistrationFn;

    SubscriptionImpl2(ObservableEmitter<Param> emitter,
                      SubscriptionsRepository.Key key,
                      Function<Integer, RegResult> registrationFn,
                      Consumer<Integer> unregistrationFn) {
        this.emitter = emitter;
        this.key = key;
        this.registrationFn = registrationFn;
        this.unregistrationFn = unregistrationFn;
    }

    public ObservableEmitter<Param> getEmitter() {
        return emitter;
    }

    Integer getId() {
        return key.id;
    }

    RegResult subscribe() {
        return subscribe(key.id);
    }

    RegResult subscribe(int id) {
        if (registrationFn != null) {
            return registrationFn.apply(id);
        }
        return null;
    }

//    public void call(Param data) {
//        callbackFn.accept(data);
//    }

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

    @Override
    public String toString() {
        final StringBuffer buffer = new StringBuffer("{");
        buffer.append("id=").append(key.id);
        buffer.append(", type=").append(key.type);
        buffer.append('}');
        return buffer.toString();
    }

    public void onNext(Param data) {
        emitter.onNext(data);
    }
}
