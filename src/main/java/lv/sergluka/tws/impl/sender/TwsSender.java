package lv.sergluka.tws.impl.sender;

import lv.sergluka.tws.TwsClient;
import lv.sergluka.tws.TwsExceptions;
import lv.sergluka.tws.impl.future.TwsFuture;
import lv.sergluka.tws.impl.future.TwsListFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unchecked")
public class TwsSender {

    private static final Logger log = LoggerFactory.getLogger(TwsSender.class);

    public enum Event {
        REQ_CONNECT,
        REQ_CONTRACT_DETAIL,
        REQ_CURRENT_TIME,
        REQ_ORDER_PLACE,
    }

    private final ConcurrentHashMap<EventKey, TwsFuture> requests = new ConcurrentHashMap<>();
    private final TwsClient twsClient;

    public TwsSender(@NotNull TwsClient twsClient) {
        this.twsClient = twsClient;
    }

    public <T> TwsFuture<T> postSingleRequest(@NotNull Event event, Integer requestId, @NotNull Runnable runnable) {
        if (!twsClient.isConnected()) {
            throw new TwsExceptions.NotConnected();
        }

        final EventKey key = new EventKey(event, requestId);
        final TwsFuture future = new TwsFuture<T>(() -> requests.remove(key));
        post(key, future, runnable);
        return future;
    }

    public <T> TwsFuture<T> postListRequest(@NotNull Event event, int requestId, @NotNull Runnable runnable) {
        if (!twsClient.isConnected()) {
            throw new TwsExceptions.NotConnected();
        }

        final EventKey key = new EventKey(event, requestId);
        final TwsFuture future = new TwsListFuture<T>(() -> requests.remove(key));
        post(key, future, runnable);
        return future;
    }

    public <T> void postUncheckedRequest(@NotNull Event event, @NotNull Runnable runnable) {
        final EventKey key = new EventKey(event, null);
        final TwsFuture future = new TwsFuture<T>(() -> requests.remove(key));
        post(key, future, runnable);
    }

    public void confirmResponse(@NotNull Event event, @Nullable Integer id, @Nullable Object result) {
        confirm(event, id, result);
    }

    public void removeRequest(@NotNull Event event, @NotNull Integer id) {
        final EventKey key = new EventKey(event, id);
        if (requests.remove(key) == null) {
            log.error("Try to remove unknown future {}", key);
        }
    }

    public void setError(int requestId, RuntimeException exception) {
        final EventKey key = new EventKey(null, requestId);
        final TwsFuture future = requests.get(key);
        if (future == null) {
            log.error("Cannot set error for unknown future with ID {}", requestId);
            return;
        }

        future.setException(exception);
    }

    public <E> void addElement(@NotNull Event event, int id, @NotNull E element) {
        final EventKey key = new EventKey(event, id);
        log.debug("=> {}:add", key);

        final TwsListFuture future = (TwsListFuture) requests.get(key);
        if (future == null) {
            log.error("Got event {} for unknown or expired request", key);
            return;
        }

        future.add(element);
    }

    private void post(EventKey key, @NotNull TwsFuture future, @NotNull Runnable runnable) {
        requests.put(key, future);
        try {
            log.debug("<= {}", key);
            runnable.run();
        } catch (Exception e) {
            requests.remove(key); // TODO: wrap with lock all procedure?
            throw e;
        }
    }

    private void confirm(@NotNull Event event, Integer id, Object result) {
        final EventKey key = new EventKey(event, id);
        log.debug("=> {}: {}", key, result);

        final TwsFuture future = requests.remove(key);
        if (future == null) {
            log.error("Got event {} for unknown or expired request", key);
            return;
        }

        future.setDone(result);
    }
}
