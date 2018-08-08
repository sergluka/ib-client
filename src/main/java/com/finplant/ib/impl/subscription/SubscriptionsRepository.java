package com.finplant.ib.impl.subscription;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;

import com.finplant.ib.IbClient;
import com.finplant.ib.IbExceptions;
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

    @NotNull private final IbClient client;
    private final IdGenerator idGenerator;
    private final Map<SubscriptionKey, Subscription> subscriptions = new ConcurrentHashMap<>();

    public SubscriptionsRepository(@NotNull IbClient client, IdGenerator idGenerator) {
        this.client = client;
        this.idGenerator = idGenerator;
    }

    @Override
    public void close() {
        subscriptions.clear();
        log.debug("SubscriptionsRepository is closed");
    }

    @NotNull
    public <RetResult> Observable<RetResult> addSubscriptionWithId(
          @Nullable Type type,
          @Nullable Consumer<Integer> subscribe,
          @Nullable Consumer<Integer> unsubscribe) {

        int id = idGenerator.nextRequestId();
        return addSubscription(type, id, subscribe, unsubscribe);
    }

    @NotNull
    public <RetResult> Observable<RetResult> addSubscriptionWithoutId(
          @Nullable Type type,
          @Nullable Consumer<Integer> subscribe,
          @Nullable Consumer<Integer> unsubscribe) {

        return addSubscription(type, null, subscribe, unsubscribe);
    }

    public <Param> void onNext(Type type, Integer reqId, Param data, Boolean requireSubscription) {
        get(type, reqId, requireSubscription).ifPresent(subscription -> subscription.onNext(data));
    }

    public void onError(Type type, Integer reqId, Throwable throwable) {
        get(type, reqId, true).ifPresent(subscription -> subscription.onError(throwable));
    }

    public void onError(Integer reqId, Throwable throwable) {
        get(null, reqId, true).ifPresent(subscription -> subscription.onError(throwable));
    }

    public <Param> Optional<Subscription<Param, ?>> get(Type type, Integer reqId, Boolean requireSubscription) {
        Subscription<Param, ?> subscription = subscriptions.get(new SubscriptionKey(type, reqId));
        if (subscription == null) {
            if (requireSubscription) {
                log.error("Got event '{}' with unknown request id {}", type, reqId);
            }
        }
        return Optional.ofNullable(subscription);
    }

    public void onComplete(Type type, Integer reqId, Boolean requireSubscription) {
        get(type, reqId, requireSubscription).ifPresent(Subscription::onComplete);
    }

    // TODO: requireSubscription: Boolean to enum
    public <Param> void onNextAndComplete(Type type, Integer reqId, Param data, Boolean requireSubscription) {
        get(type, reqId, requireSubscription).ifPresent(subscription -> {
            subscription.onNext(data);
            subscription.onComplete();
        });
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

    private void remove(SubscriptionKey key) {
        subscriptions.remove(key);
    }

    @NotNull
    public synchronized <RetResult> Observable<RetResult> addSubscription(
          @Nullable Type type,
          @Nullable Integer id,
          @Nullable Consumer<Integer> subscribe,
          @Nullable Consumer<Integer> unsubscribe) {

        Observable<RetResult> observable = Observable.create(emitter -> {

            if (!client.isConnected()) {
                throw new IbExceptions.NotConnected();
            }

            SubscriptionKey key = new SubscriptionKey(type, id);
            Subscription subscription = new Subscription<>(emitter, key, subscribe, unsubscribe);

            Subscription old = subscriptions.putIfAbsent(key, subscription);
            if (old != null) {
                log.error("Duplicated request: {}", key);
                throw new IbExceptions.DuplicatedRequest(key);
            }

            emitter.setCancellable(() -> {
                if (client.isConnected()) {
                    subscription.unsubscribe();
                } else {
                    log.warn("Have no connection at unsubscribe of {}", key);
                }
                remove(key);
            });

            subscription.subscribe();
            log.info("Subscribed to {}", subscription);
        });

        return observable.observeOn(Schedulers.io());
    }

    public enum Type {
        EVENT_CONTRACT_PNL,
        EVENT_ACCOUNT_PNL,
        EVENT_POSITION,
        EVENT_POSITION_MULTI,
        EVENT_ORDER_STATUS,
        EVENT_MARKET_DATA,
        EVENT_PORTFOLIO,
        EVENT_CONNECTION_STATUS,
        REQ_MARKET_DATA,
        REQ_CURRENT_TIME,
        REQ_ORDER_PLACE,
        REQ_ORDER_LIST,
        REQ_CONTRACT_DETAIL
    }
}
