package com.finplant.ib.impl.future;

import com.finplant.ib.impl.sender.EventKey;

import java.util.concurrent.CompletableFuture;

public class IbFutureImpl<T> extends CompletableFuture<T> {

    private final EventKey event;

    public IbFutureImpl(EventKey event) {
        this.event = event;
    }

    @Override
    public String toString() {
        return String.format("Future for {%s}", event);
    }
}
