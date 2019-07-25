package com.finplant.ib.types;

import com.ib.client.Execution;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class IbExecution {

    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd  HH:mm:ss");

    private final int orderId;
    private final int clientId;
    private final String execId;
    private final LocalDateTime time;
    private final String acctNumber;
    private final String exchange;
    private final String side;
    private final BigDecimal shares;
    private final BigDecimal price;
    private final int permId;
    private final int liquidation;
    private final BigDecimal cumQty;
    private final BigDecimal avgPrice;
    private final String orderRef;
    private final String evRule;
    private final BigDecimal evMultiplier;
    private final String modelCode;

    public IbExecution(Execution base) {
        orderId = base.orderId();
        clientId = base.clientId();
        execId = base.execId();
        time = LocalDateTime.parse(base.time(), dateTimeFormatter);
        acctNumber = base.acctNumber();
        exchange = base.exchange();
        side = base.side();
        shares = BigDecimal.valueOf(base.shares());
        price = BigDecimal.valueOf(base.price());
        permId = base.permId();
        liquidation = base.liquidation();
        cumQty = BigDecimal.valueOf(base.cumQty());
        avgPrice = BigDecimal.valueOf(base.avgPrice());
        orderRef = base.orderRef();
        evRule = base.evRule();
        evMultiplier = BigDecimal.valueOf(base.evMultiplier());
        modelCode = base.modelCode();
    }

    public int getOrderId() {
        return orderId;
    }

    public int getClientId() {
        return clientId;
    }

    public String getExecId() {
        return execId;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public String getAcctNumber() {
        return acctNumber;
    }

    public String getExchange() {
        return exchange;
    }

    public String getSide() {
        return side;
    }

    public BigDecimal getShares() {
        return shares;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getPermId() {
        return permId;
    }

    public int getLiquidation() {
        return liquidation;
    }

    public BigDecimal getCumQty() {
        return cumQty;
    }

    public BigDecimal getAvgPrice() {
        return avgPrice;
    }

    public String getOrderRef() {
        return orderRef;
    }

    public String getEvRule() {
        return evRule;
    }

    public BigDecimal getEvMultiplier() {
        return evMultiplier;
    }

    public String getModelCode() {
        return modelCode;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IbExecution)) {
            return false;
        }

        IbExecution that = (IbExecution) other;

        return execId.equals(that.execId);
    }

    @Override
    public int hashCode() {
        return execId.hashCode();
    }

    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer("{");
        buffer.append("orderId=").append(getOrderId());
        buffer.append(", clientId=").append(getClientId());
        buffer.append(", execId='").append(getExecId()).append('\'');
        buffer.append(", time='").append(getTime()).append('\'');
        buffer.append(", acctNumber='").append(getAcctNumber()).append('\'');
        buffer.append(", exchange='").append(getExchange()).append('\'');
        buffer.append(", side='").append(getSide()).append('\'');
        buffer.append(", shares=").append(getShares());
        buffer.append(", price=").append(getPrice());
        buffer.append(", permId=").append(getPermId());
        buffer.append(", liquidation=").append(getLiquidation());
        buffer.append(", cumQty=").append(getCumQty());
        buffer.append(", avgPrice=").append(getAvgPrice());
        buffer.append(", orderRef='").append(getOrderRef()).append('\'');
        buffer.append(", evRule='").append(getEvRule()).append('\'');
        buffer.append(", evMultiplier=").append(getEvMultiplier());
        buffer.append(", modelCode='").append(getModelCode()).append('\'');
        buffer.append('}');
        return buffer.toString();
    }
}
