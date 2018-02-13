package lv.sergluka.tws.impl.promise;

import lv.sergluka.tws.impl.sender.EventKey;

import java.util.LinkedList;
import java.util.List;

public class TwsListPromise<T> extends TwsPromiseImpl<List<T>> {

    private List<T> value = new LinkedList<>();

    public TwsListPromise(EventKey event) {
        super(event);
    }

    public void add(T element) {
        value.add(element);
    }

    public boolean complete() {
        return super.complete(value);
    }
}
