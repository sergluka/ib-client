package lv.sergluka.tws.impl.types;

import com.ib.client.Contract;
import com.ib.client.Order;
import com.ib.client.OrderState;

public class TwsOrder {

    private final int orderId;
    private final Contract contract;
    private final Order order;
    private final OrderState state;

    private TwsOrderStatus statuses;

    public TwsOrder(int orderId, Contract contract, Order order, OrderState state) {
        this.orderId = orderId;
        this.contract = contract;
        this.order = order;
        this.state = state;
    }

    public int getOrderId() {
        return orderId;
    }

    public TwsOrderStatus getStatus() {
        return statuses;
    }

    public Contract getContract() {
        return contract;
    }

    public Order getOrder() {
        return order;
    }

    public OrderState getState() {
        return state;
    }
}
