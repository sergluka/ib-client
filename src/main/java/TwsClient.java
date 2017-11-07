import com.ib.client.*;
import connection.ConnectionMonitor;
import connection.IConnectable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.concurrent.*;

public class TwsClient implements IConnectable.Events {

    private static final Logger log = LoggerFactory.getLogger(TwsClient.class);

    private final HashMap<Event, TwsFuture> futures = new HashMap<>();

    private final TwsClientWrapper wrapper = new TwsClientWrapper(this);

    private final EJavaSignal m_signal = new EJavaSignal();
    private final EClientSocket socket = new EClientSocket(wrapper, m_signal);

    private final TwsReader reader = new TwsReader(socket);

//    private ConnectionMonitor connectionMonitor = new ConnectionMonitor(wrapper);

    @Override
    public void onConnect() {
        reader.start();
    }

    @Override
    public void onDisconnect() {
        reader.start();
    }

    enum Event {
        REQ_ID,
        REQ_ORDER_PLACE,
    }


    public TwsClient() {
//        wrapper.init()
    }

    public void connect(final String ip, final int port, final int connId) {
        log.debug("Connecting...");
        status = Status.CONNECTING;

        socket.setAsyncEConnect(false);
        socket.eConnect(ip, port, connId);
        socket.setServerLogLevel(5); // TODO
    }


    private <T> TwsFuture<T> post(Event event, Runnable runnable) {
        if (!connectionMonitor.isConnected()) {
            throw new RuntimeException("No connection");
        }

        final TwsFuture future = new TwsFuture<>();
        futures.put(event, future);
        runnable.run();
        return future;
    }

    public TwsFuture<Integer> reqIds() {
        return post(Event.REQ_ID, () -> socket.reqIds(-1));
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
        return post(Event.REQ_ORDER_PLACE, () -> socket.placeOrder(id, contract, order));
    }

    public void nextValidId(final int orderId) {
        final TwsFuture<Integer> future = futures.get(Event.REQ_ID);
        if (future != null) {
            future.setDone(orderId);
        }
    }

    public void openOrder(final int orderId, final Contract contract, final Order order, final OrderState orderState) {
        futures.get(Event.REQ_ID).setDone();
    }
}
