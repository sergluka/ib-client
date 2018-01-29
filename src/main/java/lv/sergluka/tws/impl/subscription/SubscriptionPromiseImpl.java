package lv.sergluka.tws.impl.subscription;

import lv.sergluka.tws.impl.promise.TwsPromise;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

public class SubscriptionPromiseImpl<T> extends SubscriptionImpl<T, TwsPromise<T>> implements TwsSubscriptionPromise<T> {

    private TwsPromise<T> promise;

    SubscriptionPromiseImpl(Map<SubscriptionsRepository.Key, SubscriptionImpl> subscriptions,
                            SubscriptionsRepository.Key key,
                            Consumer<T> callbackFn,
                            Function<Integer, TwsPromise<T>> registrationFn,
                            Consumer<Integer> unregistrationFn) {
        super(subscriptions, key, callbackFn, registrationFn, unregistrationFn);
    }

    @Override
    public T get() {
        if (promise == null) {
            throw new IllegalStateException(
                    String.format("Cannot return promise for not registered subscription: %s", this));
        }

        return promise.get();
    }

    @Override
    public T get(long timeout, TimeUnit unit) {
        if (promise == null) {
            throw new IllegalStateException(
                    String.format("Cannot return promise for not registered subscription: %s", this));
        }

        return promise.get(timeout, unit);
    }

    @Override
    TwsPromise<T> subscribe(Integer id) {
        promise = super.subscribe(id);
        return promise;
    }
}
