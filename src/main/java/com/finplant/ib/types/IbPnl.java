package com.finplant.ib.types;

import java.math.BigDecimal;

/**
 * PnL structure.
 *
 * @implNote Any of BigDecimal members can be null.
 */
@SuppressWarnings("unused")
public class IbPnl {
    private final Integer positionId;
    private final BigDecimal dailyPnL;
    private final BigDecimal unrealizedPnL;
    private final BigDecimal realizedPnL;
    private final BigDecimal value;

    public IbPnl(Integer positionId, BigDecimal dailyPnL, BigDecimal unrealizedPnL, BigDecimal realizedPnL,
                 BigDecimal value) {
        this.positionId = positionId;
        this.dailyPnL = dailyPnL;
        this.unrealizedPnL = unrealizedPnL;
        this.realizedPnL = realizedPnL;
        this.value = value;
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

    public Integer getPositionId() {
        return positionId;
    }

    public BigDecimal getDailyPnL() {
        return dailyPnL;
    }

    public BigDecimal getUnrealizedPnL() {
        return unrealizedPnL;
    }

    public BigDecimal getRealizedPnL() {
        return realizedPnL;
    }

    public BigDecimal getValue() {
        return value;
    }
}
