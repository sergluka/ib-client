package com.finplant.ib;

import com.finplant.ib.impl.request.RequestKey;

@SuppressWarnings({"WeakerAccess", "unused"})
public class IbExceptions {

    public static class IbClientError extends RuntimeException {

        private final Integer requestId;

        public IbClientError(Integer requestId, String text) {
            super(text);
            this.requestId = requestId;
        }

        public IbClientError(String text) {
            super(text);
            this.requestId = null;
        }

        public Integer getRequestId() {
            return requestId;
        }
    }

    public static class TerminalError extends IbClientError {

        //An attempt was made to cancel an order not currently in the system.
        public static final int CODE_CANT_FIND_ORDER = 135;

        private final String errorMsg;
        private final int errorCode;

        public TerminalError(int requestId, String errorMsg, int errorCode) {
            super(requestId, String.format("[%d]: id=%d, %s", errorCode, requestId, errorMsg));
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
            super(key.getId(), String.format("Request already exists: %s", key));
        }
    }

    public static class NoDataError extends IbClientError {
        public NoDataError(int requestId) {
            super(requestId, "Has no data");
        }

        public NoDataError(int requestId, String message) {
            super(requestId, message);
        }
    }

    public static class NoPermissions extends IbClientError {
        public NoPermissions(int requestId, String message) {
            super(requestId, message);
        }
    }

    public static class OrderAlreadyFilledError extends IbClientError {
        public OrderAlreadyFilledError(int orderId) {
            super(String.format("Order %d already is filled", orderId));
        }
    }

    public static class SubscriptionLostByDisconnectError extends IbClientError {
        public SubscriptionLostByDisconnectError(int requestId, String message) {
            super(requestId, message);
        }
    }

    public static class SubscriptionLostByCompetingSessionError extends IbClientError {
        public SubscriptionLostByCompetingSessionError(int requestId, String message) {
            super(requestId, message);
        }
    }

    public static class NoSecurityDefinitionError extends IbClientError {
        public NoSecurityDefinitionError(int requestId, String message) {
            super(requestId, message);
        }
    }

    public static class MaxNumberOfTickersError extends IbClientError {
        public MaxNumberOfTickersError(int requestId, String message) {
            super(requestId, message);
        }
    }
}
