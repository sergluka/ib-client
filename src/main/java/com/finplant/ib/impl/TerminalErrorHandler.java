package com.finplant.ib.impl;

import com.finplant.ib.types.IbLogRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.finplant.ib.IbExceptions;
import com.finplant.ib.impl.request.RequestRepository;

public abstract class TerminalErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(TerminalErrorHandler.class);

    private final RequestRepository requests;

    private enum ErrorType {
        CUSTOM,
        DEBUG,
        INFO,
        WARN,
        ERROR,
        REQUEST_ERROR,
        CRITICAL
    }

    public TerminalErrorHandler(RequestRepository requests) {
        this.requests = requests;
    }

    abstract void onLog(IbLogRecord record);
    abstract void onError();
    abstract void onFatalError();

    @SuppressWarnings("MagicNumber")
    public void handle(int id, int code, String message) {

        log.trace("Message from IB client: id={}, code={}, message={}", id, code, message);

        IbExceptions.IbClientError exception = new IbExceptions.TerminalError(id, message, code);

        ErrorType type;
        switch (code) {

            case 202: // Order canceled
            case 2100: // API client has been unsubscribed from account data..
            case 2104: // Market data farm connection is OK
            case 2106: // A historical data farm is connected.
            case 2107: // A historical data farm connection has become inactive but should be available upon demand.
            case 2108: // A market data farm connection has become inactive but should be available upon demand.
                type = ErrorType.INFO;
                break;

            case 161: // Cancel attempted when order is not in a cancellable state
            case 201: // Order rejected
            case 399: // Order message error
            case 2103: // Market data farm connection is broken
            case 2105: // A historical data farm is disconnected.
            case 2109: // Order Event Warning: Attribute "Outside Regular Trading Hours" is ignored based on the
                // order type and destination. PlaceOrder is now processed.
            case 10185: // Failed to cancel PNL (not subscribed)
            case 10186: // Failed to cancel PNL single (not subscribed)
                type = ErrorType.WARN;
                break;

            case 503: // The TWS is out of date and must be upgraded
                type = ErrorType.CRITICAL;
                break;

            case 10147: // OrderId ... that needs to be cancelled is not found
                requests.onError(RequestRepository.Type.REQ_ORDER_CANCEL, id, exception);
                type = ErrorType.CUSTOM;
                break;

            case 10148: // OrderId ... that needs to be cancelled can not be cancelled
                requests.onNextAndComplete(RequestRepository.Type.REQ_ORDER_CANCEL, id, true, true);
                type = ErrorType.WARN;
                break;

            case 101:
                exception = new IbExceptions.MaxNumberOfTickersError(id, message);
                type = ErrorType.REQUEST_ERROR;
                break;

            /* Ugly TWS API sends all events related with historical data with the same
             * code (162). The only way to separate error and non-error cases is to parse text message*/
            case 162:
                onHistoricalDataError(code, id, message);
                type = ErrorType.DEBUG;
                break;

            case 200:
                exception = new IbExceptions.NoSecurityDefinitionError(id, message);
                type = ErrorType.REQUEST_ERROR;
                break;

            case 10182: // Failed to request live updates (disconnected)
                exception = new IbExceptions.SubscriptionLostError(id, message);
                type = ErrorType.REQUEST_ERROR;
                break;

            default:
                if (id >= 0) {
                    type = ErrorType.REQUEST_ERROR;
                } else {
                    type = ErrorType.ERROR;
                }
                break;
        }

        IbLogRecord.Severity severity;
        switch (type) {
            case CUSTOM:
            case ERROR:
            case REQUEST_ERROR:
            case CRITICAL:
                severity = IbLogRecord.Severity.ERROR;
                break;
            case WARN:
                severity = IbLogRecord.Severity.WARN;
                break;
            case INFO:
                severity = IbLogRecord.Severity.INFO;
                break;
            case DEBUG:
                severity = IbLogRecord.Severity.DEBUG;
                break;
            default:
                throw new IllegalStateException("Unknown message type: " + type);
        }
        onLog(new IbLogRecord(severity, id, code, message));

        switch (type) {
            case CUSTOM:
                break;
            case REQUEST_ERROR:
                requests.onError(id, exception, false);
                break;
            case DEBUG:
                log.debug("TWS message: [#{}] {}", code, message);
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

    private void onHistoricalDataError(int code, int id, String message) {

        String HISTORICAL_DATA_MSG = "Historical Market Data Service error message";

        String HISTORICAL_DATA_CANCEL_MSG = "API historical data query cancelled";
        String HISTORICAL_DATA_NO_PERMISSIONS_MSG = "No market data permissions for";
        String HISTORICAL_DATA_NO_DATA_MSG = "HMDS query returned no data";

        if (!message.startsWith(HISTORICAL_DATA_MSG)) {
            log.error("Unexpected message for REQ_HISTORICAL_DATA: {}", message);
            requests.onError(null, id, new IbExceptions.TerminalError(id, message, code));
            return;
        }

        String messageInfo = message.substring(HISTORICAL_DATA_MSG.length() + 1);
        //noinspection StatementWithEmptyBody Subscription canceling is not an error
        if (messageInfo.startsWith(HISTORICAL_DATA_CANCEL_MSG)) {
        } else if (messageInfo.startsWith(HISTORICAL_DATA_NO_PERMISSIONS_MSG)) {
            requests.onError(null, id, new IbExceptions.NoPermissions(id, messageInfo), true);
        } else if (messageInfo.startsWith(HISTORICAL_DATA_NO_DATA_MSG)) {
            requests.onError(null, id, new IbExceptions.NoDataError(id, messageInfo), true);
        } else {
            requests.onError(null, id, new IbExceptions.IbClientError(id, message), true);
        }
    }
}
