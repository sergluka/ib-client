package lv.sergluka.tws.impl;

import lv.sergluka.tws.TwsClient;
import lv.sergluka.tws.impl.future.TwsFuture;
import lv.sergluka.tws.impl.future.TwsListFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class TwsSender {

    private static final Logger log = LoggerFactory.getLogger(TwsSender.class);

    public enum Event {
        REQ_CONNECT,
        REQ_ID,
        REQ_CONTRACT_DETAIL,
        REQ_ORDER_PLACE,
    }

    // TODO: test
    static class EventKey {
        Event   event;
        Integer id;

        EventKey(Event event, Integer id) {
            this.event = event;
            this.id = id;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }

            EventKey eventKey = (EventKey) obj;
            return Objects.equals(id, eventKey.id) || event == eventKey.event;
        }

        @Override
        public int hashCode() {
            if (id != null) {
                return Objects.hash(id);

            }

            return Objects.hash(event);
        }

        @Override
        public String toString() {
            if (id == null && event == null) {
                return "Invalid message";
            }

            if (id != null && event == null) {
                return id.toString();
            }

            if (id == null && event != null) {
                return event.name();
            }

            return String.format("%s,%s", event.name(), id);
        }
    }

    private final ConcurrentHashMap<EventKey, TwsFuture> futures = new ConcurrentHashMap<>();
    private final TwsClient twsClient;

    public TwsSender(@NotNull TwsClient twsClient) {
        this.twsClient = twsClient;
    }

    public <T> TwsFuture<T> postSingleRequest(@NotNull Event event, int requestId, @NotNull Runnable runnable) {
        if (!twsClient.isConnected()) {
            throw new RuntimeException("Not connected");
        }

        final EventKey key = new EventKey(event, requestId);
        final TwsFuture future = new TwsFuture<T>(() -> futures.remove(key));
        post(key, future, runnable);
        return future;
    }

    public <T> TwsFuture<T> postListRequest(@NotNull Event event, int requestId, @NotNull Runnable runnable) {
        if (!twsClient.isConnected()) {
            throw new RuntimeException("Not connected");
        }

        final EventKey key = new EventKey(event, requestId);
        final TwsFuture future = new TwsListFuture<T>(() -> futures.remove(key));
        post(key, future, runnable);
        return future;
    }

    public <T> TwsFuture<T> postInternalRequest(@NotNull Event event, @NotNull Runnable runnable) {
        final EventKey key = new EventKey(event, null);
        final TwsFuture future = new TwsFuture<T>(() -> futures.remove(key));
        post(key, future, runnable);
        return future;
    }

    void confirmResponse(@NotNull Event event, @Nullable Integer id, @Nullable Object result) {
        confirm(event, id, result);
    }

    void confirmInnerResponse(@NotNull Event event) {
        confirm(event, null, null);
    }

    public void setError(int requestId, RuntimeException exception) {
        final EventKey key = new EventKey(null, requestId);
        final TwsFuture future = futures.get(key);
        if (future == null) {
            log.error("Cannot find future with ID {}", requestId);
            return;
        }

        future.setException(exception);
    }

    <E> void addElement(@NotNull Event event, int id, @NotNull E element) {
        final EventKey key = new EventKey(event, id);
        log.debug("=> {}:add", key);

        final TwsListFuture future = (TwsListFuture) futures.get(key);
        if (future == null) {
            log.error("Got event {} for unknown or expired request", key);
            return;
        }

        future.add(element);
    }

    private <T> void post(EventKey key, @NotNull TwsFuture future, @NotNull Runnable runnable) {
        futures.put(key, future);
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
        log.debug("=> {}: {}", key, result);

        final TwsFuture future = futures.remove(key);
        if (future == null) {
            log.error("Got event {} for unknown or expired request", key);
            return;
        }

        future.setDone(result);
    }
}
