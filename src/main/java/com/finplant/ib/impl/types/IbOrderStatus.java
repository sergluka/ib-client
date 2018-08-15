package com.finplant.ib.impl.types;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import com.ib.client.OrderStatus;
import org.apache.commons.math3.util.Precision;

import java.util.Objects;

import static org.apache.commons.math3.util.Precision.EPSILON;

public class IbOrderStatus implements Comparable<IbOrderStatus> {
    private final int orderId;
    private final OrderStatus status;
    private final double filled;
    private final double remaining;
    private final double avgFillPrice;
    private final int permId;
    private final int parentId;
    private final double lastFillPrice;
    private final int clientId;
    private final String whyHeld;
    private final double mktCapPrice;

    public IbOrderStatus(final int orderId,
                         final String status,
                         final double filled,
                         final double remaining,
                         final double avgFillPrice,
                         final int permId,
                         final int parentId,
                         final double lastFillPrice,
                         final int clientId,
                         final String whyHeld,
                         final double mktCapPrice) {
        this.orderId = orderId;
        this.status = OrderStatus.get(status);
        this.filled = filled;
        this.remaining = remaining;
        this.avgFillPrice = avgFillPrice;
        this.permId = permId;
        this.parentId = parentId;
        this.lastFillPrice = lastFillPrice;
        this.clientId = clientId;
        this.whyHeld = whyHeld;
        this.mktCapPrice = mktCapPrice;
    }

    public int getOrderId() {
        return orderId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public double getFilled() {
        return filled;
    }

    public double getRemaining() {
        return remaining;
    }

    public double getAvgFillPrice() {
        return avgFillPrice;
    }

    public int getPermId() {
        return permId;
    }

    public int getParentId() {
        return parentId;
    }

    public double getLastFillPrice() {
        return lastFillPrice;
    }

    public int getClientId() {
        return clientId;
    }

    public String getWhyHeld() {
        return whyHeld;
    }

    public double getMktCapPrice() {
        return mktCapPrice;
    }

    @Override
    public String toString() {
        final StringBuffer buffer = new StringBuffer("{");
        buffer.append("orderId=").append(orderId);
        buffer.append(", status='").append(status).append('\'');
        buffer.append(", filled=").append(filled);
        buffer.append(", remaining=").append(remaining);
        buffer.append(", avgFillPrice=").append(avgFillPrice);
        buffer.append(", permId=").append(permId);
        buffer.append(", parentId=").append(parentId);
        buffer.append(", lastFillPrice=").append(lastFillPrice);
        buffer.append(", clientId=").append(clientId);
        buffer.append(", whyHeld='").append(whyHeld).append('\'');
        buffer.append(", mktCapPrice=").append(mktCapPrice);
        buffer.append('}');
        return buffer.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        IbOrderStatus that = (IbOrderStatus) obj;
        return orderId == that.orderId &&
                permId == that.permId &&
                parentId == that.parentId &&
                clientId == that.clientId &&
                Precision.equals(that.filled, filled, EPSILON) &&
                Precision.equals(that.remaining, remaining, EPSILON) &&
                Precision.equals(that.avgFillPrice, avgFillPrice, EPSILON) &&
                Precision.equals(that.lastFillPrice, lastFillPrice, EPSILON) &&
                Precision.equals(that.mktCapPrice, mktCapPrice, EPSILON) &&
                Objects.equals(status, that.status) &&
                Objects.equals(whyHeld, that.whyHeld);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, status, filled, remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId,
                            whyHeld, mktCapPrice);
    }


    @Override
    public int compareTo(IbOrderStatus rhs) {
        return ComparisonChain.start()
                              .compare(orderId, rhs.orderId)
                              .compare(filled, rhs.filled)
                              .compare(remaining, rhs.remaining)
                              .compare(avgFillPrice, rhs.avgFillPrice)
                              .compare(permId, rhs.permId)
                              .compare(parentId, rhs.parentId)
                              .compare(lastFillPrice, rhs.lastFillPrice)
                              .compare(clientId, rhs.clientId)
                              .compare(mktCapPrice, rhs.mktCapPrice)
                              .compare(status, rhs.status, Ordering.natural().nullsFirst())
                              .compare(whyHeld, rhs.whyHeld, Ordering.natural().nullsFirst())
                              .result();

    }
}
