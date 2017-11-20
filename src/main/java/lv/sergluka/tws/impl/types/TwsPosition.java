package lv.sergluka.tws.impl.types;

import com.ib.client.Contract;

import java.util.Objects;

public class TwsPosition {
    private final String account;
    private final Contract contract;
    private final double pos;
    private final double avgCost;

    public TwsPosition(String account, Contract contract, double pos, double avgCost) {
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

    public double getPos() {
        return pos;
    }

    public double getAvgCost() {
        return avgCost;
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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        TwsPosition that = (TwsPosition) obj;
        return Double.compare(that.pos, pos) == 0 &&
                Double.compare(that.avgCost, avgCost) == 0 &&
                Objects.equals(account, that.account) &&
                Objects.equals(contract, that.contract);
    }

    @Override
    public int hashCode() {
        return Objects.hash(account, contract, pos, avgCost);
    }
}
