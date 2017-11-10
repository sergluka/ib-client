package lv.sergluka.tws;

public class TwsExceptions {

    static class ServerError extends RuntimeException {

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

}
