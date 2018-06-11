package lv.sergluka.ib.impl.subscription;

import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;

import io.reactivex.ObservableEmitter;

public class Subscription2<T> extends SubscriptionImpl2<T, Future<T>> {

    private Future<T> future;

    // TODO Remove subscription on unsubscribe

    Subscription2(ObservableEmitter<T> emitter,
                  SubscriptionsRepository.Key key,
                  Function<Integer, Future<T>> registrationFn,
                  Consumer<Integer> unregistrationFn) {
        super(emitter, key, registrationFn, unregistrationFn);
    }

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
}
