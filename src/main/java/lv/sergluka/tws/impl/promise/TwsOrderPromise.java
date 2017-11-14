package lv.sergluka.tws.impl.promise;

import com.ib.client.OrderStatus;
import lv.sergluka.tws.impl.promise.TwsPromise;
import lv.sergluka.tws.impl.types.TwsOrderStatus;

import java.util.function.Consumer;

public class TwsOrderPromise extends TwsPromise<OrderStatus> {

//    private final ConcurrentHashMap<OrderStatus, Condition> conditions = new ConcurrentHashMap<>();

    public TwsOrderPromise(Consumer<OrderStatus> consumer) {
        super(consumer, null);
    }

//    public TwsOrderStatus get(OrderStatus status, long timeout, TimeUnit unit) {
//        final TwsOrderStatus twsStatus = get(timeout, unit);
//        done.set(false);
//    }
}
