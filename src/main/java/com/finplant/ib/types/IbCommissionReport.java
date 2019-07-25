package com.finplant.ib.types;

import com.finplant.ib.impl.utils.Converter;
import com.ib.client.CommissionReport;

import java.math.BigDecimal;

public class IbCommissionReport {

    private final String execId;
    private final BigDecimal commission;
    private final String currency;
    private final BigDecimal realizedPnl;
    private final BigDecimal yield;
    private final int yieldRedemptionDate;

    public IbCommissionReport(CommissionReport base) {
        execId = base.execId();
        commission = Converter.doubleToBigDecimal("commission", base.commission());
        currency = base.currency();
        realizedPnl = Converter.doubleToBigDecimal("realizedPNL", base.realizedPNL());
        yield = Converter.doubleToBigDecimal("yield", base.yield());
        yieldRedemptionDate = base.yieldRedemptionDate();
    }

    public String getExecId() {
        return execId;
    }

    public BigDecimal getCommission() {
        return commission;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getRealizedPnl() {
        return realizedPnl;
    }

    public BigDecimal getYield() {
        return yield;
    }

    public int getYieldRedemptionDate() {
        return yieldRedemptionDate;
    }

    @Override
    public String toString() {
        final StringBuffer buffer = new StringBuffer("{");
        buffer.append("execId='").append(execId).append('\'');
        buffer.append(", commission=").append(commission);
        buffer.append(", currency='").append(currency).append('\'');
        buffer.append(", realizedPnl=").append(realizedPnl);
        buffer.append(", yield=").append(yield);
        buffer.append(", yieldRedemptionDate=").append(yieldRedemptionDate);
        buffer.append('}');
        return buffer.toString();
    }
}
