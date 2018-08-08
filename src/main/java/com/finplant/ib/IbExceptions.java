package com.finplant.ib;

import com.finplant.ib.impl.subscription.SubscriptionKey;

public class IbExceptions {

    public static class TerminalError extends RuntimeException {

        //An attempt was made to cancel an order not currently in the system.
        public static int CODE_CANT_FIND_ORDER = 135;

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
        public DuplicatedRequest(SubscriptionKey key) {
            super(String.format("Request already exists: %s", key));
        }
    }

    public static class NoTicks extends RuntimeException {
        public NoTicks() {
            super("Has no ticks");
        }
    }

}
