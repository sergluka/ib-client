package lv.sergluka.tws.impl.future;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;

public class TwsListFuture<T> extends TwsFuture<List<T>> {

    private final List<T> data = new LinkedList<>();

    TwsListFuture(@NotNull final Runnable onTimeout) {
        super(onTimeout);
    }

    public void add(T element) {
        data.add(element);
    }
}
