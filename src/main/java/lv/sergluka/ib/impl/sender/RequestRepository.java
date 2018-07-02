package lv.sergluka.ib.impl.sender;

import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import lv.sergluka.ib.IbClient;
import lv.sergluka.ib.IbExceptions;
import lv.sergluka.ib.impl.future.IbListFuture;

@SuppressWarnings("unchecked")
public class RequestRepository {

    private static final Logger log = LoggerFactory.getLogger(RequestRepository.class);
    private final ConcurrentHashMap<EventKey, ObservableEmitter> futures = new ConcurrentHashMap<>();
    private final IbClient client;

    public RequestRepository(@NotNull IbClient client) {
        this.client = client;
    }

    public <T> Observable<T> postRequest(@NotNull Event event, Integer requestId, @NotNull Runnable runnable) {

        return Observable.create(emitter -> {
            if (!client.isConnected()) {
                throw new IbExceptions.NotConnected();
            }

            final EventKey key = new EventKey(event, requestId);
            emitter.setCancellable(() -> futures.remove(key));

            final ObservableEmitter old = futures.put(key, emitter);
            if (old != null) {
                log.error("Duplicated request: {}", key);
                throw new IbExceptions.DuplicatedRequest(key);
            }

            log.debug("<= {}", key);
            runnable.run();
        });
//
//        // Make sure future removes itself from a repository // TODO: Test removal form a repository
//        return future.whenComplete((val, e) -> futures.remove(key));
    }

//    public <T> CompletableFuture<T> postListRequest(@NotNull Event event,
//                                                    Integer requestId,
//                                                    @NotNull Runnable runnable) {
//        if (!client.isConnected()) {
//            throw new IbExceptions.NotConnected();
//        }
//
//        final EventKey key = new EventKey(event, requestId);
//        final IbFutureImpl future = new IbListFuture<T>(key);
//        post(key, future, runnable);
//
//        // Make sure future removes itself from a repository
//        return future.whenComplete((val, e) -> futures.remove(key));
//    }

    public void setError(int requestId, RuntimeException exception) {
        final EventKey key = new EventKey(null, requestId);
        final ObservableEmitter<?> future = futures.get(key);
        if (future == null) {
            log.warn("Cannot set error for unknown future with ID {}", requestId);
            log.error("Received error is: {}", exception.getMessage(), exception);
            return;
        }

        future.onError(exception);
    }

//    public <E> void addToList(@NotNull Event event, Integer id, @NotNull E element) {
//        final EventKey key = new EventKey(event, id);
//        log.debug("=> {}:add", key);
//
//        final IbListFuture future = (IbListFuture) futures.get(key);
//        if (future == null) {
//            log.error("Got event {} for unknown or expired request", key);
//            return;
//        }
//
//        future.add(element);
//    }

//    private <T> void post(EventKey key, @NotNull SingleEmitter<T> emitter, @NotNull Runnable runnable) {
//
//        final ObservableEmitter old = futures.put(key, emitter);
//        if (old != null) {
//            log.error("Duplicated request: {}", key);
//            throw new IbExceptions.DuplicatedRequest(key);
//        }
//
//        try {
//            log.debug("<= {}", key);
//            runnable.run();
//        } catch (Exception e) {
//            futures.remove(key);
//            throw e;
//        }
//    }

    public void onNext(@NotNull Event event, Integer id, Object result) {
        final EventKey key = new EventKey(event, id);
        log.debug("=> {}: {}", key, result != null ? result.toString().replaceAll("\n", "; ") : "null");

        final ObservableEmitter future = futures.get(key);
        if (future == null) {
            log.error("Got event {} for unknown or expired request", key);
            return;
        }

        future.onNext(result);
    }

    public void onNextAndConfirm(@NotNull Event event, Integer id, Object result) {
        final EventKey key = new EventKey(event, id);
        log.debug("=> {}: {}", key, result != null ? result.toString().replaceAll("\n", "; ") : "null");

        final ObservableEmitter future = futures.remove(key);
        if (future == null) {
            log.error("Got event {} for unknown or expired request", key);
            return;
        }

        future.onNext(result);
        future.onComplete();
    }

    public void onComplete(@NotNull Event event, Integer id) {
        final EventKey key = new EventKey(event, id);
        log.debug("=> {}", key);

        final ObservableEmitter future = futures.remove(key);
        if (future == null) {
            log.error("Got event {} for unknown or expired request", key);
            return;
        }

        future.onComplete();
    }

    public enum Event {
        REQ_CONTRACT_DETAIL,
        REQ_CURRENT_TIME,
        REQ_ORDER_PLACE,
        REQ_ORDER_LIST,
        REQ_MARKET_DATA,
        REQ_POSITIONS,
//        REQ_POSITIONS_MULTI,
        REQ_PORTFOLIO
    }
}
