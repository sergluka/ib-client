package lv.sergluka.tws.impl;

import com.ib.client.*;
import lv.sergluka.tws.impl.sender.RequestRepository;
import lv.sergluka.tws.impl.types.TwsPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class Wrapper implements EWrapper {

    private static final Logger log = LoggerFactory.getLogger(Wrapper.class);

    private RequestRepository requests;

    public Wrapper() {
    }

    protected void setRequests(final RequestRepository requests) {
        this.requests = requests;
    }

    @Override
    public void connectAck() {
        requests.confirmAndRemove(RequestRepository.Event.REQ_CONNECT, null, null);
    }

    @Override
    public void orderStatus(int orderId, String status, double filled, double remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld, double mktCapPrice) {
    }

    @Override
    public void openOrder(final int orderId, final Contract contract, final Order order, final OrderState state) {
        log.info("openOrder: requestId={}, contract={}, order={}, orderState={}",
                orderId, contract.symbol(), order.orderId(), state.status());
        requests.confirmAndRemove(RequestRepository.Event.REQ_ORDER_PLACE, orderId, state);
    }

    @Override
    public void contractDetails(final int reqId, final ContractDetails contractDetails) {
        requests.addToList(RequestRepository.Event.REQ_CONTRACT_DETAIL, reqId, contractDetails);
    }

    @Override
    public void contractDetailsEnd(final int reqId) {
        requests.confirmAndRemove(RequestRepository.Event.REQ_CONTRACT_DETAIL, reqId, null);
    }

    @Override
    public void currentTime(final long time) {
        requests.confirmAndRemove(RequestRepository.Event.REQ_CURRENT_TIME, null, time);
    }

    @Override
    public void position(final String account, final Contract contract, final double pos, final double avgCost) {
    }

    @Override
    public void positionEnd() {
    }

    @Override
    public void nextValidId(int id) {
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
    public void tickPrice(int tickerId, int field, double price, TickAttr attrib) {
        log.debug("tickPrice: NOT IMPLEMENTED");
    }

    @Override
    public void tickSize(final int tickerId, final int field, final int size) {
        log.debug("tickSize: NOT IMPLEMENTED");
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
        log.debug("tickOptionComputation: NOT IMPLEMENTED");
    }

    @Override
    public void tickGeneric(final int tickerId, final int tickType, final double value) {
        log.debug("tickGeneric: NOT IMPLEMENTED");
    }

    @Override
    public void tickString(final int tickerId, final int tickType, final String value) {
        log.debug("tickString: NOT IMPLEMENTED");
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
        log.debug("dividendsToLastTradeDate: NOT IMPLEMENTED");
    }

    @Override
    public void openOrderEnd() {
        log.info("openOrderEnd: NOT IMPLEMENTED");
    }

    @Override
    public void updateAccountValue(final String key,
                                   final String value,
                                   final String currency,
                                   final String accountName) {
        log.debug("updateAccountValue: NOT IMPLEMENTED");
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
        log.debug("updatePortfolio: NOT IMPLEMENTED");
    }

    @Override
    public void updateAccountTime(final String timeStamp) {
        log.debug("updateAccountTime: NOT IMPLEMENTED");
    }

    @Override
    public void accountDownloadEnd(final String accountName) {
        log.debug("accountDownloadEnd: NOT IMPLEMENTED");
    }

    @Override
    public void bondContractDetails(final int reqId, final ContractDetails contractDetails) {
        log.debug("bondContractDetails: NOT IMPLEMENTED");
    }

    @Override
    public void execDetails(final int reqId, final Contract contract, final Execution execution) {
        log.debug("execDetails: NOT IMPLEMENTED");
    }

    @Override
    public void execDetailsEnd(final int reqId) {
        log.debug("execDetailsEnd: NOT IMPLEMENTED");
    }

    @Override
    public void updateMktDepth(final int tickerId,
                               final int position,
                               final int operation,
                               final int side,
                               final double price,
                               final int size) {
        log.debug("updateMktDepth: NOT IMPLEMENTED");
    }

    @Override
    public void updateMktDepthL2(final int tickerId,
                                 final int position,
                                 final String marketMaker,
                                 final int operation,
                                 final int side,
                                 final double price,
                                 final int size) {
        log.debug("updateMktDepthL2: NOT IMPLEMENTED");
    }

    @Override
    public void updateNewsBulletin(final int msgId,
                                   final int msgType,
                                   final String message,
                                   final String origExchange) {
        log.debug("updateNewsBulletin: NOT IMPLEMENTED");
    }

    @Override
    public void managedAccounts(final String accountsList) {
        log.debug("managedAccounts: NOT IMPLEMENTED");
    }

    @Override
    public void receiveFA(final int faDataType, final String xml) {
        log.debug("receiveFA: NOT IMPLEMENTED");
    }

    @Override
    public void historicalData(int reqId, Bar bar) {
        log.debug("historicalData: NOT IMPLEMENTED");
    }

    @Override
    public void scannerParameters(final String xml) {
        log.debug("scannerParameters: NOT IMPLEMENTED");
    }

    @Override
    public void scannerData(final int reqId,
                            final int rank,
                            final ContractDetails contractDetails,
                            final String distance,
                            final String benchmark,
                            final String projection,
                            final String legsStr) {

        log.debug("scannerData: NOT IMPLEMENTED");
    }

    @Override
    public void scannerDataEnd(final int reqId) {
        log.debug("scannerDataEnd: NOT IMPLEMENTED");
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
        log.debug("realtimeBar: NOT IMPLEMENTED");
    }

    @Override
    public void fundamentalData(final int reqId, final String data) {
        log.debug("fundamentalData: NOT IMPLEMENTED");
    }

    @Override
    public void deltaNeutralValidation(final int reqId, final DeltaNeutralContract underComp) {
        log.debug("deltaNeutralValidation: NOT IMPLEMENTED");
    }

    @Override
    public void tickSnapshotEnd(final int reqId) {
        log.debug("tickSnapshotEnd: NOT IMPLEMENTED");
    }

    @Override
    public void marketDataType(final int reqId, final int marketDataType) {
        log.debug("marketDataType: NOT IMPLEMENTED");
    }

    @Override
    public void commissionReport(final CommissionReport commissionReport) {
        log.debug("commissionReport: NOT IMPLEMENTED");
    }

    @Override
    public void accountSummary(final int reqId,
                               final String account,
                               final String tag,
                               final String value,
                               final String currency) {
        log.debug("accountSummary: NOT IMPLEMENTED");
    }

    @Override
    public void accountSummaryEnd(final int reqId) {
        log.debug("accountSummaryEnd: NOT IMPLEMENTED");
    }

    @Override
    public void verifyMessageAPI(final String apiData) {
        log.debug("verifyMessageAPI: NOT IMPLEMENTED");
    }

    @Override
    public void verifyCompleted(final boolean isSuccessful, final String errorText) {
        log.debug("verifyCompleted: NOT IMPLEMENTED");
    }

    @Override
    public void verifyAndAuthMessageAPI(final String apiData, final String xyzChallange) {
        log.debug("verifyAndAuthMessageAPI: NOT IMPLEMENTED");
    }

    @Override
    public void verifyAndAuthCompleted(final boolean isSuccessful, final String errorText) {
        log.debug("verifyAndAuthCompleted: NOT IMPLEMENTED");
    }

    @Override
    public void displayGroupList(final int reqId, final String groups) {
        log.debug("displayGroupList: NOT IMPLEMENTED");
    }

    @Override
    public void displayGroupUpdated(final int reqId, final String contractInfo) {
        log.debug("displayGroupUpdated: NOT IMPLEMENTED");
    }

    @Override
    public void positionMulti(final int reqId,
                              final String account,
                              final String modelCode,
                              final Contract contract,
                              final double pos,
                              final double avgCost) {
        log.debug("positionMulti: NOT IMPLEMENTED");
    }

    @Override
    public void positionMultiEnd(final int reqId) {
        log.debug("positionMultiEnd: NOT IMPLEMENTED");
    }

    @Override
    public void accountUpdateMulti(final int reqId,
                                   final String account,
                                   final String modelCode,
                                   final String key,
                                   final String value,
                                   final String currency) {
        log.debug("accountUpdateMulti: NOT IMPLEMENTED");
    }

    @Override
    public void accountUpdateMultiEnd(final int reqId) {
        log.debug("accountUpdateMultiEnd: NOT IMPLEMENTED");
    }

    @Override
    public void securityDefinitionOptionalParameter(final int reqId,
                                                    final String exchange,
                                                    final int underlyingConId,
                                                    final String tradingClass,
                                                    final String multiplier,
                                                    final Set<String> expirations,
                                                    final Set<Double> strikes) {
        log.debug("securityDefinitionOptionalParameter: NOT IMPLEMENTED");
    }

    @Override
    public void securityDefinitionOptionalParameterEnd(final int reqId) {
        log.debug("securityDefinitionOptionalParameterEnd: NOT IMPLEMENTED");
    }

    @Override
    public void softDollarTiers(final int reqId, final SoftDollarTier[] tiers) {
        log.debug("softDollarTiers: NOT IMPLEMENTED");
    }

    @Override
    public void familyCodes(FamilyCode[] familyCodes) {
        log.debug("familyCodes: NOT IMPLEMENTED");
    }

    @Override
    public void symbolSamples(int reqId, ContractDescription[] contractDescriptions) {
        log.debug("symbolSamples: NOT IMPLEMENTED");
    }

    @Override
    public void historicalDataEnd(int reqId, String startDateStr, String endDateStr) {
        log.debug("historicalDataEnd: NOT IMPLEMENTED");
    }

    @Override
    public void mktDepthExchanges(DepthMktDataDescription[] depthMktDataDescriptions) {
        log.debug("mktDepthExchanges: NOT IMPLEMENTED");
    }

    @Override
    public void tickNews(int tickerId, long timeStamp, String providerCode, String articleId, String headline, String extraData) {
        log.debug("tickNews: NOT IMPLEMENTED");
    }

    @Override
    public void smartComponents(int reqId, Map<Integer, Map.Entry<String, Character>> theMap) {
        log.debug("smartComponents: NOT IMPLEMENTED");
    }

    @Override
    public void tickReqParams(int tickerId, double minTick, String bboExchange, int snapshotPermissions) {
        log.debug("tickReqParams: NOT IMPLEMENTED");
    }

    @Override
    public void newsProviders(NewsProvider[] newsProviders) {
        log.debug("newsProviders: NOT IMPLEMENTED");
    }

    @Override
    public void newsArticle(int requestId, int articleType, String articleText) {
        log.debug("newsArticle: NOT IMPLEMENTED");
    }

    @Override
    public void historicalNews(int requestId, String time, String providerCode, String articleId, String headline) {
        log.debug("historicalNews: NOT IMPLEMENTED");
    }

    @Override
    public void historicalNewsEnd(int requestId, boolean hasMore) {
        log.debug("historicalNewsEnd: NOT IMPLEMENTED");
    }

    @Override
    public void headTimestamp(int reqId, String headTimestamp) {
        log.debug("headTimestamp: NOT IMPLEMENTED");
    }

    @Override
    public void histogramData(int reqId, List<HistogramEntry> items) {
        log.debug("histogramData: NOT IMPLEMENTED");
    }

    @Override
    public void historicalDataUpdate(int reqId, Bar bar) {
        log.debug("historicalDataUpdate: NOT IMPLEMENTED");
    }

    @Override
    public void rerouteMktDataReq(int reqId, int conId, String exchange) {
        log.debug("rerouteMktDataReq: NOT IMPLEMENTED");
    }

    @Override
    public void rerouteMktDepthReq(int reqId, int conId, String exchange) {
        log.debug("rerouteMktDepthReq: NOT IMPLEMENTED");
    }

    @Override
    public void marketRule(int marketRuleId, PriceIncrement[] priceIncrements) {
        log.debug("marketRule: NOT IMPLEMENTED");
    }

    @Override
    public void pnl(int reqId, double dailyPnL, double unrealizedPnL, double realizedPnL) {
        log.debug("pnl: NOT IMPLEMENTED");
    }

    @Override
    public void pnlSingle(int reqId, int pos, double dailyPnL, double unrealizedPnL, double realizedPnL, double value) {
        log.debug("pnlSingle: NOT IMPLEMENTED");
    }

    @Override
    public void historicalTicks(int reqId, List<HistoricalTick> ticks, boolean done) {
        log.debug("historicalTicks: NOT IMPLEMENTED");
    }

    @Override
    public void historicalTicksBidAsk(int reqId, List<HistoricalTickBidAsk> ticks, boolean done) {
        log.debug("historicalTicksBidAsk: NOT IMPLEMENTED");
    }

    @Override
    public void historicalTicksLast(int reqId, List<HistoricalTickLast> ticks, boolean done) {
        log.debug("historicalTicksLast: NOT IMPLEMENTED");
    }
}
