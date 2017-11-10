package lv.sergluka.tws;

public class TwsExceptions {

    public static class ServerError extends RuntimeException {

        private final String errorMsg;
        private final int errorCode;

        public ServerError(String errorMsg, int errorCode) {
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
}
