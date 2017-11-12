package lv.sergluka.tws.impl.promise;

import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

public class TwsListPromise<T> extends TwsPromise<List<T>> {

    public TwsListPromise(Consumer<List<T>> consumer, @NotNull final Runnable onTimeout) {
        super(consumer, onTimeout);
        value = new LinkedList<>();
    }

    public void add(T element) {
        value.add(element);
    }
}
