package lv.sergluka.tws;

import lv.sergluka.tws.impl.sender.RequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class TerminalErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(TerminalErrorHandler.class);

    private enum ErrorType {
        INFO,
        WARN,
        ERROR,
        REQUEST,
        CRITICAL
    }

    abstract void onError();
    abstract void onFatalError();

    public void handle(final int id, final int code, final String message) {
        ErrorType severity;
        switch (code) {
            case 2104:
            case 2106:
            case 2108:
            case 202: // Order canceled
                severity = ErrorType.INFO;
                break;
            case 201: // Order rejected
            case 399: // Order message error
            case 2109: // Order Event Warning: Attribute "Outside Regular Trading Hours" is ignored based on the order type and destination. PlaceOrder is now processed.
            case 10147: // OrderId ... that needs to be cancelled is not found
            case 10148: // OrderId ... that needs to be cancelled can not be cancelled
                severity = ErrorType.WARN;
                break;

//            case 110: // The price does not conform to the minimum price variation for this contract
//            case 200: // The contract description specified for <Symbol> is ambiguous.
//            case 320: // Server error when reading an API client request.
//            case 321: // Server error when validating an API client request.
//            case 354: // Requested market data is not subscribed
//            case 10168: // Requested market data is not subscribed. Delayed market data is not enabled
//                severity = ErrorType.REQUEST;
//                break;

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
                TwsClient.requests.setError(id, new TwsExceptions.TerminalError(message, code));
                break;
            case INFO:
                log.debug("Server message - {}", message);
                break;
            case WARN:
                log.warn("Server message - {}", message);
                // TODO: should be threat as error, to notify a caller?
//                TwsClient.requests.removeRequest(RequestRepository.Event.REQ_ORDER_PLACE, id);
                break;
            case ERROR:
                log.error("Terminal error: code={}, msg={}.", code, message);
                onError();
                break;
            case CRITICAL:
                log.error("Terminal critical error: code={}, msg={}. Disconnecting.", code, message);
                onFatalError();
                break;
        }
    }
}
