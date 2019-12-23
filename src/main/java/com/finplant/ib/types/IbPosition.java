package com.finplant.ib.types;

import java.math.BigDecimal;
import java.util.Objects;

import com.ib.client.Contract;

/**
 * Data class for IB position data.
 */
@SuppressWarnings("unused")
public class IbPosition {

    public static final IbPosition COMPLETE = new IbPosition();

    private final String account;
    private final Contract contract;
    private final BigDecimal pos;
    private final BigDecimal avgCost;

    public IbPosition(String account, Contract contract, BigDecimal pos, BigDecimal avgCost) {
        this.account = account;
        this.contract = contract;
        this.pos = pos;
        this.avgCost = avgCost;
    }

    public String getAccount() {
        return account;
    }

    public Contract getContract() {
        return contract;
    }

    public BigDecimal getPos() {
        return pos;
    }

    public BigDecimal getAvgCost() {
        return avgCost;
    }

    public boolean isValid() {
        return account != null && contract != null;
    }

    @Override
    public int hashCode() {
        return Objects.hash(account, contract, pos, avgCost);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        IbPosition that = (IbPosition) obj;
        return that.pos.compareTo(pos) == 0 &&
               that.avgCost.compareTo(avgCost) == 0 &&
               Objects.equals(account, that.account) &&
               Objects.equals(contract, that.contract);
    }

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder("{");
        buffer.append("account='").append(account).append('\'');
        buffer.append(", contract=").append(contract);
        buffer.append(", pos=").append(pos);
        buffer.append(", avgCost=").append(avgCost);
        buffer.append('}');
        return buffer.toString();
    }

    private IbPosition() {
        account = null;
        contract = null;
        pos = BigDecimal.ZERO;
        avgCost = BigDecimal.ZERO;
    }
}
