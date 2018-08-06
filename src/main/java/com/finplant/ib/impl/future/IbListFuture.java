package com.finplant.ib.impl.future;

import com.finplant.ib.impl.sender.EventKey;

import java.util.LinkedList;
import java.util.List;

public class IbListFuture<T> extends IbFutureImpl<List<T>> {

    private List<T> value = new LinkedList<>();

    public IbListFuture(EventKey event) {
        super(event);
    }

    public void add(T element) {
        value.add(element);
    }

    public boolean complete() {
        return super.complete(value);
    }
}
