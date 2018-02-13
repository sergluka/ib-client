package lv.sergluka.ib.impl.sender;

import lv.sergluka.ib.IbClient;
import lv.sergluka.ib.IbExceptions;
import lv.sergluka.ib.impl.future.IbListFuture;
import lv.sergluka.ib.impl.future.IbFutureImpl;
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

    private final ConcurrentHashMap<EventKey, CompletableFuture> futures = new ConcurrentHashMap<>();

    private final IbClient client;

    public RequestRepository(@NotNull IbClient client) {
        this.client = client;
    }

    public <T> CompletableFuture<T> postSingleRequest(@NotNull Event event, Integer requestId,
                                                 @NotNull Runnable runnable) {
        if (!client.isConnected()) {
            throw new IbExceptions.NotConnected();
        }

        final EventKey key = new EventKey(event, requestId);
        final IbFutureImpl future = new IbFutureImpl<T>(key);
        post(key, future, runnable);

        // Make sure future removes itself from a repository // TODO: Test removal form a repository
        return future.whenComplete((val, e) -> futures.remove(key));
    }

    public <T> CompletableFuture<T> postListRequest(@NotNull Event event,
                                                    Integer requestId,
                                                    @NotNull Runnable runnable) {
        if (!client.isConnected()) {
            throw new IbExceptions.NotConnected();
        }

        final EventKey key = new EventKey(event, requestId);
        final IbFutureImpl future = new IbListFuture<T>(key);
        post(key, future, runnable);

        // Make sure future removes itself from a repository
        return future.whenComplete((val, e) -> futures.remove(key));
    }

    public void confirmAndRemove(@NotNull Event event, @Nullable Integer id, @Nullable Object result) {
        confirm(event, id, result);
    }

    public void setError(int requestId, RuntimeException exception) {
        final EventKey key = new EventKey(null, requestId);
        final CompletableFuture future = futures.get(key);
        if (future == null) {
            log.warn("Cannot set error for unknown future with ID {}", requestId);
            log.error("Received error is: {}", exception.getMessage(), exception);
            return;
        }

        future.completeExceptionally(exception);
    }

    public <E> void addToList(@NotNull Event event, Integer id, @NotNull E element) {
        final EventKey key = new EventKey(event, id);
        log.debug("=> {}:add", key);

        final IbListFuture future = (IbListFuture) futures.get(key);
        if (future == null) {
            log.error("Got event {} for unknown or expired request", key);
            return;
        }

        future.add(element);
    }

    private void post(EventKey key, @NotNull IbFutureImpl future, @NotNull Runnable runnable) {

        final CompletableFuture old = futures.put(key, future);
        if (old != null) {
            log.error("Duplicated request: {}", key);
            throw new IbExceptions.DuplicatedRequest(key);
        }

        try {
            log.debug("<= {}", key);
            runnable.run();
        } catch (Exception e) {
            futures.remove(key); // TODO: wrap with lock all procedure?
            throw e;
        }
    }

    private void confirm(@NotNull Event event, Integer id, Object result) {
        final EventKey key = new EventKey(event, id);
        log.debug("=> {}: {}", key, result != null ? result.toString().replaceAll("\n", "; ") : "null");

        final IbFutureImpl future = (IbFutureImpl) futures.remove(key);
        if (future == null) {
            log.error("Got event {} for unknown or expired request", key);
            return;
        }

        future.complete(result);
    }

    public void confirmListAndRemove(@NotNull Event event, Integer id) {
        final EventKey key = new EventKey(event, id);
        log.debug("=> {}", key);

        final IbListFuture future = (IbListFuture) futures.remove(key);
        if (future == null) {
            log.error("Got event {} for unknown or expired request", key);
            return;
        }

        future.complete();
    }
}
