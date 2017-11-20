package lv.sergluka.tws;

import com.ib.client.Contract;
import lv.sergluka.tws.impl.ConnectionMonitor;
import lv.sergluka.tws.impl.Wrapper;
import lv.sergluka.tws.impl.sender.RequestRepository;
import lv.sergluka.tws.impl.types.TwsOrderStatus;
import lv.sergluka.tws.impl.types.TwsPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketException;

class TwsWrapper extends Wrapper {

    private enum ErrorType {
        INFO,
        ERROR,
        REQUEST, CRITICAL
    }

    private static final Logger log = LoggerFactory.getLogger(TwsWrapper.class);

    private TwsClient twsClient;

    void setClient(TwsClient twsClient) {
        this.twsClient = twsClient;
    }

    @Override
    public void connectAck() {
        try {
            log.info("Connection is opened. version: {}", twsClient.getSocket().serverVersion());
            twsClient.getReader().start(); // TODO: maybe move it into Conn Manager thread

            twsClient.getConnectionMonitor().confirmConnection();
            super.connectAck();
        } catch (Exception e) {
            log.error("Exception at `connectAck`", e);
        }
    }

    @Override
    public void connectionClosed() {
        log.error("TWS closes the connection");

        twsClient.getConnectionMonitor().reconnect();
    }

    @Override
    public void orderStatus(int orderId, String status, double filled, double remaining, double avgFillPrice,
                            int permId, int parentId, double lastFillPrice, int clientId, String whyHeld,
                            double mktCapPrice) {

        final TwsOrderStatus twsStatus = new TwsOrderStatus(orderId, status, filled, remaining, avgFillPrice,
                permId, parentId, lastFillPrice, clientId, whyHeld, mktCapPrice);

        log.info("New order status: {}", twsStatus);
        if (twsClient.getOrdersRepository().addNewStatus(orderId, twsStatus)) {
            if (twsClient.getOnOrderStatus() != null) {
                twsClient.getOnOrderStatus().accept(orderId, twsStatus);
            }
        }
    }

    @Override
    public void position(String account, Contract contract, double pos, double avgCost) {
        log.info("Position change: {}/{}/{}", account, contract.localSymbol(), pos);

        if (twsClient.getOnPosition() != null) {
            TwsPosition position = new TwsPosition(account, contract, pos, avgCost);
            twsClient.getOnPosition().accept(position);
        }
    }

    @Override
    public void positionEnd() {
        twsClient.getOnPosition().accept(null);
    }

    @Override
    public void error(final Exception e) {
        if (e instanceof SocketException) {

            final ConnectionMonitor.Status connectionStatus = twsClient.getConnectionMonitor().status();

            if (connectionStatus == ConnectionMonitor.Status.DISCONNECTING ||
                connectionStatus == ConnectionMonitor.Status.DISCONNECTED) {

                log.debug("Socket has been closed at shutdown");
                return;
            }

            log.warn("Connection lost", e);
            twsClient.getConnectionMonitor().reconnect();
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
        twsClient.getOrderId().set(id);
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
                    twsClient.getRequests().removeRequest(RequestRepository.Event.REQ_ORDER_PLACE, id);
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
                    severity = ErrorType.ERROR;
                    break;
            }

            switch (severity) {
                case REQUEST:
                    twsClient.getRequests().setError(id, new TwsExceptions.TerminalError(message, code));
                    break;
                case INFO:
                    log.debug("Server message - {}", message);
                    break;
                case ERROR:
                    log.error("Terminal error: code={}, msg={}. Reconnecting.", code, message);
                    twsClient.getConnectionMonitor().reconnect();
                    break;
                case CRITICAL:
                    log.error("Terminal critical error: code={}, msg={}. Disconnecting.", code, message);
                    twsClient.getConnectionMonitor().disconnect();
                    break;
            }
        }
    }
}
