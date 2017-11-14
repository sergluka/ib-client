package lv.sergluka.tws.impl.types;

import java.util.Objects;

public class TwsOrderStatus {
    private final int orderId;
    private final String status;
    private final double filled;
    private final double remaining;
    private final double avgFillPrice;
    private final int permId;
    private final int parentId;
    private final double lastFillPrice;
    private final int clientId;
    private final String whyHeld;
    private final double mktCapPrice;

    public TwsOrderStatus(final int orderId,
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
        this.status = status;
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

    public String getStatus() {
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

        TwsOrderStatus that = (TwsOrderStatus) obj;
        return orderId == that.orderId &&
                Double.compare(that.filled, filled) == 0 &&
                Double.compare(that.remaining, remaining) == 0 &&
                Double.compare(that.avgFillPrice, avgFillPrice) == 0 &&
                permId == that.permId &&
                parentId == that.parentId &&
                Double.compare(that.lastFillPrice, lastFillPrice) == 0 &&
                clientId == that.clientId &&
                Double.compare(that.mktCapPrice, mktCapPrice) == 0 &&
                Objects.equals(status, that.status) &&
                Objects.equals(whyHeld, that.whyHeld);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, status, filled, remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId,
                            whyHeld, mktCapPrice);
    }
}
