package com.finplant.ib.types;

import com.ib.client.Contract;
import com.ib.client.Order;
import com.ib.client.OrderState;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.TreeSet;

@SuppressWarnings("unused")
public class IbOrder {

    private final int orderId;
    private final Contract contract;
    private final Order order;
    private final OrderState state;

    private final TreeSet<IbOrderStatus> statuses = new TreeSet<>();

    public IbOrder(int orderId, Contract contract, Order order, OrderState state) {
        this.orderId = orderId;
        this.contract = contract;
        this.order = order;
        this.state = state;
    }

    // TODO: Hide
    public synchronized boolean addStatus(@NotNull IbOrderStatus status) {
        return statuses.add(status);
    }

    public synchronized void addStatuses(@NotNull Set<IbOrderStatus> set) {
        statuses.addAll(set);
    }

    public int getOrderId() {
        return orderId;
    }

    public synchronized IbOrderStatus getLastStatus() {
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
