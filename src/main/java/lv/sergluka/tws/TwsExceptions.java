package lv.sergluka.tws;

import lv.sergluka.tws.impl.sender.EventKey;

public class TwsExceptions {

    public static class TerminalError extends RuntimeException {

        private final String errorMsg;
        private final int errorCode;

        public TerminalError(String errorMsg, int errorCode) {
            super(errorMsg);
            this.errorMsg = errorMsg;
            this.errorCode = errorCode;
        }

        public String getErrorMsg() {
            return errorMsg;
        }

        public int getErrorCode() {
            return errorCode;
        }
    }

    public static class NotConnected extends RuntimeException {}

    public static class ResponseTimeout extends RuntimeException {
        public ResponseTimeout(String message) {
            super(message);
        }
    }

    public static class DuplicatedRequest extends RuntimeException {
        public DuplicatedRequest(EventKey key) {
            super(String.format("Request already exists: %s", key));
        }
    }

}
