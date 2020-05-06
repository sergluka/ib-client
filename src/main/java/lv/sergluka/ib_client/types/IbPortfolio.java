package lv.sergluka.ib_client.types;

import java.math.BigDecimal;

import com.ib.client.Contract;

@SuppressWarnings("unused")
public class IbPortfolio {

    public static final IbPortfolio COMPLETE = new IbPortfolio();

    private final Contract contract;
    private final BigDecimal position;
    private final BigDecimal marketPrice;
    private final BigDecimal marketValue;
    private final BigDecimal averageCost;
    private final BigDecimal unrealizedPNL;
    private final BigDecimal realizedPNL;
    private final String account;

    public IbPortfolio(Contract contract,
                       BigDecimal position,
                       BigDecimal marketPrice,
                       BigDecimal marketValue,
                       BigDecimal averageCost,
                       BigDecimal unrealizedPNL,
                       BigDecimal realizedPNL,
                       String account) {
        this.contract = contract;
        this.position = position;
        this.marketPrice = marketPrice;
        this.marketValue = marketValue;
        this.averageCost = averageCost;
        this.unrealizedPNL = unrealizedPNL;
        this.realizedPNL = realizedPNL;
        this.account = account;
    }

    private IbPortfolio() {
        this.contract = null;
        this.position = null;
        this.marketPrice = null;
        this.marketValue = null;
        this.averageCost = null;
        this.unrealizedPNL = null;
        this.realizedPNL = null;
        this.account = null;
    }

    public Contract getContract() {
        return contract;
    }

    public BigDecimal getPosition() {
        return position;
    }

    public BigDecimal getMarketPrice() {
        return marketPrice;
    }

    public BigDecimal getMarketValue() {
        return marketValue;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public BigDecimal getUnrealizedPNL() {
        return unrealizedPNL;
    }

    public BigDecimal getRealizedPNL() {
        return realizedPNL;
    }

    public String getAccount() {
        return account;
    }

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder("{");
        buffer.append("contract=").append(contract);
        buffer.append(", position=").append(position);
        buffer.append(", marketPrice=").append(marketPrice);
        buffer.append(", marketValue=").append(marketValue);
        buffer.append(", averageCost=").append(averageCost);
        buffer.append(", unrealizedPNL=").append(unrealizedPNL);
        buffer.append(", realizedPNL=").append(realizedPNL);
        buffer.append(", account='").append(account).append('\'');
        buffer.append('}');
        return buffer.toString();
    }
}
