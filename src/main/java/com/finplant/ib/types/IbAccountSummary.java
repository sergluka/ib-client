package com.finplant.ib.types;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

// TODO: Refactor
public class IbAccountSummary {

    private String accountType;
    private BigDecimal netLiquidation;
    private BigDecimal totalCashValue;
    private BigDecimal settledCash;
    private BigDecimal accruedCash;
    private BigDecimal buyingPower;
    private BigDecimal equityWithLoanValue;
    private BigDecimal previousEquityWithLoanValue;
    private BigDecimal grossPositionValue;
    private BigDecimal regTEquity;
    private BigDecimal regTMargin;
    private BigDecimal SMA;
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
    private int highestSeverity;
    private int dayTradesRemaining;
    private int leverage;

    private final Map<String, DetailsPerCurrency> details = new HashMap<>();

    public void update(String account, String tag, String value, String currency) {

        if (!account.equals("All")) {
            switch (tag) {
                case "AccountType":
                    accountType = value;
                    break;
                case "NetLiquidation":
                    netLiquidation = new BigDecimal(value);
                    break;
                case "TotalCashValue":
                    totalCashValue = new BigDecimal(value);
                    break;
                case "SettledCash":
                    settledCash = new BigDecimal(value);
                    break;
                case "AccruedCash":
                    accruedCash = new BigDecimal(value);
                    break;
                case "BuyingPower":
                    buyingPower = new BigDecimal(value);
                    break;
                case "EquityWithLoanValue":
                    equityWithLoanValue = new BigDecimal(value);
                    break;
                case "PreviousEquityWithLoanValue":
                    previousEquityWithLoanValue = new BigDecimal(value);
                    break;
                case "GrossPositionValue":
                    grossPositionValue = new BigDecimal(value);
                    break;
                case "RegTEquity":
                    regTEquity = new BigDecimal(value);
                    break;
                case "RegTMargin":
                    regTMargin = new BigDecimal(value);
                    break;
                case "SMA":
                    SMA = new BigDecimal(value);
                    break;
                case "InitMarginReq":
                    initMarginReq = new BigDecimal(value);
                    break;
                case "MaintMarginReq":
                    maintMarginReq = new BigDecimal(value);
                    break;
                case "AvailableFunds":
                    availableFunds = new BigDecimal(value);
                    break;
                case "ExcessLiquidity":
                    excessLiquidity = new BigDecimal(value);
                    break;
                case "Cushion":
                    cushion = new BigDecimal(value);
                    break;
                case "FullInitMarginReq":
                    fullInitMarginReq = new BigDecimal(value);
                    break;
                case "FullMaintMarginReq":
                    fullMaintMarginReq = new BigDecimal(value);
                    break;
                case "FullAvailableFunds":
                    fullAvailableFunds = new BigDecimal(value);
                    break;
                case "FullExcessLiquidity":
                    fullExcessLiquidity = new BigDecimal(value);
                    break;
                case "LookAheadNextChange":
                    lookAheadNextChange = new Date(Integer.valueOf(value) * 1000L);
                    break;
                case "LookAheadInitMarginReq":
                    lookAheadInitMarginReq = new BigDecimal(value);
                    break;
                case "LookAheadMaintMarginReq":
                    lookAheadMaintMarginReq = new BigDecimal(value);
                    break;
                case "LookAheadAvailableFunds":
                    lookAheadAvailableFunds = new BigDecimal(value);
                    break;
                case "LookAheadExcessLiquidity":
                    lookAheadExcessLiquidity = new BigDecimal(value);
                    break;
                case "HighestSeverity":
                    highestSeverity = Integer.valueOf(value);
                    break;
                case "DayTradesRemaining":
                    dayTradesRemaining = Integer.valueOf(value);
                    break;
                case "Leverage":
                    leverage = Integer.valueOf(value);
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + tag);
            }
        } else {
            DetailsPerCurrency summary = details.computeIfAbsent(currency, key -> new DetailsPerCurrency());

            switch (tag) {
                case "Currency":
                case "RealCurrency":
                    break;
                case "CashBalance":
                    summary.cashBalance = new BigDecimal(value);
                    break;
                case "TotalCashBalance":
                    summary.totalCashBalance = new BigDecimal(value);
                    break;
                case "AccruedCash":
                    summary.accruedCash = new BigDecimal(value);
                    break;
                case "StockMarketValue":
                    summary.stockMarketValue = new BigDecimal(value);
                    break;
                case "OptionMarketValue":
                    summary.optionMarketValue = new BigDecimal(value);
                    break;
                case "FutureOptionValue":
                    summary.futureOptionValue = new BigDecimal(value);
                    break;
                case "FuturesPNL":
                    summary.futuresPNL = new BigDecimal(value);
                    break;
                case "NetLiquidationByCurrency":
                    summary.netLiquidationByCurrency = new BigDecimal(value);
                    break;
                case "UnrealizedPnL":
                    summary.unrealizedPnL = new BigDecimal(value);
                    break;
                case "RealizedPnL":
                    summary.realizedPnL = new BigDecimal(value);
                    break;
                case "ExchangeRate":
                    summary.exchangeRate = new BigDecimal(value);
                    break;
                case "FundValue":
                    summary.fundValue = new BigDecimal(value);
                    break;
                case "NetDividend":
                    summary.netDividend = new BigDecimal(value);
                    break;
                case "MutualFundValue":
                    summary.mutualFundValue = new BigDecimal(value);
                    break;
                case "MoneyMarketFundValue":
                    summary.moneyMarketFundValue = new BigDecimal(value);
                    break;
                case "CorporateBondValue":
                    summary.corporateBondValue = new BigDecimal(value);
                    break;
                case "TBondValue":
                    summary.tBondValue = new BigDecimal(value);
                    break;
                case "TBillValue":
                    summary.tBillValue = new BigDecimal(value);
                    break;
                case "WarrantValue":
                    summary.warrantValue = new BigDecimal(value);
                    break;
                case "FxCashBalance":
                    summary.fxCashBalance = new BigDecimal(value);
                    break;
                case "AccountOrGroup":
                    summary.accountOrGroup = value;
                    break;
                case "IssuerOptionValue":
                    summary.issuerOptionValue = new BigDecimal(value);
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + tag);

            }
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

    public BigDecimal getAccruedCash() {
        return accruedCash;
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

    public BigDecimal getSMA() {
        return SMA;
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

    public class DetailsPerCurrency {
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
