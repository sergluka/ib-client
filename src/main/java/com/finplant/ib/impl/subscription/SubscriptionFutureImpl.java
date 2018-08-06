//package com.finplant.ib.impl.subscription;
//
//import org.jetbrains.annotations.NotNull;
//
//import java.util.Map;
//import java.util.concurrent.ExecutionException;
//import java.util.concurrent.Future;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.TimeoutException;
//import java.util.function.Consumer;
//import java.util.function.Function;
//
//public class SubscriptionFutureImpl<T> extends SubscriptionImpl<T, Future<T>> implements IbSubscriptionFuture<T> {
//
//    private Future<T> future;
//
//    SubscriptionFutureImpl(Map<SubscriptionsRepository.Key, SubscriptionImpl> subscriptions,
//                           SubscriptionsRepository.Key key,
//                           Consumer<T> callbackFn,
//                           Function<Integer, Future<T>> registrationFn,
//                           Consumer<Integer> unregistrationFn) {
//        super(subscriptions, key, callbackFn, registrationFn, unregistrationFn);
//    }
//
//    @Override
//    public T get() throws ExecutionException, InterruptedException {
//        if (future == null) {
//            throw new IllegalStateException(
//                    String.format("Cannot return future for not registered subscription: %s", this));
//        }
//
//        return future.get();
//    }
//
//    @Override
//    public T get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
//        if (future == null) {
//            throw new IllegalStateException(
//                    String.format("Cannot return future for not registered subscription: %s", this));
//        }
//
//        return future.get(timeout, unit);
//    }
//
//    @Override
//    Future<T> subscribe(Integer id) {
//        future = super.subscribe(id);
//        return future;
//    }
//
//    @Override
//    public boolean cancel(boolean mayInterruptIfRunning) {
//        return false;
//    }
//
//    @Override
//    public boolean isCancelled() {
//        return false;
//    }
//
//    @Override
//    public boolean isDone() {
//        return future.isDone();
//    }
//}
