package lv.sergluka.ib.impl.types;

import com.ib.client.Contract;
import com.ib.client.Order;
import com.ib.client.OrderState;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class IbOrder {

    private static final Logger log = LoggerFactory.getLogger(IbOrder.class);

    private final int orderId;
    private final Contract contract;
    private final Order order;
    private final OrderState state;

    private final TreeSet<IbOrderStatus> statuses = new TreeSet<>(); // TODO: make thread safe

    public IbOrder(int orderId, Contract contract, Order order, OrderState state) {
        this.orderId = orderId;
        this.contract = contract;
        this.order = order;
        this.state = state;
    }

    public boolean addStatus(@NotNull IbOrderStatus status) {
        return statuses.add(status);
    }

    public boolean addStatuses(@NotNull Set<IbOrderStatus> statuses) {
        return statuses.addAll(statuses);
    }

    public int getOrderId() {
        return orderId;
    }

    public IbOrderStatus getLastStatus() {
        return statuses.last();
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

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder("{");
        buffer.append("orderId=").append(orderId);
        buffer.append(", contract=").append(contract);
        buffer.append(", order=").append(order);
        buffer.append(", state=").append(state);
        buffer.append(", statuses=").append(statuses);
        buffer.append('}');
        return buffer.toString();
    }
}
