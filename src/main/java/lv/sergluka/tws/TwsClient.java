package lv.sergluka.tws;

import com.ib.client.*;
import lv.sergluka.tws.connection.ConnectionMonitor;
import lv.sergluka.tws.connection.TwsFuture;
import lv.sergluka.tws.connection.TwsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.*;

public class TwsClient extends TwsClientWrapper implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TwsClient.class);

//    private final ConcurrentHashMap<Event, TwsFuture> futures = new ConcurrentHashMap<>();

    private final EJavaSignal signal = new EJavaSignal();
    private final EClientSocket socket = new EClientSocket(this, signal);

    private TwsReader reader = new TwsReader(socket, signal);
    private final TwsSender sender = new TwsSender(this);

    private final ConnectionMonitor connectionMonitor = new ConnectionMonitor(socket, reader);

    enum Status {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        CONNECTION_LOST,
        DISCONNECTING,
    }

    private Status status = Status.DISCONNECTED;

    @Override
    public void close() throws IOException {
        connectionMonitor.disconnect();
    }

    public void connect(final String ip, final int port, final int connId) {
        log.debug("Connecting...");
        status = Status.CONNECTING;

        TwsFuture fConnect = sender.post(TwsSender.Event.REQ_CONNECT, () -> connectionMonitor.connect(ip, port, connId));
        fConnect.get();

//        reader = new TwsReader(socket, signal);
//        reader.start();
    }

    public void disconnect() throws TimeoutException {
        log.debug("Disconnecting...");
        status = Status.DISCONNECTING;
        connectionMonitor.disconnect();
        status = Status.DISCONNECTED;

//        TwsFuture fDisconnect = sender.post(TwsSender.Event.REQ_DISCONNECT, connectionMonitor::disconnect);
//        fDisconnect.get(10, TimeUnit.SECONDS);
    }

    public boolean isConnected() {
        return socket.isConnected() && status == Status.CONNECTED;
    }

//    private <T> connection.TwsFuture<T> postIfConnected(Event event, Runnable runnable) {
//        if (!isConnected()) {
//            throw new RuntimeException("No connection");
//        }
//
//        return post(event, runnable);
//    }
//
//    private <T> connection.TwsFuture<T> post(Event event, Runnable runnable) {
//        final connection.TwsFuture future = new connection.TwsFuture<T>(() -> futures.remove(event));
//        futures.put(event, future);
//        runnable.run();
//        return future;
//    }

    public TwsFuture<Integer> reqId() {
        return sender.postIfConnected(TwsSender.Event.REQ_ID, () -> socket.reqIds(-1));
    }

    public Integer reqIdsSync() throws TimeoutException {
        try {
            return reqId().get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new TimeoutException("Timeout of 'reqId' call");
        }
    }

    public TwsFuture<Object> placeOrder(Contract contract, Order order) throws TimeoutException {
        Integer id = reqIdsSync();
        return sender.postIfConnected(TwsSender.Event.REQ_ORDER_PLACE, () -> socket.placeOrder(id, contract, order));
    }

    public void nextValidId(final int orderId) {
        sender.confirmWeak(TwsSender.Event.REQ_ID, orderId);
    }

    public void openOrder(final int orderId, final Contract contract, final Order order, final OrderState orderState) {
        sender.confirmStrict(TwsSender.Event.REQ_ORDER_PLACE, orderId); // TODO: return struct
    }

    @Override
    public void connectAck() {
        log.info("Connection opened. version: {}", socket.serverVersion());
        status = Status.CONNECTED;

        reader.start();

        sender.confirmStrict(TwsSender.Event.REQ_CONNECT);
    }

    @Override
    public void connectionClosed() {
        log.error("TWS closes the connection");
        status = Status.CONNECTION_LOST;
        connectionMonitor.reconnect();
    }

    @Override
    public void error(final Exception e) { // TODO, awaike all futures with exception
        if (e instanceof SocketException) {
            if (status == Status.DISCONNECTING) {
                log.debug("Socket has been closed at shutdown");
                return;
            }

            status = Status.CONNECTION_LOST;
            log.warn("Connection lost");
//            connectionMonitor.reconnect();
        }

        log.error("TWS error", e);
    }

    @Override
    public void error(final String str) {
        log.error("TWS error: ", str);
    }

    @Override
    public void error(final int id, final int errorCode, final String errorMsg) {
        if (id == -1 && (errorCode == 2104 || errorCode == 2106)) {
            log.debug("Connection is OK: {}", errorMsg);
            return;
        }

        log.error("Terminal returns an error: id={}, code={}, msg={}", id, errorCode, errorMsg);

        switch (errorCode) {
            case 503: // The TWS is out of date and must be upgraded
                break;
            case 2104: // OK
            case 2106: // OK
                log.debug("Connection is OK: {}", errorMsg);
                break;
            default:
                connectionMonitor.reconnect();
        }
    }
}
