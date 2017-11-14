import com.ib.client.Contract;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.Types;
import lv.sergluka.tws.TwsClient;

import java.util.concurrent.TimeUnit;

class Main {
    public static void main(String[] args) throws Exception {
        try (TwsClient client = new TwsClient()) {
            client.setOnOrderStatus((id, status) -> {
                System.out.println(status);
            });

            client.connect("127.0.0.1", 7497, 1);
            long time = client.reqCurrentTime().get(5, TimeUnit.SECONDS);
            System.out.println(time);

            Contract contract = new Contract();
            contract.symbol("GC");
            contract.currency("USD");
            contract.exchange("NYMEX");
            contract.secType(Types.SecType.FUT);
            contract.multiplier("100");
            contract.lastTradeDateOrContractMonth("201712");

            Order order = new Order();
            order.orderId(client.nextOrderId());
            order.action("BUY");
            order.orderType("STP");
            order.auxPrice(1.1);
            order.triggerPrice(0.23);
            order.tif("GTC");
            order.totalQuantity(1.0);
            order.outsideRth(true);

            final OrderState status = client.placeOrder(contract, order).get(10, TimeUnit.SECONDS);
            System.out.println(status);

            Thread.sleep(10000);
        }
    }
}