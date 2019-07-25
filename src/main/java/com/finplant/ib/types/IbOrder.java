package com.finplant.ib.types;

import com.ib.client.Contract;
import com.ib.client.Order;
import com.ib.client.OrderState;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.List;

@SuppressWarnings("unused")
public class IbOrder {

    private final int orderId;
    private final Contract contract;
    private final Order order;
    private final OrderState state;

    private final LinkedList<IbOrderStatus> statuses = new LinkedList<>();

    public IbOrder(int orderId, Contract contract, Order order, OrderState state) {
        this.orderId = orderId;
        this.contract = contract;
        this.order = order;
        this.state = state;
    }

    // TODO: Hide
    public synchronized boolean addStatus(@NotNull IbOrderStatus status) {
        if (!statuses.contains(status)) {
            return statuses.add(status);
        }

        return false;
    }

    public synchronized void addStatuses(@NotNull List<IbOrderStatus> list) {
        statuses.addAll(list);
    }

    public int getOrderId() {
        return orderId;
    }

    public synchronized List<IbOrderStatus> getStatuses() {
        return statuses;
    }

    public synchronized IbOrderStatus getLastStatus() {
        return statuses.getLast();
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
