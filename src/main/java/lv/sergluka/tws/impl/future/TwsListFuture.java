package lv.sergluka.tws.impl.future;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;

public class TwsListFuture<T> extends TwsFuture<List<T>> {

    public TwsListFuture(@NotNull final Runnable onTimeout) {
        super(onTimeout);
        value = new LinkedList<>();
    }

    public void add(T element) {
        value.add(element);
    }
}
