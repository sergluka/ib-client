package lv.sergluka.tws.impl.promise;

import java.util.concurrent.TimeUnit;

public interface TwsPromise<T> {
    T get();
    T get(long timeout, TimeUnit unit);
}
