package com.finplant.ib;

import com.finplant.ib.impl.request.RequestKey;

@SuppressWarnings({"WeakerAccess", "unused"})
public class IbExceptions {

    public abstract static class IbClientError extends RuntimeException {
        IbClientError(String text) {
            super(text);
        }
    }

    public static class TerminalError extends IbClientError {

        //An attempt was made to cancel an order not currently in the system.
        public static final int CODE_CANT_FIND_ORDER = 135;

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

    public static class NotConnectedError extends IbClientError {
        public NotConnectedError() {
            super("Not connected");
        }
    }

    public static class DuplicatedRequestError extends IbClientError {
        public DuplicatedRequestError(RequestKey key) {
            super(String.format("Request already exists: %s", key));
        }
    }

    public static class NoTicksError extends IbClientError {
        public NoTicksError() {
            super("Has no ticks");
        }
    }

    public static class OrderAlreadyFilledError extends IbClientError {
        OrderAlreadyFilledError(int orderId) {
            super(String.format("Order %d already is filled", orderId));
        }
    }

}
