package lv.sergluka.tws.connection;

import lv.sergluka.tws.TwsClient;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

public class TwsSender {

    private static final Logger log = LoggerFactory.getLogger(TwsSender.class);

    public enum Event {
        REQ_CONNECT,
        REQ_ID,
        REQ_ORDER_PLACE,
    }

    private final ConcurrentHashMap<Event, TwsFuture> futures = new ConcurrentHashMap<>();
    private final TwsClient twsClient;

    public TwsSender(@NotNull TwsClient twsClient) {
        this.twsClient = twsClient;
    }

    public <T> TwsFuture<T> postIfConnected(@NotNull Event event, @NotNull Runnable runnable) {
        if (!twsClient.isConnected()) {
            throw new RuntimeException("No connection");
        }

        return post(event, runnable);
    }

    public <T> TwsFuture<T> post(@NotNull Event event, @NotNull Runnable runnable) {
        final TwsFuture future = new TwsFuture<T>(() -> futures.remove(event));
        futures.put(event, future);
        try {
            log.debug("<= {}", event.name());
            runnable.run();
        } catch (Exception e) {
            futures.remove(event); // TODO: wrap with lock all procedure?
            throw e;
        }
        return future;
    }

    public boolean confirmWeak(@NotNull Event event, @NotNull Object result) {
        return confirm(event, result);
    }

    public void confirmStrict(@NotNull Event event, Object result) {
        if (!confirm(event, result)) {
            log.error(String.format("TWS sends unexpected event: %s", event.name())); // TODO: check does needed?
            throw new IllegalStateException(String.format("TWS sends unexpected event: %s", event.name()));
        }
    }

    public void confirmStrict(@NotNull Event event) {
        confirmStrict(event, null);
    }

    private boolean confirm(@NotNull Event event, Object result) {
        final TwsFuture future = futures.remove(event);
        if (future != null) {
            log.debug("=> {}: {}", event.name(), result);
            future.setDone(result);
            return true;
        }

        return false;
    }
}
