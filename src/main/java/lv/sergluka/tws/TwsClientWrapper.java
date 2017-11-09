package lv.sergluka.tws;

import com.ib.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class TwsClientWrapper implements EWrapper {

    private static final Logger log = LoggerFactory.getLogger(TwsClientWrapper.class);

    @Override
    public void tickPrice(int tickerId, int field, double price, TickAttr attrib) {
        log.debug("tickPrice");
    }

    @Override
    public void tickSize(final int tickerId, final int field, final int size) {
        log.debug("tickSize");
    }

    @Override
    public void tickOptionComputation(final int tickerId,
                                      final int field,
                                      final double impliedVol,
                                      final double delta,
                                      final double optPrice,
                                      final double pvDividend,
                                      final double gamma,
                                      final double vega,
                                      final double theta,
                                      final double undPrice) {
        log.debug("tickOptionComputation");
    }

    @Override
    public void tickGeneric(final int tickerId, final int tickType, final double value) {
        log.debug("tickGeneric");
    }

    @Override
    public void tickString(final int tickerId, final int tickType, final String value) {
        log.debug("tickString");
    }

    @Override
    public void tickEFP(final int tickerId,
                        final int tickType,
                        final double basisPoints,
                        final String formattedBasisPoints,
                        final double impliedFuture,
                        final int holdDays,
                        final String futureLastTradeDate,
                        final double dividendImpact,
                        final double dividendsToLastTradeDate) {
        log.debug("tickEFP");
    }

    @Override
    public void orderStatus(int orderId, String status, double filled, double remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld, double mktCapPrice) {
        log.info("orderStatus: orderId={}, status={}, filled={}, remaining={}, avgFillPrice={}, permId={}, " +
                        "parentId={}, lastFillPrice={}, clientId={}, whyHeld={}, mktCapPrice={}",
                orderId, status, filled, remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld,
                mktCapPrice);
    }

    @Override
    public void openOrder(final int orderId, final Contract contract, final Order order, final OrderState orderState) {
        log.info("openOrder: orderId={}, contract={}, order={}, orderState={}", orderId, contract, order, orderState);
    }

    @Override
    public void openOrderEnd() {
        log.info("openOrderEnd");
    }

    @Override
    public void updateAccountValue(final String key,
                                   final String value,
                                   final String currency,
                                   final String accountName) {
        log.debug("updateAccountValue");
    }

    @Override
    public void updatePortfolio(final Contract contract,
                                final double position,
                                final double marketPrice,
                                final double marketValue,
                                final double averageCost,
                                final double unrealizedPNL,
                                final double realizedPNL,
                                final String accountName) {
        log.debug("updatePortfolio");
    }

    @Override
    public void updateAccountTime(final String timeStamp) {
        log.debug("updateAccountTime");
    }

    @Override
    public void accountDownloadEnd(final String accountName) {
        log.debug("accountDownloadEnd");
    }

    @Override
    public void nextValidId(final int orderId) {
        log.info("next ID: {}", orderId);
    }

    @Override
    public void contractDetails(final int reqId, final ContractDetails contractDetails) {
        log.debug("contractDetails");
    }

    @Override
    public void bondContractDetails(final int reqId, final ContractDetails contractDetails) {
        log.debug("bondContractDetails");
    }

    @Override
    public void contractDetailsEnd(final int reqId) {
        log.debug("contractDetailsEnd");
    }

    @Override
    public void execDetails(final int reqId, final Contract contract, final Execution execution) {
        log.debug("execDetails");
    }

    @Override
    public void execDetailsEnd(final int reqId) {
        log.debug("execDetailsEnd");
    }

    @Override
    public void updateMktDepth(final int tickerId,
                               final int position,
                               final int operation,
                               final int side,
                               final double price,
                               final int size) {
        log.debug("updateMktDepth");
    }

    @Override
    public void updateMktDepthL2(final int tickerId,
                                 final int position,
                                 final String marketMaker,
                                 final int operation,
                                 final int side,
                                 final double price,
                                 final int size) {
        log.debug("public");
    }

    @Override
    public void updateNewsBulletin(final int msgId,
                                   final int msgType,
                                   final String message,
                                   final String origExchange) {
        log.debug("public");
    }

    @Override
    public void managedAccounts(final String accountsList) {
        log.debug("managedAccounts");
    }

    @Override
    public void receiveFA(final int faDataType, final String xml) {
        log.debug("receiveFA");
    }

    @Override
    public void historicalData(int reqId, Bar bar) {
        log.debug("historicalData");
    }

    @Override
    public void scannerParameters(final String xml) {
        log.debug("scannerParameters");
    }

    @Override
    public void scannerData(final int reqId,
                            final int rank,
                            final ContractDetails contractDetails,
                            final String distance,
                            final String benchmark,
                            final String projection,
                            final String legsStr) {

    }

    @Override
    public void scannerDataEnd(final int reqId) {
        log.debug("scannerDataEnd");
    }

    @Override
    public void realtimeBar(final int reqId,
                            final long time,
                            final double open,
                            final double high,
                            final double low,
                            final double close,
                            final long volume,
                            final double wap,
                            final int count) {
        log.debug("realtimeBar");
    }

    @Override
    public void currentTime(final long time) {
        log.debug("currentTime");
    }

    @Override
    public void fundamentalData(final int reqId, final String data) {
        log.debug("fundamentalData");
    }

    @Override
    public void deltaNeutralValidation(final int reqId, final DeltaNeutralContract underComp) {
        log.debug("deltaNeutralValidation");
    }

    @Override
    public void tickSnapshotEnd(final int reqId) {
        log.debug("tickSnapshotEnd");
    }

    @Override
    public void marketDataType(final int reqId, final int marketDataType) {
        log.debug("marketDataType");
    }

    @Override
    public void commissionReport(final CommissionReport commissionReport) {
        log.debug("commissionReport");
    }

    @Override
    public void position(final String account, final Contract contract, final double pos, final double avgCost) {
        log.debug("position");
    }

    @Override
    public void positionEnd() {
        log.debug("positionEnd");
    }

    @Override
    public void accountSummary(final int reqId,
                               final String account,
                               final String tag,
                               final String value,
                               final String currency) {
        log.debug("accountSummary");
    }

    @Override
    public void accountSummaryEnd(final int reqId) {
        log.debug("accountSummaryEnd");
    }

    @Override
    public void verifyMessageAPI(final String apiData) {
        log.debug("verifyMessageAPI");
    }

    @Override
    public void verifyCompleted(final boolean isSuccessful, final String errorText) {
        log.debug("verifyCompleted");
    }

    @Override
    public void verifyAndAuthMessageAPI(final String apiData, final String xyzChallange) {
        log.debug("verifyAndAuthMessageAPI");
    }

    @Override
    public void verifyAndAuthCompleted(final boolean isSuccessful, final String errorText) {
        log.debug("verifyAndAuthCompleted");
    }

    @Override
    public void displayGroupList(final int reqId, final String groups) {
        log.debug("displayGroupList");
    }

    @Override
    public void displayGroupUpdated(final int reqId, final String contractInfo) {
        log.debug("displayGroupUpdated");
    }

    @Override
    public void error(final Exception e) {
    }

    @Override
    public void error(final String str) {
    }

    @Override
    public void error(final int id, final int errorCode, final String errorMsg) {
    }

    @Override
    public void connectionClosed() {
    }

    @Override
    public void connectAck() {
    }

    @Override
    public void positionMulti(final int reqId,
                              final String account,
                              final String modelCode,
                              final Contract contract,
                              final double pos,
                              final double avgCost) {
        log.debug("positionMulti");
    }

    @Override
    public void positionMultiEnd(final int reqId) {
        log.debug("positionMultiEnd");
    }

    @Override
    public void accountUpdateMulti(final int reqId,
                                   final String account,
                                   final String modelCode,
                                   final String key,
                                   final String value,
                                   final String currency) {
        log.debug("accountUpdateMulti");
    }

    @Override
    public void accountUpdateMultiEnd(final int reqId) {
        log.debug("accountUpdateMultiEnd");
    }

    @Override
    public void securityDefinitionOptionalParameter(final int reqId,
                                                    final String exchange,
                                                    final int underlyingConId,
                                                    final String tradingClass,
                                                    final String multiplier,
                                                    final Set<String> expirations,
                                                    final Set<Double> strikes) {
        log.debug("securityDefinitionOptionalParameter");
    }

    @Override
    public void securityDefinitionOptionalParameterEnd(final int reqId) {
        log.debug("securityDefinitionOptionalParameterEnd");
    }

    @Override
    public void softDollarTiers(final int reqId, final SoftDollarTier[] tiers) {
        log.debug("softDollarTiers");
    }

    @Override
    public void familyCodes(FamilyCode[] familyCodes) {

    }

    @Override
    public void symbolSamples(int reqId, ContractDescription[] contractDescriptions) {

    }

    @Override
    public void historicalDataEnd(int reqId, String startDateStr, String endDateStr) {

    }

    @Override
    public void mktDepthExchanges(DepthMktDataDescription[] depthMktDataDescriptions) {

    }

    @Override
    public void tickNews(int tickerId, long timeStamp, String providerCode, String articleId, String headline, String extraData) {

    }

    @Override
    public void smartComponents(int reqId, Map<Integer, Map.Entry<String, Character>> theMap) {

    }

    @Override
    public void tickReqParams(int tickerId, double minTick, String bboExchange, int snapshotPermissions) {

    }

    @Override
    public void newsProviders(NewsProvider[] newsProviders) {

    }

    @Override
    public void newsArticle(int requestId, int articleType, String articleText) {

    }

    @Override
    public void historicalNews(int requestId, String time, String providerCode, String articleId, String headline) {

    }

    @Override
    public void historicalNewsEnd(int requestId, boolean hasMore) {

    }

    @Override
    public void headTimestamp(int reqId, String headTimestamp) {

    }

    @Override
    public void histogramData(int reqId, List<HistogramEntry> items) {

    }

    @Override
    public void historicalDataUpdate(int reqId, Bar bar) {

    }

    @Override
    public void rerouteMktDataReq(int reqId, int conId, String exchange) {

    }

    @Override
    public void rerouteMktDepthReq(int reqId, int conId, String exchange) {

    }

    @Override
    public void marketRule(int marketRuleId, PriceIncrement[] priceIncrements) {

    }

    @Override
    public void pnl(int reqId, double dailyPnL, double unrealizedPnL, double realizedPnL) {

    }

    @Override
    public void pnlSingle(int reqId, int pos, double dailyPnL, double unrealizedPnL, double realizedPnL, double value) {

    }

    @Override
    public void historicalTicks(int reqId, List<HistoricalTick> ticks, boolean done) {

    }

    @Override
    public void historicalTicksBidAsk(int reqId, List<HistoricalTickBidAsk> ticks, boolean done) {

    }

    @Override
    public void historicalTicksLast(int reqId, List<HistoricalTickLast> ticks, boolean done) {

    }
}
