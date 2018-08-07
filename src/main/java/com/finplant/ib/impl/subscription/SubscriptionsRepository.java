package com.finplant.ib.impl.subscription;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import com.finplant.ib.IdGenerator;

/**
 * Stores all subscription
 * <p>
 * Holding subscription in one place not only for simple access, but also to restorer them after disconnects,
 * when terminal lost them
 */
@SuppressWarnings("unchecked")
public class SubscriptionsRepository implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionsRepository.class);

    private final IdGenerator idGenerator;
    private final Map<Key, SubscriptionImpl> subscriptions = new ConcurrentHashMap<>();

    public SubscriptionsRepository(IdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    @Override
    public void close() {
        subscriptions.clear();
        log.debug("SubscriptionsRepository is closed");
    }

    @NotNull
    public <Param, RetResult> Observable<RetResult> addSubscription(
          @Nullable EventType type,
          @Nullable Function<Integer, Observable<Param>> subscribe,
          @Nullable Consumer<Integer> unsubscribe) {

        int id = idGenerator.nextRequestId();
        return addSubscriptionImpl(type, id, subscribe, unsubscribe);
    }

    @NotNull
    public <Param, RetResult> Observable<RetResult> addSubscriptionUnique(
          @Nullable EventType type,
          @Nullable Function<Integer, Observable<Param>> subscribe,
          @Nullable Consumer<Integer> unsubscribe) {

        return addSubscriptionImpl(type, null, subscribe, unsubscribe);
    }

    public <Param> void onNext(EventType type, Param data, Boolean requireSubscription) {
        onNext(type, null, data, requireSubscription);
    }

    public void onError(EventType type, Integer reqId, Throwable throwable) {
        SubscriptionImpl<?, ?> subscription = subscriptions.get(new Key(type, reqId));
        subscription.onError(throwable);
    }

    public <Param> void onNext(EventType type, Integer reqId, Param data, Boolean requireSubscription) {
        SubscriptionImpl<Param, ?> subscription = subscriptions.get(new Key(type, reqId));
        if (subscription == null) {
            if (requireSubscription) {
                log.error("Got event '{}' with unknown request id {}", type, reqId);
            }
            return;
        }

        subscription.onNext(data);

//        // TODO: Add queue explicitly and monitor its size
//        // TODO: Log posting, execution and finalization of a task
//        Runnable runnable = () -> {
//            Thread thread = Thread.currentThread();
//            thread.setName(String.format("subscription-%s", subscription.toString()));
//            subscription.onNext(data);
//        };
//
//        try {
//            executors.submit(runnable);
//        } catch (Exception e) {
//            log.error("Fail to submit task for execution: {}, id: {}, param: {}", type, reqId, data);
//            throw e;
//        }
    }

    // TODO: Make resubscribe optional
    public void resubscribe() {
        if (!subscriptions.isEmpty()) {
            log.debug("Restoring {} subscription", subscriptions.size());
        }

        subscriptions.values().forEach(subscription -> {
            log.debug("Subscription {} is restoring", subscription);

            subscription.subscribe(subscription.getId());

            log.info("Subscription {} has been restored", subscription);
        });
    }

    private void remove(Key key) {
        subscriptions.remove(key);
    }

    @NotNull
    private <Param, RetResult> Observable<RetResult> addSubscriptionImpl(
          @Nullable EventType type,
          @Nullable Integer id,
          @Nullable Function<Integer, Param> subscribe,
          @Nullable Consumer<Integer> unsubscribe) {

        Observable<RetResult> observable = Observable.create(emitter -> {
            Key key = new Key(type, id);
            SubscriptionImpl subscription = new SubscriptionImpl<>(emitter, key, subscribe, unsubscribe);

            emitter.setCancellable(() -> {
                subscription.unsubscribe();
                remove(key);
            });

            subscription.subscribe();
            subscriptions.put(key, subscription);

            log.info("Subscribed to {}", subscription);
        });

        return observable.observeOn(Schedulers.io());
    }

    public enum EventType {
        EVENT_CONTRACT_PNL,
        EVENT_ACCOUNT_PNL,
        EVENT_POSITION,
        EVENT_POSITION_MULTI,
        EVENT_ORDER_STATUS,
        EVENT_MARKET_DATA,
        EVENT_PORTFOLIO,
        EVENT_CONNECTION_STATUS
    }

    class Key {
        final EventType type;
        final Integer id;

        Key(EventType type, Integer id) {
            this.type = type;
            this.id = id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, id);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }

            Key key = (Key) obj;
            return Objects.equals(type, key.type) && Objects.equals(id, key.id);
        }
    }
}
