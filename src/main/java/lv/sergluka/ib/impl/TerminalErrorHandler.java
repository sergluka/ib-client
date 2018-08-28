package lv.sergluka.ib.impl;

import lv.sergluka.ib.IbExceptions;
import lv.sergluka.ib.impl.sender.RequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class TerminalErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(TerminalErrorHandler.class);

    private final RequestRepository requests;

    private enum ErrorType {
        INFO,
        WARN,
        ERROR,
        REQUEST,
        CRITICAL
    }

    public TerminalErrorHandler(RequestRepository requests) {
        this.requests = requests;
    }

    abstract void onError();
    abstract void onFatalError();

    public void handle(final int id, final int code, final String message) {
        ErrorType severity;
        switch (code) {
            case 202: // Order canceled
            case 2100: // API client has been unsubscribed from account data..
            case 2104: // Market data farm connection is OK
            case 2106: // A historical data farm is connected.
            case 2107: // A historical data farm connection has become inactive but should be available upon demand.
            case 2108: // A market data farm connection has become inactive but should be available upon demand.
                severity = ErrorType.INFO;
                break;
            case 201: // Order rejected
            case 399: // Order message error
            case 2105: // A historical data farm is disconnected.
            case 2109: // Order Event Warning: Attribute "Outside Regular Trading Hours" is ignored based on the order type and destination. PlaceOrder is now processed.
            case 10147: // OrderId ... that needs to be cancelled is not found
            case 10148: // OrderId ... that needs to be cancelled can not be cancelled
            case 10185: // Failed to cancel PNL (not subscribed)
            case 10186: // Failed to cancel PNL single (not subscribed)
                severity = ErrorType.WARN;
                break;

            case 503: // The TWS is out of date and must be upgraded
                severity = ErrorType.CRITICAL;
                break;

            default:
                if (id >= 0) {
                    severity = ErrorType.REQUEST;
                } else {
                    severity = ErrorType.ERROR;
                }
                break;
        }

        switch (severity) {
            case REQUEST:
                requests.setError(id, new IbExceptions.TerminalError(message, code));
                break;
            case INFO:
                log.info("TWS message: [#{}] {}", code, message);
                break;
            case WARN:
                log.warn("TWS message - [#{}] {}", code, message);
                break;
            case ERROR:
                log.error("TWS error - [#{}] {}", code, message);
                onError();
                break;
            case CRITICAL:
                log.error("TWS critical error - [#{}] {}. Disconnecting.", code, message);
                onFatalError();
                break;
        }
    }
}
