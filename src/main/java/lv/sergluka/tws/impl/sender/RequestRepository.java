package lv.sergluka.tws.impl.sender;

import lv.sergluka.tws.TwsClient;
import lv.sergluka.tws.TwsExceptions;
import lv.sergluka.tws.impl.promise.TwsListPromise;
import lv.sergluka.tws.impl.promise.TwsPromiseImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unchecked")
public class RequestRepository {

    private static final Logger log = LoggerFactory.getLogger(RequestRepository.class);

    public enum Event {
        REQ_CONTRACT_DETAIL,
        REQ_CURRENT_TIME,
        REQ_ORDER_PLACE,
        REQ_ORDER_LIST,
        REQ_MARKET_DATA,
        REQ_POSITIONS,
        REQ_PORTFOLIO
    }

    private final ConcurrentHashMap<EventKey, CompletableFuture> promises = new ConcurrentHashMap<>();

    private final TwsClient client;

    public RequestRepository(@NotNull TwsClient client) {
        this.client = client;
    }

    public <T> TwsPromiseImpl<T> postSingleRequest(@NotNull Event event, Integer requestId,
                                               @NotNull Runnable runnable) {
        if (!client.isConnected()) {
            throw new TwsExceptions.NotConnected();
        }

        final EventKey key = new EventKey(event, requestId);
        final TwsPromiseImpl promise = new TwsPromiseImpl<T>(key);
        post(key, promise, runnable);
        return promise;
    }

    public <T> CompletableFuture<T> postListRequest(@NotNull Event event,
                                                    Integer requestId,
                                                    @NotNull Runnable runnable) {
        if (!client.isConnected()) {
            throw new TwsExceptions.NotConnected();
        }

        final EventKey key = new EventKey(event, requestId);
        final TwsPromiseImpl promise = new TwsListPromise<T>(key);
        post(key, promise, runnable);
        return promise;
    }

    public void confirmAndRemove(@NotNull Event event, @Nullable Integer id, @Nullable Object result) {
        confirm(event, id, result);
    }

    public void setError(int requestId, RuntimeException exception) {
        final EventKey key = new EventKey(null, requestId);
        final CompletableFuture promise = promises.get(key);
        if (promise == null) {
            log.warn("Cannot set error for unknown promise with ID {}", requestId);
            log.error("Received error is: {}", exception.getMessage(), exception);
            return;
        }

        promise.completeExceptionally(exception);
    }

    public <E> void addToList(@NotNull Event event, Integer id, @NotNull E element) {
        final EventKey key = new EventKey(event, id);
        log.debug("=> {}:add", key);

        final TwsListPromise promise = (TwsListPromise) promises.get(key);
        if (promise == null) {
            log.error("Got event {} for unknown or expired request", key);
            return;
        }

        promise.add(element);
    }

    private void post(EventKey key, @NotNull TwsPromiseImpl promise, @NotNull Runnable runnable) {

        // Make sure future removes itself from a repository
        CompletableFuture futureWithCleanup = promise.thenRun(() -> promises.remove(key));

        final CompletableFuture old = promises.put(key, futureWithCleanup);
        if (old != null) {
            log.error("Duplicated request: {}", key);
            throw new TwsExceptions.DuplicatedRequest(key);
        }

        try {
            log.debug("<= {}", key);
            runnable.run();
        } catch (Exception e) {
            promises.remove(key); // TODO: wrap with lock all procedure?
            throw e;
        }
    }

    private void confirm(@NotNull Event event, Integer id, Object result) {
        final EventKey key = new EventKey(event, id);
        log.debug("=> {}: {}", key, result != null ? result.toString().replaceAll("\n", "; ") : "null");

        final TwsPromiseImpl promise = (TwsPromiseImpl) promises.remove(key);
        if (promise == null) {
            log.error("Got event {} for unknown or expired request", key);
            return;
        }

        promise.complete(result);
    }

    public void confirmListAndRemove(@NotNull Event event, Integer id) {
        final EventKey key = new EventKey(event, id);
        log.debug("=> {}", key);

        final TwsListPromise promise = (TwsListPromise) promises.remove(key);
        if (promise == null) {
            log.error("Got event {} for unknown or expired request", key);
            return;
        }

        promise.complete();
    }
}
