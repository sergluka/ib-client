package lv.sergluka.tws.impl.subscription;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

public class SubscriptionPromiseImpl<T> extends SubscriptionImpl<T, Future<T>> implements TwsSubscriptionPromise<T> {

    private Future<T> promise;

    SubscriptionPromiseImpl(Map<SubscriptionsRepository.Key, SubscriptionImpl> subscriptions,
                            SubscriptionsRepository.Key key,
                            Consumer<T> callbackFn,
                            Function<Integer, Future<T>> registrationFn,
                            Consumer<Integer> unregistrationFn) {
        super(subscriptions, key, callbackFn, registrationFn, unregistrationFn);
    }

    @Override
    public T get() throws ExecutionException, InterruptedException {
        if (promise == null) {
            throw new IllegalStateException(
                    String.format("Cannot return promise for not registered subscription: %s", this));
        }

        return promise.get();
    }

    @Override
    public T get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (promise == null) {
            throw new IllegalStateException(
                    String.format("Cannot return promise for not registered subscription: %s", this));
        }

        return promise.get(timeout, unit);
    }

    @Override
    Future<T> subscribe(Integer id) {
        promise = super.subscribe(id);
        return promise;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return promise.isDone();
    }
}
