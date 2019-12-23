package com.finplant.ib.types;

import com.finplant.ib.impl.utils.Converter;
import com.ib.client.ComboLeg;
import com.ib.client.Contract;
import com.ib.client.DeltaNeutralContract;
import com.ib.client.Types;

import java.math.BigDecimal;
import java.util.List;

public class IbContract {

    private final int conid;
    private final String symbol;
    private final Types.SecType secType;
    private final String lastTradeDateOrContractMonth;
    private final BigDecimal strike;
    private final Types.Right right;
    private final String multiplier;
    private final String exchange;
    private final String primaryExchange;
    private final String currency;
    private final String localSymbol;
    private final String tradingClass;
    private final Types.SecIdType secIdType;
    private final String secId;
    private final DeltaNeutralContract deltaNeutralContract;
    private final boolean includeExpired;
    private final String comboLegsDescription;
    private final List<ComboLeg> comboLegs;

    public IbContract(Contract base) {
        conid = base.conid();
        symbol = base.symbol();
        secType = base.secType();
        lastTradeDateOrContractMonth = base.lastTradeDateOrContractMonth();
        strike = Converter.doubleToBigDecimal("strike", base.strike());
        right = base.right();
        multiplier = base.multiplier();
        exchange = base.exchange();
        primaryExchange = base.primaryExch();
        currency = base.currency();
        localSymbol = base.localSymbol();
        tradingClass = base.tradingClass();
        secIdType = base.secIdType();
        secId = base.secId();
        deltaNeutralContract = base.deltaNeutralContract();
        includeExpired = base.includeExpired();
        comboLegsDescription = base.comboLegsDescrip();
        comboLegs = base.comboLegs();
    }

    public int getConId() {
        return conid;
    }

    public String getSecId() {
        return secId;
    }

    public String getSymbol() {
        return symbol;
    }

    public Types.SecType getSecType() {
        return secType;
    }

    public String getLastTradeDateOrContractMonth() {
        return lastTradeDateOrContractMonth;
    }

    public BigDecimal getStrike() {
        return strike;
    }

    public Types.Right getRight() {
        return right;
    }

    public String getMultiplier() {
        return multiplier;
    }

    public String getExchange() {
        return exchange;
    }

    public String getPrimaryExchange() {
        return primaryExchange;
    }

    public String getCurrency() {
        return currency;
    }

    public String getLocalSymbol() {
        return localSymbol;
    }

    public String getTradingClass() {
        return tradingClass;
    }

    public Types.SecIdType getSecIdType() {
        return secIdType;
    }

    public DeltaNeutralContract getDeltaNeutralContract() {
        return deltaNeutralContract;
    }

    public boolean isIncludeExpired() {
        return includeExpired;
    }

    public String getComboLegsDescription() {
        return comboLegsDescription;
    }

    public List<ComboLeg> getComboLegs() {
        return comboLegs;
    }

    @Override
    public String toString() {
        final StringBuffer buffer = new StringBuffer("{");
        buffer.append("conid=").append(conid);
        buffer.append(", symbol='").append(symbol).append('\'');
        buffer.append(", secType=").append(secType);
        buffer.append(", lastTradeDateOrContractMonth='").append(lastTradeDateOrContractMonth).append('\'');
        buffer.append(", strike=").append(strike);
        buffer.append(", right=").append(right);
        buffer.append(", multiplier='").append(multiplier).append('\'');
        buffer.append(", exchange='").append(exchange).append('\'');
        buffer.append(", primaryExch='").append(primaryExchange).append('\'');
        buffer.append(", currency='").append(currency).append('\'');
        buffer.append(", localSymbol='").append(localSymbol).append('\'');
        buffer.append(", tradingClass='").append(tradingClass).append('\'');
        buffer.append(", secIdType=").append(secIdType);
        buffer.append(", secId='").append(secId).append('\'');
        buffer.append(", deltaNeutralContract=").append(deltaNeutralContract);
        buffer.append(", includeExpired=").append(includeExpired);
        buffer.append(", comboLegsDescription='").append(comboLegsDescription).append('\'');
        buffer.append(", comboLegs=").append(comboLegs);
        buffer.append('}');
        return buffer.toString();
    }
}
