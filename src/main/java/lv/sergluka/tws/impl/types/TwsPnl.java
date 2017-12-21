package lv.sergluka.tws.impl.types;

public class TwsPnl {
    private final Integer positionId;
    private final double dailyPnL;
    private final double unrealizedPnL;
    private final double realizedPnL;
    private final Double value;

    public TwsPnl(Integer positionId, double dailyPnL, double unrealizedPnL, double realizedPnL, Double value) {
        this.positionId = positionId;
        this.dailyPnL = dailyPnL;
        this.unrealizedPnL = unrealizedPnL;
        this.realizedPnL = realizedPnL;
        this.value = value;
    }

    public int getPositionId() {
        return positionId;
    }

    public double getDailyPnL() {
        return dailyPnL;
    }

    public double getUnrealizedPnL() {
        return unrealizedPnL;
    }

    public double getRealizedPnL() {
        return realizedPnL;
    }

    public double getValue() {
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
