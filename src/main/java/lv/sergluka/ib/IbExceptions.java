package lv.sergluka.ib;

import lv.sergluka.ib.impl.sender.EventKey;

public class IbExceptions {

    public static class TerminalError extends RuntimeException {

        private final String errorMsg;
        private final int errorCode;

        public TerminalError(String errorMsg, int errorCode) {
            super(String.format("[%d]: %s", errorCode, errorMsg));
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

    public static class NotConnected extends RuntimeException {
        public NotConnected() {
            super("Not connected");
        }
    }

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
