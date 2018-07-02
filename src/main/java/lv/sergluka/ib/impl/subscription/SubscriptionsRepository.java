package lv.sergluka.ib.impl.subscription;

import io.reactivex.Observable;
import lv.sergluka.ib.IdGenerator;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Stores all subscription
 * <p>
 * Holding subscription in one place not only for simple access, but also to restorer them after disconnects,
 * when terminal lost them
 */
public class SubscriptionsRepository implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionsRepository.class);
    private final IdGenerator idGenerator;
    private final ExecutorService executors;
    private Map<Key, SubscriptionImpl2> subscriptions = new ConcurrentHashMap<>();
    public SubscriptionsRepository(ExecutorService executors, IdGenerator idGenerator) {
        this.executors = executors;
        this.idGenerator = idGenerator;
    }

    void remove(Key key) {
        subscriptions.remove(key);
    }

    @Override
    public void close() {
//        subscriptions.values().forEach(SubscriptionImpl::unsubscribe);
        subscriptions.clear();

        executors.shutdownNow().forEach(runnable -> log.warn("Thread still works at shutdown: {}", runnable));

        boolean done = false;
        try {
            done = executors.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.warn("Thread has been interrupted");
        }
        if (!done) {
            log.warn("Still have threads running after shutdown");
        }
        log.debug("SubscriptionsRepository is closed");
    }

//    public <Param, RegResult> IbSubscription add(EventType type,
//                                                 Consumer<Param> callback,
//                                                 Function<Integer, RegResult> registration,
//                                                 Consumer<Integer> unregistration) {
//
//        int id = idGenerator.nextRequestId();
//        return add(type, id, callback, registration, unregistration);
//    }
//
//    @NotNull
//    public <Param, RegResult> IbSubscription addUnique(@Nullable EventType type,
//                                                       @NotNull Consumer<Param> callback,
//                                                       @Nullable Function<Integer, RegResult> registration,
//                                                       @Nullable Consumer<Integer> unregistration) {
//
//        return add(type, null, callback, registration, unregistration);
//    }

    @NotNull
    private <Param, RetResult> Observable<RetResult> addSubscriptionImpl(
          @Nullable EventType type,
          @Nullable Integer id,
          @Nullable Function<Integer, Param> subscribe,
          @Nullable Consumer<Integer> unsubscribe) {

        return Observable.create(emitter -> {
            Key key = new Key(type, id);
            SubscriptionImpl2 subscription = new SubscriptionImpl2<>(emitter, key, subscribe, unsubscribe);

            emitter.setCancellable(() -> {
                subscription.unsubscribe();
                remove(key);
            });

            subscription.subscribe();
            subscriptions.put(key, subscription);

            log.info("Subscribed to {}", subscription);
        });
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

//    private <Param, RegResult> IbSubscription add(EventType type,
//                                                  Integer id,
//                                                  Consumer<Param> callback,
//                                                  Function<Integer, RegResult> registration,
//                                                  Consumer<Integer> unregistration) {
//
//        Key key = new Key(type, id);
//        SubscriptionImpl subscription =
//              new SubscriptionImpl<>(subscriptions, key, callback, registration, unregistration);
//
//        addSubscription(key, subscription);
//        return subscription;
//    }

    public <Param> void onNext(EventType type, Param data, Boolean requireSubscription) {
        onNext(type, null, data, requireSubscription);
    }

    public <Param> void onNext(EventType type, Integer reqId, Param data, Boolean requireSubscription) {
        SubscriptionImpl2<Param, ?> subscription = subscriptions.get(new Key(type, reqId));
        if (subscription == null) {
            if (requireSubscription) {
                log.error("Got event '{}' with unknown request id {}", type, reqId);
            }
            return;
        }

        // TODO: Add queue explicitly and monitor its size
        // TODO: Log posting, execution and finalization of a task
        Runnable runnable = () -> {
            Thread thread = Thread.currentThread();
            thread.setName(String.format("subscription-%s", subscription.toString()));
            subscription.onNext(data);
        };

        try {
            executors.submit(runnable);
        } catch (Exception e) {
            log.error("Fail to submit task for execution: {}, id: {}, param: {}", type, reqId, data);
            throw e;
        }
    }

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

    private void addSubscription(Key key, SubscriptionImpl2 subscription) {
        subscriptions.put(key, subscription);
//        subscription.subscribe(key.id);
        log.info("Subscribed to {}", subscription);
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
