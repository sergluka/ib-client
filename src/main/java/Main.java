import lv.sergluka.tws.TwsClient;

class Main {

    static class TwsClientImpl extends TwsClient {
        @Override
        public void orderStatus(int orderId, String status, double filled, double remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld, double mktCapPrice) {
            System.out.println(String.format("new status for: %d", orderId));
        }
    }

    public static void main(String[] args) throws Exception {
        try (TwsClientImpl client = new TwsClientImpl()) {
            client.connect("127.0.0.1", 7497, 1);

            // client.getSocket().placeOrder(.....)
        }
    }
}