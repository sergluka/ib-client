package lv.sergluka.tws.impl.types;

public class OrderStatus {
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

    public OrderStatus(final int orderId,
                       final String status,
                       final double filled,
                       final double remaining,
                       final double avgFillPrice,
                       final int permId,
                       final int parentId,
                       final double lastFillPrice,
                       final int clientId, final String whyHeld, final double mktCapPrice) {
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

    private int getOrderId() {
        return orderId;
    }

    private String getStatus() {
        return status;
    }

    private double getFilled() {
        return filled;
    }

    private double getRemaining() {
        return remaining;
    }

    private double getAvgFillPrice() {
        return avgFillPrice;
    }

    private int getPermId() {
        return permId;
    }

    private int getParentId() {
        return parentId;
    }

    private double getLastFillPrice() {
        return lastFillPrice;
    }

    private int getClientId() {
        return clientId;
    }

    private String getWhyHeld() {
        return whyHeld;
    }

    private double getMktCapPrice() {
        return mktCapPrice;
    }
}
