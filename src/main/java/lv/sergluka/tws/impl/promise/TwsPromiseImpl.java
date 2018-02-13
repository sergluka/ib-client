package lv.sergluka.tws.impl.promise;

import lv.sergluka.tws.impl.sender.EventKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class TwsPromiseImpl<T> extends CompletableFuture<T> {

    private static final Logger log = LoggerFactory.getLogger(TwsPromiseImpl.class);

    private final EventKey event;

    public TwsPromiseImpl(EventKey event) {
        this.event = event;
    }

    @Override
    public String toString() {
        return String.format("Future for {%s}", event);
    }
}
