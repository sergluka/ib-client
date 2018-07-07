package lv.sergluka.ib.impl.types;

/**
 * PnL structure. Any of Double members can be null, if TWS sends MAX_DBL value.
 */
public class IbPnl {
    private final Integer positionId;
    private final Double dailyPnL;
    private final Double unrealizedPnL;
    private final Double realizedPnL;
    private final Double value;

    public IbPnl(Integer positionId, Double dailyPnL, Double unrealizedPnL, Double realizedPnL, Double value) {
        this.positionId = positionId;
        this.dailyPnL = dailyPnL;
        this.unrealizedPnL = unrealizedPnL;
        this.realizedPnL = realizedPnL;
        this.value = value;
    }

    public Integer getPositionId() {
        return positionId;
    }

    public Double getDailyPnL() {
        return dailyPnL;
    }

    public Double getUnrealizedPnL() {
        return unrealizedPnL;
    }

    public Double getRealizedPnL() {
        return realizedPnL;
    }

    public Double getValue() {
        return value;
    }

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder("{");
        buffer.append("positionId=").append(positionId);
        buffer.append(", dailyPnL=").append(dailyPnL);
        buffer.append(", unrealizedPnL=").append(unrealizedPnL);
        buffer.append(", realizedPnL=").append(realizedPnL);
        buffer.append(", value=").append(value);
        buffer.append('}');
        return buffer.toString();
    }
}
