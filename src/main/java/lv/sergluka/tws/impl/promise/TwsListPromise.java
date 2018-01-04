package lv.sergluka.tws.impl.promise;

import lv.sergluka.tws.impl.sender.EventKey;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

public class TwsListPromise<T> extends TwsPromise<List<T>> {

    public TwsListPromise(EventKey event, Consumer<List<T>> consumer, @NotNull final Runnable onTimeout) {
        super(event, consumer, onTimeout);
        value = new LinkedList<>();
    }

    public void add(T element) {
        value.add(element);
    }
}
