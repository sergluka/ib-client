package lv.sergluka.tws.impl.future;

import com.ib.client.OrderStatus;
import lv.sergluka.tws.impl.promise.TwsPromise;
import lv.sergluka.tws.impl.types.TwsOrderStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.function.Consumer;

public class TwsOrderPromise extends TwsPromise<TwsOrderStatus> {

//    private final ConcurrentHashMap<OrderStatus, Condition> conditions = new ConcurrentHashMap<>();

    public TwsOrderPromise(Consumer<TwsOrderStatus> consumer) {
        super(consumer, null);
    }

//    public TwsOrderStatus get(OrderStatus status, long timeout, TimeUnit unit) {
//        final TwsOrderStatus twsStatus = get(timeout, unit);
//        done.set(false);
//    }
}
