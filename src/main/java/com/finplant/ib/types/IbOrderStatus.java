package com.finplant.ib.types;

import java.math.BigDecimal;
import java.util.Objects;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import com.ib.client.OrderStatus;

@SuppressWarnings("unused")
public class IbOrderStatus {
    private final int orderId;
    private final OrderStatus status;
    private final BigDecimal filled;
    private final BigDecimal remaining;
    private final BigDecimal avgFillPrice;
    private final int permId;
    private final int parentId;
    private final BigDecimal lastFillPrice;
    private final int clientId;
    private final String whyHeld;
    private final BigDecimal mktCapPrice;

    public IbOrderStatus(final int orderId,
                         final String status,
                         final BigDecimal filled,
                         final BigDecimal remaining,
                         final BigDecimal avgFillPrice,
                         final int permId,
                         final int parentId,
                         final BigDecimal lastFillPrice,
                         final int clientId,
                         final String whyHeld,
                         final BigDecimal mktCapPrice) {
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

    public BigDecimal getFilled() {
        return filled;
    }

    public BigDecimal getRemaining() {
        return remaining;
    }

    public BigDecimal getAvgFillPrice() {
        return avgFillPrice;
    }

    public int getPermId() {
        return permId;
    }

    public int getParentId() {
        return parentId;
    }

    public BigDecimal getLastFillPrice() {
        return lastFillPrice;
    }

    public int getClientId() {
        return clientId;
    }

    public String getWhyHeld() {
        return whyHeld;
    }

    public BigDecimal getMktCapPrice() {
        return mktCapPrice;
    }

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder("{");
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
                that.filled.compareTo(filled) == 0 &&
                that.remaining.compareTo(remaining) == 0 &&
                that.avgFillPrice.compareTo(avgFillPrice) == 0 &&
                that.lastFillPrice.compareTo(lastFillPrice) == 0 &&
                that.mktCapPrice.compareTo(mktCapPrice) == 0 &&
                Objects.equals(status, that.status) &&
                Objects.equals(whyHeld, that.whyHeld);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, status, filled, remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId,
                            whyHeld, mktCapPrice);
    }

    public boolean isCanceled() {
        return status == OrderStatus.ApiCancelled || status == OrderStatus.Cancelled;
    }

    public boolean isInactive() {
        return status == OrderStatus.Inactive;
    }

    public boolean isFilled() {
        return status == OrderStatus.Filled;
    }
}
