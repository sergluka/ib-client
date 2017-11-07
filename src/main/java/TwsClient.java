import com.ib.client.Contract;
import com.ib.client.Order;
import com.ib.client.OrderState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.concurrent.*;

public class TwsClient extends TwsClientWrapper {

    private static final Logger log = LoggerFactory.getLogger(TwsClient.class);

    enum Event {
        REQ_ID,
        REQ_ORDER_PLACE,
    }

    private final HashMap<Event, TwsFuture> futures = new HashMap<>();

    private <T> TwsFuture<T> post(Event event, Runnable runnable) {
        if (!client.isConnected()) {
            throw new RuntimeException("No connection");
        }

        final TwsFuture future = new TwsFuture<>();
        futures.put(event, future);
        runnable.run();
        return future;
    }

    public TwsFuture<Integer> reqIds() {
        return post(Event.REQ_ID, () -> client.reqIds(-1));
    }

    public Integer reqIdsSync() throws TimeoutException {
        try {
            return reqIds().get(1, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new TimeoutException("Timeout of 'reqIds' call");
        }
    }

    public TwsFuture<Object> placeOrder(Contract contract, Order order) throws TimeoutException {
        Integer id = reqIdsSync();
        return post(Event.REQ_ORDER_PLACE, () -> client.placeOrder(id, contract, order));
    }

    @Override
    public void connectionClosed() {
        log.info("Connection closed");
    }

    @Override
    public void connectAck() {
        log.info("Connection open");
    }

    @Override
    public void nextValidId(final int orderId) {
        final TwsFuture<Integer> future = futures.get(Event.REQ_ID);
        if (future != null) {
            future.setDone(orderId);
        }

        super.nextValidId(orderId);
    }

    @Override
    public void openOrder(final int orderId, final Contract contract, final Order order, final OrderState orderState) {
        super.openOrder(orderId, contract, order, orderState);
        futures.get(Event.REQ_ID).setDone();
    }

    @Override
    public void orderStatus(final int orderId, final String status, final double filled, final double remaining, final double avgFillPrice, final int permId, final int parentId, final double lastFillPrice, final int clientId, final String whyHeld) {
        super.orderStatus(orderId, status, filled, remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld);
    }
}
