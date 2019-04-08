package com.finplant.ib.types;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@SuppressWarnings("unused")
public interface IbTick {
    Integer getBidSize();
    Integer getAskSize();
    Integer getLastSize();
    Integer getVolume();
    Integer getVolumeAverage();
    Integer getOptionCallOpenInterest();
    Integer getOptionPutOpenInterest();
    Integer getOptionCallVolume();
    Integer getOptionPutVolume();
    Integer getActionVolume();
    Integer getActionImbalance();
    Integer getRegulatoryImbalance();
    Integer getShortTermVolume3Min();
    Integer getShortTermVolume5Min();
    Integer getShortTermVolume10Min();
    Integer getDelayedBidSize();
    Integer getDelayedAskSize();
    Integer getDelayedLastSize();
    Integer getDelayedVolume();
    Integer getFuturesOpenInterest();

    BigDecimal getBid();
    BigDecimal getAsk();
    BigDecimal getLastPrice();
    BigDecimal getHighPrice();
    BigDecimal getLowPrice();
    BigDecimal getClosePrice();
    BigDecimal getOpenTick();
    BigDecimal getLow13Weeks();
    BigDecimal getHigh13Weeks();
    BigDecimal getLow26Weeks();
    BigDecimal getHigh26Weeks();
    BigDecimal getLow52Weeks();
    BigDecimal getHigh52Weeks();
    BigDecimal getAuctionPrice();
    BigDecimal getMarkPrice();
    BigDecimal getBidYield();
    BigDecimal getAskYield();
    BigDecimal getLastYield();
    BigDecimal getLastRthTrade();
    BigDecimal getDelayedBid();
    BigDecimal getDelayedAsk();
    BigDecimal getDelayedLast();
    BigDecimal getDelayedHighPrice();
    BigDecimal getDelayedLowPrice();
    BigDecimal getDelayedClose();
    BigDecimal getDelayedOpen();
    BigDecimal getCreditmanMarkPrice();
    BigDecimal getCreditmanSlowMarkPrice();
    BigDecimal getDelayedBidOption();
    BigDecimal getDelayedAskOption();
    BigDecimal getDelayedLastOption();
    BigDecimal getDelayedModelOption();
    BigDecimal getOptionHistoricalVolatility();
    BigDecimal getOptionImpliedVolatility();
    BigDecimal getIndexFuturePremium();
    BigDecimal getShortable();
    BigDecimal getHalted();
    BigDecimal getTradeCount();
    BigDecimal getTradeRate();
    BigDecimal getVolumeRate();
    BigDecimal getRtHistoricalVolatility();

    String getBidExchange();
    String getAskExchange();
    String getLastTimestamp();
    String getRtVolume();
    String getIbDividends();
    String getNews();
    String getRtTradeVolume();
    String getLastExchange();
    String getLastRegulatoryTime();

    LocalDateTime getUpdateTime();
}
