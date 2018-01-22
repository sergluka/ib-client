package lv.sergluka.tws.impl.sender;

import lv.sergluka.tws.TwsClient;
import lv.sergluka.tws.TwsExceptions;
import lv.sergluka.tws.impl.promise.TwsListPromise;
import lv.sergluka.tws.impl.promise.TwsPromise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@SuppressWarnings("unchecked")
public class RequestRepository {

    private static final Logger log = LoggerFactory.getLogger(RequestRepository.class);

    public enum Event {
        REQ_CONTRACT_DETAIL,
        REQ_CURRENT_TIME,
        REQ_ORDER_PLACE,
        REQ_ORDER_LIST,
        REQ_MAKET_DATA,
        REQ_MAKET_DEPTH,
        REQ_POSITIONS,
    }

    private final ConcurrentHashMap<EventKey, TwsPromise> promises = new ConcurrentHashMap<>();

    private final TwsClient client;

    public RequestRepository(@NotNull TwsClient client) {
        this.client = client;
    }

    public <T> TwsPromise<T> postSingleRequest(@NotNull Event event, Integer requestId,
                                               @NotNull Runnable runnable, Consumer<T> consumer) {
        if (!client.isConnected()) {
            throw new TwsExceptions.NotConnected();
        }

        final EventKey key = new EventKey(event, requestId);
        final TwsPromise promise = new TwsPromise<>(key, consumer, () -> promises.remove(key));
        post(key, promise, runnable);
        return promise;
    }

    public <T> TwsPromise<T> postListRequest(@NotNull Event event, Integer requestId,
                                             @NotNull Runnable runnable, Consumer<List<T>> consumer) {
        if (!client.isConnected()) {
            throw new TwsExceptions.NotConnected();
        }

        final EventKey key = new EventKey(event, requestId);
        final TwsPromise promise = new TwsListPromise<>(key, consumer, () -> promises.remove(key));
        post(key, promise, runnable);
        return promise;
    }

    public <T> void postUncheckedRequest(@NotNull Event event, @NotNull Runnable runnable) {
        final EventKey key = new EventKey(event, null);
        final TwsPromise promise = new TwsPromise<T>(key, null, () -> promises.remove(key));
        post(key, promise, runnable);
    }

    public void confirmAndRemove(@NotNull Event event, @Nullable Integer id, @Nullable Object result) {
        confirm(event, id, result);
    }

    public void confirmAndRemoveWeak(@NotNull Event event, @Nullable Integer id, @Nullable Object result) {
        confirmWeak(event, id, result);
    }

    public void removeRequest(@NotNull Event event, @NotNull Integer id) {
        final EventKey key = new EventKey(event, id);
        if (promises.remove(key) == null) {
            log.error("Try to remove unknown promise {}", key);
        }
    }

    public void setError(int requestId, RuntimeException exception) {
        final EventKey key = new EventKey(null, requestId);
        final TwsPromise promise = promises.get(key);
        if (promise == null) {
            log.warn("Cannot set error for unknown promise with ID {}", requestId);
            log.error("Received error is: {}", exception.getMessage(), exception);
            return;
        }

        promise.setException(exception);
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

    private void post(EventKey key, @NotNull TwsPromise promise, @NotNull Runnable runnable) {
        final TwsPromise old = promises.put(key, promise);
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

        final TwsPromise promise = promises.remove(key);
        if (promise == null) {
            log.error("Got event {} for unknown or expired request", key);
            return;
        }

        promise.setDone(result);
    }

    private void confirmWeak(@NotNull Event event, Integer id, Object result) {
        final EventKey key = new EventKey(event, id);
        log.debug("=> {}: {}", key, result);

        final TwsPromise promise = promises.remove(key);
        if (promise == null) {
            return;
        }

        promise.setDone(result);
    }

    public <E> void modify(@NotNull Event event, Integer id, Class<E> clazz, Consumer<E> consumer) {
        final EventKey key = new EventKey(event, id);

        final TwsPromise promise = promises.get(key);
        if (promise == null) {
            log.error("Got event {} for unknown or expired request", key);
            return;
        }

        promise.modify(clazz, consumer);
    }
}
