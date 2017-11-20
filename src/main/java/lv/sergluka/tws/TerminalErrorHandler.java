package lv.sergluka.tws;

import lv.sergluka.tws.impl.sender.RequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class TerminalErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(TerminalErrorHandler.class);

    private enum ErrorType {
        INFO,
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
                severity = ErrorType.INFO;
                break;
            case 202: // Order canceled
                TwsClient.requests.removeRequest(RequestRepository.Event.REQ_ORDER_PLACE, id);
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
                TwsClient.requests.setError(id, new TwsExceptions.TerminalError(message, code));
                break;
            case INFO:
                log.debug("Server message - {}", message);
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
