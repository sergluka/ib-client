package lv.sergluka.tws;

import com.ib.client.*;
import lv.sergluka.tws.impl.ConnectionMonitor;
import lv.sergluka.tws.impl.TwsBaseWrapper;
import lv.sergluka.tws.impl.TwsReader;
import lv.sergluka.tws.impl.sender.TwsSender;
import lv.sergluka.tws.impl.future.TwsFuture;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketException;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class TwsClient implements AutoCloseable {

    private enum ErrorType {
        INFO,
        ERROR,
        REQUEST, CRITICAL
    };

    private static final Logger log = LoggerFactory.getLogger(TwsClient.class);
    private static final int INVALID_ID = -1;
    private EClientSocket socket;
    private TwsReader reader;
    private TwsSender sender;
    private ConnectionMonitor connectionMonitor;

    private AtomicInteger requestId = new AtomicInteger(0);
    private AtomicInteger orderId;

    @Override
    public void close() throws Exception {
        connectionMonitor.disconnect();
        connectionMonitor.close();
    }

    public void connect(final @NotNull String ip, final int port, final int connId) {
        log.debug("Connecting...");

        if (isConnected()) {
            log.warn("Already is connected");
            return;
        }

        sender = new TwsSender(this);

        connectionMonitor = new ConnectionMonitor() {

            @Override
            protected void onConnect() {
                init();

                sender.postUncheckedRequest(TwsSender.Event.REQ_CONNECT,
                    () -> {
                        socket.setAsyncEConnect(false);
                        socket.eConnect(ip, port, connId);
                        socket.setServerLogLevel(5); // TODO
                    });
            }

            @Override
            protected void onDisconnect() {
                socket.eDisconnect();
                reader.close();
            }
        };
        connectionMonitor.start();
        connectionMonitor.connect();
        connectionMonitor.waitForStatus(ConnectionMonitor.Status.CONNECTED);
    }

    public void disconnect() throws TimeoutException {
        log.debug("Disconnecting...");
        connectionMonitor.disconnect();
        log.info("Disconnected");
    }

    public boolean isConnected() {
        return socket != null &&
               socket.isConnected() &&
               connectionMonitor.status() == ConnectionMonitor.Status.CONNECTED;
    }

    public int nextOrderId() {
        if (orderId.get() == INVALID_ID) {
            throw new IllegalStateException("Has no request ID from TWS");
        }

        int id = orderId.getAndSet(INVALID_ID);
        socket.reqIds(-1);
        return id;
    }

    @NotNull
    public TwsFuture<Integer> reqCurrentTime() {
        return sender.postSingleRequest(TwsSender.Event.REQ_CURRENT_TIME, null, () -> socket.reqCurrentTime());
    }

    @NotNull
    public TwsFuture<OrderState> placeOrder(@NotNull Contract contract, @NotNull Order order) {
//        final Integer id = nextRequestId();
        final Integer id = order.orderId();
        return sender.postSingleRequest(TwsSender.Event.REQ_ORDER_PLACE, id,
                () -> socket.placeOrder(id, contract, order));
    }

    @NotNull
    public TwsFuture<List<ContractDetails>> reqContractDetails(@NotNull Contract contract) {
        shouldBeConnected();

        final Integer id = nextOrderId();
        return sender.postListRequest(TwsSender.Event.REQ_CONTRACT_DETAIL, id,
                () -> socket.reqContractDetails(id, contract));
    }

    private void init() {
        orderId = new AtomicInteger(INVALID_ID);

        EJavaSignal signal = new EJavaSignal();

        final TwsBaseWrapper wrapper = new TwsWrapper(sender);
        socket = new EClientSocket(wrapper, signal);
        reader = new TwsReader(socket, signal);
    }

//    private int nextRequestId() {
//        return requestId.getAndIncrement();
//    }

    private void shouldBeConnected() {
        if (!isConnected()) {
            throw new TwsExceptions.NotConnected();
        }
    }

    private class TwsWrapper extends TwsBaseWrapper {

        TwsWrapper(TwsSender sender) {
            super(sender);
        }

        @Override
        public void connectAck() {
            log.info("Connection is opened. version: {}", socket.serverVersion());
            reader.start(); // TODO: maybe move it into Conn Manager thread

            connectionMonitor.confirmConnection();
            super.connectAck();
        }

        @Override
        public void connectionClosed() {
            log.error("TWS closes the connection");

            connectionMonitor.reconnect();
        }

        @Override
        public void error(final Exception e) {
            if (e instanceof SocketException) {

                final ConnectionMonitor.Status connectionStatus = connectionMonitor.status();

                if (connectionStatus == ConnectionMonitor.Status.DISCONNECTING ||
                    connectionStatus == ConnectionMonitor.Status.DISCONNECTED) {

                    log.debug("Socket has been closed at shutdown");
                    return;
                }

                log.warn("Connection lost", e);
                connectionMonitor.reconnect();
            }

            log.error("TWS error", e);
        }

        @Override
        public void error(final int id, final int code, final String message) {
            new TerminalErrorHandler(id, code, message).invoke();
        }

        @Override
        public void nextValidId(int id) {
            log.debug("New request ID: {}", id);
            orderId.set(id);
        }

        private class TerminalErrorHandler {
            private final int id;
            private final int code;
            private final String message;

            public TerminalErrorHandler(final int id, final int code, final String message) {
                this.id = id;
                this.code = code;
                this.message = message;
            }

            public void invoke() {
                ErrorType severity;
                switch (code) {
                    case 2104:
                    case 2106:
                        severity = ErrorType.INFO;
                        break;
                    case 202: // Order canceled
                        sender.removeRequest(TwsSender.Event.REQ_ORDER_PLACE, id);
                        severity = ErrorType.INFO;
                        break;

                    case 320: // Server error when reading an API client request.
                    case 321: // Server error when validating an API client request.
                        severity = ErrorType.REQUEST;
                        break;

                    case 503: // The TWS is out of date and must be upgraded
                        severity = ErrorType.CRITICAL;
                        break;

                    default:
                        log.error("Try to reconnect on error");
                        severity = ErrorType.ERROR;
                        break;
                }

                switch (severity) {
                    case REQUEST:
                        sender.setError(id, new TwsExceptions.TerminalError(message, code));
                        break;
                    case INFO:
                        log.debug("Server message - {}", message);
                        break;
                    case ERROR:
                        log.error("Terminal error: code={}, msg={}. Reconnecting.", code, message);
                        connectionMonitor.reconnect();
                        break;
                    case CRITICAL:
                        log.error("Terminal critical error: code={}, msg={}. Disconnecting.", code, message);
                        connectionMonitor.disconnect();
                        break;
                }
            }
        }
    }
}
