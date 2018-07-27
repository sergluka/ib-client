package lv.sergluka.ib.impl.types;

import static org.apache.commons.math3.util.Precision.EPSILON;

import java.util.Objects;

import org.apache.commons.math3.util.Precision;

import com.ib.client.Contract;

public class IbPosition {

    public static final IbPosition COMPLETE = new IbPosition();

    private final String account;
    private final Contract contract;
    private final double pos; // TODO Convert all floats to BigDecimals
    private final double avgCost;

    public IbPosition(String account, Contract contract, double pos, double avgCost) {
        this.account = account;
        this.contract = contract;
        this.pos = pos;
        this.avgCost = avgCost;
    }

    private IbPosition() {
        account = null;
        contract = null;
        pos = Double.NEGATIVE_INFINITY;
        avgCost = Double.NEGATIVE_INFINITY;
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

    public boolean isValid() {
        return account != null && contract != null;
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

        IbPosition that = (IbPosition) obj;
        return Precision.equals(that.pos, pos, EPSILON) &&
                Precision.equals(that.avgCost, avgCost, EPSILON) &&
                Objects.equals(account, that.account) &&
                Objects.equals(contract, that.contract);
    }

    @Override
    public int hashCode() {
        return Objects.hash(account, contract, pos, avgCost);
    }
}
