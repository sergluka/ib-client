package lv.sergluka.ib.impl.types;

import com.ib.client.Contract;

public class IbPortfolio {

    private Contract contract;
    private Double position;
    private Double marketPrice;
    private Double marketValue;
    private Double averageCost;
    private Double unrealizedPNL;
    private Double realizedPNL;
    private String account;

    public IbPortfolio(Contract contract,
                       Double position,
                       Double marketPrice,
                       Double marketValue,
                       Double averageCost,
                       Double unrealizedPNL,
                       Double realizedPNL,
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

    public Contract getContract() {
        return contract;
    }

    public Double getPosition() {
        return position;
    }

    public Double getMarketPrice() {
        return marketPrice;
    }

    public Double getMarketValue() {
        return marketValue;
    }

    public Double getAverageCost() {
        return averageCost;
    }

    public Double getUnrealizedPNL() {
        return unrealizedPNL;
    }

    public Double getRealizedPNL() {
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
