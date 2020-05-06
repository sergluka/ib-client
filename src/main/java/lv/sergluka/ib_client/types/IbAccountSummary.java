package lv.sergluka.ib_client.types;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class IbAccountSummary {

    private String accountType;
    private BigDecimal netLiquidation;
    private BigDecimal totalCashValue;
    private BigDecimal settledCash;
    private BigDecimal buyingPower;
    private BigDecimal equityWithLoanValue;
    private BigDecimal previousEquityWithLoanValue;
    private BigDecimal grossPositionValue;
    private BigDecimal regTEquity;
    private BigDecimal regTMargin;
    private BigDecimal sma;
    private BigDecimal initMarginReq;
    private BigDecimal maintMarginReq;
    private BigDecimal availableFunds;
    private BigDecimal excessLiquidity;
    private BigDecimal cushion;
    private BigDecimal fullInitMarginReq;
    private BigDecimal fullMaintMarginReq;
    private BigDecimal fullAvailableFunds;
    private BigDecimal fullExcessLiquidity;
    private Date lookAheadNextChange;
    private BigDecimal lookAheadInitMarginReq;
    private BigDecimal lookAheadMaintMarginReq;
    private BigDecimal lookAheadAvailableFunds;
    private BigDecimal lookAheadExcessLiquidity;
    private Integer highestSeverity;
    private Integer dayTradesRemaining;
    private Integer leverage;

    private final Map<String, DetailsPerCurrency> details = new HashMap<>();

    @SuppressWarnings("checkstyle:MethodLength")
    public void update(String tag, String value, String currency) throws Exception {

        switch (tag) {
            case "AccountType":
                shouldBeNull(accountType, () -> accountType = value);
                break;
            case "NetLiquidation":
                shouldBeNull(netLiquidation, () -> netLiquidation = new BigDecimal(value));
                break;
            case "TotalCashValue":
                shouldBeNull(totalCashValue, () -> totalCashValue = new BigDecimal(value));
                break;
            case "SettledCash":
                shouldBeNull(settledCash, () -> settledCash = new BigDecimal(value));
                break;
            case "BuyingPower":
                shouldBeNull(buyingPower, () -> buyingPower = new BigDecimal(value));
                break;
            case "EquityWithLoanValue":
                shouldBeNull(equityWithLoanValue, () -> equityWithLoanValue = new BigDecimal(value));
                break;
            case "PreviousEquityWithLoanValue":
                shouldBeNull(previousEquityWithLoanValue, () -> previousEquityWithLoanValue = new BigDecimal(value));
                break;
            case "GrossPositionValue":
                shouldBeNull(grossPositionValue, () -> grossPositionValue = new BigDecimal(value));
                break;
            case "RegTEquity":
                shouldBeNull(regTEquity, () -> regTEquity = new BigDecimal(value));
                break;
            case "RegTMargin":
                shouldBeNull(regTMargin, () -> regTMargin = new BigDecimal(value));
                break;
            case "SMA":
                shouldBeNull(sma, () -> sma = new BigDecimal(value));
                break;
            case "InitMarginReq":
                shouldBeNull(initMarginReq, () -> initMarginReq = new BigDecimal(value));
                break;
            case "MaintMarginReq":
                shouldBeNull(maintMarginReq, () -> maintMarginReq = new BigDecimal(value));
                break;
            case "AvailableFunds":
                shouldBeNull(availableFunds, () -> availableFunds = new BigDecimal(value));
                break;
            case "ExcessLiquidity":
                shouldBeNull(excessLiquidity, () -> excessLiquidity = new BigDecimal(value));
                break;
            case "Cushion":
                shouldBeNull(cushion, () -> cushion = new BigDecimal(value));
                break;
            case "FullInitMarginReq":
                shouldBeNull(fullInitMarginReq, () -> fullInitMarginReq = new BigDecimal(value));
                break;
            case "FullMaintMarginReq":
                shouldBeNull(fullMaintMarginReq, () -> fullMaintMarginReq = new BigDecimal(value));
                break;
            case "FullAvailableFunds":
                shouldBeNull(fullAvailableFunds, () -> fullAvailableFunds = new BigDecimal(value));
                break;
            case "FullExcessLiquidity":
                shouldBeNull(fullExcessLiquidity, () -> fullExcessLiquidity = new BigDecimal(value));
                break;
            case "LookAheadNextChange":
                long ms = TimeUnit.SECONDS.toMillis(Integer.parseInt(value));
                shouldBeNull(lookAheadNextChange, () -> lookAheadNextChange = new Date(ms));
                break;
            case "LookAheadInitMarginReq":
                shouldBeNull(lookAheadInitMarginReq, () -> lookAheadInitMarginReq = new BigDecimal(value));
                break;
            case "LookAheadMaintMarginReq":
                shouldBeNull(lookAheadMaintMarginReq, () -> lookAheadMaintMarginReq = new BigDecimal(value));
                break;
            case "LookAheadAvailableFunds":
                shouldBeNull(lookAheadAvailableFunds, () -> lookAheadAvailableFunds = new BigDecimal(value));
                break;
            case "LookAheadExcessLiquidity":
                shouldBeNull(lookAheadExcessLiquidity, () -> lookAheadExcessLiquidity = new BigDecimal(value));
                break;
            case "HighestSeverity":
                shouldBeNull(highestSeverity, () -> highestSeverity = Integer.valueOf(value));
                break;
            case "DayTradesRemaining":
                shouldBeNull(dayTradesRemaining, () -> dayTradesRemaining = Integer.valueOf(value));
                break;
            case "Leverage":
                shouldBeNull(leverage, () -> leverage = Integer.valueOf(value));
                break;

            // details per currency

            case "Currency":
                applyDetails(currency, summary -> summary.currency = value);
                break;
            case "RealCurrency":
                applyDetails(currency, summary -> summary.realCurrency = value);
                break;
            case "CashBalance":
                applyDetails(currency, summary -> summary.cashBalance = new BigDecimal(value));
                break;
            case "TotalCashBalance":
                applyDetails(currency, summary -> summary.totalCashBalance = new BigDecimal(value));
                break;
            case "AccruedCash":
                applyDetails(currency, summary -> summary.accruedCash = new BigDecimal(value));
                break;
            case "StockMarketValue":
                applyDetails(currency, summary -> summary.stockMarketValue = new BigDecimal(value));
                break;
            case "OptionMarketValue":
                applyDetails(currency, summary -> summary.optionMarketValue = new BigDecimal(value));
                break;
            case "FutureOptionValue":
                applyDetails(currency, summary -> summary.futureOptionValue = new BigDecimal(value));
                break;
            case "FuturesPNL":
                applyDetails(currency, summary -> summary.futuresPNL = new BigDecimal(value));
                break;
            case "NetLiquidationByCurrency":
                applyDetails(currency, summary -> summary.netLiquidationByCurrency = new BigDecimal(value));
                break;
            case "UnrealizedPnL":
                applyDetails(currency, summary -> summary.unrealizedPnL = new BigDecimal(value));
                break;
            case "RealizedPnL":
                applyDetails(currency, summary -> summary.realizedPnL = new BigDecimal(value));
                break;
            case "ExchangeRate":
                applyDetails(currency, summary -> summary.exchangeRate = new BigDecimal(value));
                break;
            case "FundValue":
                applyDetails(currency, summary -> summary.fundValue = new BigDecimal(value));
                break;
            case "NetDividend":
                applyDetails(currency, summary -> summary.netDividend = new BigDecimal(value));
                break;
            case "MutualFundValue":
                applyDetails(currency, summary -> summary.mutualFundValue = new BigDecimal(value));
                break;
            case "MoneyMarketFundValue":
                applyDetails(currency, summary -> summary.moneyMarketFundValue = new BigDecimal(value));
                break;
            case "CorporateBondValue":
                applyDetails(currency, summary -> summary.corporateBondValue = new BigDecimal(value));
                break;
            case "TBondValue":
                applyDetails(currency, summary -> summary.tBondValue = new BigDecimal(value));
                break;
            case "TBillValue":
                applyDetails(currency, summary -> summary.tBillValue = new BigDecimal(value));
                break;
            case "WarrantValue":
                applyDetails(currency, summary -> summary.warrantValue = new BigDecimal(value));
                break;
            case "FxCashBalance":
                applyDetails(currency, summary -> summary.fxCashBalance = new BigDecimal(value));
                break;
            case "AccountOrGroup":
                applyDetails(currency, summary -> summary.accountOrGroup = value);
                break;
            case "IssuerOptionValue":
                applyDetails(currency, summary -> summary.issuerOptionValue = new BigDecimal(value));
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + tag);

        }
    }

    //region Getters

    public String getAccountType() {
        return accountType;
    }

    public BigDecimal getNetLiquidation() {
        return netLiquidation;
    }

    public BigDecimal getTotalCashValue() {
        return totalCashValue;
    }

    public BigDecimal getSettledCash() {
        return settledCash;
    }

    public BigDecimal getBuyingPower() {
        return buyingPower;
    }

    public BigDecimal getEquityWithLoanValue() {
        return equityWithLoanValue;
    }

    public BigDecimal getPreviousEquityWithLoanValue() {
        return previousEquityWithLoanValue;
    }

    public BigDecimal getGrossPositionValue() {
        return grossPositionValue;
    }

    public BigDecimal getRegTEquity() {
        return regTEquity;
    }

    public BigDecimal getRegTMargin() {
        return regTMargin;
    }

    public BigDecimal getSma() {
        return sma;
    }

    public BigDecimal getInitMarginReq() {
        return initMarginReq;
    }

    public BigDecimal getMaintMarginReq() {
        return maintMarginReq;
    }

    public BigDecimal getAvailableFunds() {
        return availableFunds;
    }

    public BigDecimal getExcessLiquidity() {
        return excessLiquidity;
    }

    public BigDecimal getCushion() {
        return cushion;
    }

    public BigDecimal getFullInitMarginReq() {
        return fullInitMarginReq;
    }

    public BigDecimal getFullMaintMarginReq() {
        return fullMaintMarginReq;
    }

    public BigDecimal getFullAvailableFunds() {
        return fullAvailableFunds;
    }

    public BigDecimal getFullExcessLiquidity() {
        return fullExcessLiquidity;
    }

    public Date getLookAheadNextChange() {
        return lookAheadNextChange;
    }

    public BigDecimal getLookAheadInitMarginReq() {
        return lookAheadInitMarginReq;
    }

    public BigDecimal getLookAheadMaintMarginReq() {
        return lookAheadMaintMarginReq;
    }

    public BigDecimal getLookAheadAvailableFunds() {
        return lookAheadAvailableFunds;
    }

    public BigDecimal getLookAheadExcessLiquidity() {
        return lookAheadExcessLiquidity;
    }

    public int getHighestSeverity() {
        return highestSeverity;
    }

    public int getDayTradesRemaining() {
        return dayTradesRemaining;
    }

    public int getLeverage() {
        return leverage;
    }

    //endregion

    public Map<String, DetailsPerCurrency> getDetails() {
        return details;
    }

    private <T> void shouldBeNull(T member, Runnable runnable) throws Exception {

        if (member != null) {
            throw new Exception("Value is overwritten");
        }
        runnable.run();
    }

    private void applyDetails(String currency, Consumer<DetailsPerCurrency> applyValue) {
        DetailsPerCurrency summary = details.computeIfAbsent(currency, key -> new DetailsPerCurrency());
        applyValue.accept(summary);
    }

    public class DetailsPerCurrency {
        private String currency;
        private String realCurrency;
        private BigDecimal cashBalance;
        private BigDecimal totalCashBalance;
        private BigDecimal accruedCash;
        private BigDecimal stockMarketValue;
        private BigDecimal optionMarketValue;
        private BigDecimal futureOptionValue;
        private BigDecimal futuresPNL;
        private BigDecimal netLiquidationByCurrency;
        private BigDecimal unrealizedPnL;
        private BigDecimal realizedPnL;
        private BigDecimal exchangeRate;
        private BigDecimal fundValue;
        private BigDecimal netDividend;
        private BigDecimal mutualFundValue;
        private BigDecimal moneyMarketFundValue;
        private BigDecimal corporateBondValue;
        private BigDecimal tBondValue;
        private BigDecimal tBillValue;
        private BigDecimal warrantValue;
        private BigDecimal fxCashBalance;
        private String accountOrGroup;
        private BigDecimal issuerOptionValue;

        public String getCurrency() {
            return currency;
        }

        public String getRealCurrency() {
            return realCurrency;
        }

        public BigDecimal getCashBalance() {
            return cashBalance;
        }

        public BigDecimal getTotalCashBalance() {
            return totalCashBalance;
        }

        public BigDecimal getAccruedCash() {
            return accruedCash;
        }

        public BigDecimal getStockMarketValue() {
            return stockMarketValue;
        }

        public BigDecimal getOptionMarketValue() {
            return optionMarketValue;
        }

        public BigDecimal getFutureOptionValue() {
            return futureOptionValue;
        }

        public BigDecimal getFuturesPNL() {
            return futuresPNL;
        }

        public BigDecimal getNetLiquidationByCurrency() {
            return netLiquidationByCurrency;
        }

        public BigDecimal getUnrealizedPnL() {
            return unrealizedPnL;
        }

        public BigDecimal getRealizedPnL() {
            return realizedPnL;
        }

        public BigDecimal getExchangeRate() {
            return exchangeRate;
        }

        public BigDecimal getFundValue() {
            return fundValue;
        }

        public BigDecimal getNetDividend() {
            return netDividend;
        }

        public BigDecimal getMutualFundValue() {
            return mutualFundValue;
        }

        public BigDecimal getMoneyMarketFundValue() {
            return moneyMarketFundValue;
        }

        public BigDecimal getCorporateBondValue() {
            return corporateBondValue;
        }

        public BigDecimal gettBondValue() {
            return tBondValue;
        }

        public BigDecimal gettBillValue() {
            return tBillValue;
        }

        public BigDecimal getWarrantValue() {
            return warrantValue;
        }

        public BigDecimal getFxCashBalance() {
            return fxCashBalance;
        }

        public String getAccountOrGroup() {
            return accountOrGroup;
        }

        public BigDecimal getIssuerOptionValue() {
            return issuerOptionValue;
        }
    }
}
