import com.ib.client.*;
//import connection.ConnectionMonitor;
import connection.IConnectable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketException;
import java.util.HashMap;
import java.util.concurrent.*;

public class TwsClient extends TwsClientWrapper implements Closeable, IConnectable.Events {

    private static final Logger log = LoggerFactory.getLogger(TwsClient.class);

    private final ConcurrentHashMap<Event, TwsFuture> futures = new ConcurrentHashMap<>();

    private final EJavaSignal signal = new EJavaSignal();
    private final EClientSocket socket = new EClientSocket(this, signal);

    private final TwsReader reader = new TwsReader(socket, signal);


//    @Override
//    public void onConnect() {
//        reader.start();
//    }
//
//    @Override
//    public void onDisconnect() {
//        reader.start();
//    }

    @Override
    public void close() throws IOException {
        disconnect();
    }

    enum Status {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        CONNECTION_LOST,
        DISCONNECTING,
    }

    enum Event {
        REQ_CONNECT,
        REQ_ID,
        REQ_ORDER_PLACE,
    }

    private Status status = Status.DISCONNECTED;

    public void connect(final String ip, final int port, final int connId) {
        log.debug("Connecting...");
        status = Status.CONNECTING;



        socket.setAsyncEConnect(false);
        socket.eConnect(ip, port, connId);
        socket.setServerLogLevel(5); // TODO
    }

    public void disconnect() {
        reader.close();
        socket.eDisconnect();
    }

    public boolean isConnected() {
        return socket.isConnected();
    }

    private <T> TwsFuture<T> post(Event event, Runnable runnable) {
        if (!isConnected()) {
            throw new RuntimeException("No connection");
        }

        final TwsFuture future = new TwsFuture<T>(() -> {
            futures.remove(event);
        });
        futures.put(event, future);
        runnable.run();
        return future;
    }

    public TwsFuture<Integer> reqId() {
        return post(Event.REQ_ID, () -> socket.reqIds(-1));
    }

    public Integer reqIdsSync() throws TimeoutException {
        try {
            return reqId().get(1, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new TimeoutException("Timeout of 'reqId' call");
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

    @Override
    public void error(final Exception e) {
        if (e instanceof SocketException) {
            if (status == Status.DISCONNECTING) {
                log.debug("Socket has been closed at shutdown");
                return;
            } else {
                log.debug(">>: {}", status);
                status = Status.CONNECTION_LOST;
                disconnect();
                connect();
            }
        }
    }

    @Override
    public void error(final String str) {
        super.error(str);
    }

    @Override
    public void error(final int id, final int errorCode, final String errorMsg) {
        if (id == -1 && (errorCode == 2104 || errorCode == 2106)) {
            log.debug("Connection is OK: {}", errorMsg);
            return;
        }

        log.error("Terminal returns an error: id={}, code={}, msg={}", id, errorCode, errorMsg);
        if (id == -1) {
            disconnect();
            connect();
        }
    }
}
