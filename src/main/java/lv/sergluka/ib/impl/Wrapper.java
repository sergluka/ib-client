package lv.sergluka.ib.impl;

import com.google.common.base.Splitter;
import com.ib.client.*;

import lv.sergluka.ib.IbExceptions;
import lv.sergluka.ib.IdGenerator;
import lv.sergluka.ib.impl.cache.CacheRepository;
import lv.sergluka.ib.impl.connection.ConnectionMonitor;
import lv.sergluka.ib.impl.sender.RequestRepository;
import lv.sergluka.ib.impl.subscription.SubscriptionsRepository;
import lv.sergluka.ib.impl.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketException;
import java.util.*;

public class Wrapper implements EWrapper {

    private static final Logger log = LoggerFactory.getLogger(Wrapper.class);

    private RequestRepository requests;
    private Set<String> managedAccounts;

    private final TerminalErrorHandler errorHandler;

    private final ConnectionMonitor connectionMonitor;

    private final CacheRepository cache;
    private final SubscriptionsRepository subscriptions;
    private final IdGenerator idGenerator;
    private IbReader reader;
    private EClientSocket socket;

    public Wrapper(ConnectionMonitor connectionMonitor,
                   RequestRepository requests,
                   CacheRepository cache,
                   SubscriptionsRepository subscriptions,
                   IdGenerator idGenerator) {

        errorHandler = new TerminalErrorHandler(requests) {

            @Override
            void onError() {
                ConnectionMonitor.Status status = connectionMonitor.status();
                if (status == ConnectionMonitor.Status.DISCONNECTING ||
                        status == ConnectionMonitor.Status.DISCONNECTED) {
                    log.warn("Got error at disconnect. Ignoring");
                    return;
                }

                connectionMonitor.reconnect();
            }

            @Override
            void onFatalError() {
                connectionMonitor.disconnect();
            }
        };
        this.connectionMonitor = connectionMonitor;
        this.requests = requests;
        this.cache = cache;
        this.subscriptions = subscriptions;
        this.idGenerator = idGenerator;
    }

    public void setSocket(EClientSocket socket) {
        this.socket = socket;
    }

    public void setReader(IbReader reader) {
        this.reader = reader;
    }

    @Override
    public void connectAck() {
        log.info("Connection is opened. version: {}", socket.serverVersion());
    }

    @Override
    public void connectionClosed() {
        log.error("TWS closes the connection");
        connectionMonitor.reconnect();
    }

    @Override
    public void orderStatus(int orderId, String status, double filled, double remaining, double avgFillPrice,
                            int permId, int parentId, double lastFillPrice, int clientId, String whyHeld,
                            double mktCapPrice) {

        final IbOrderStatus twsStatus = new IbOrderStatus(orderId,
                                                          status,
                                                          filled,
                                                          remaining,
                                                          avgFillPrice,
                                                          permId,
                                                          parentId,
                                                          lastFillPrice,
                                                          clientId,
                                                          whyHeld,
                                                          mktCapPrice);

        log.debug("orderStatus: {}", twsStatus);

        if (cache.addNewStatus(twsStatus)) {

            log.info("New order status: {}", twsStatus);
            subscriptions.onNext(SubscriptionsRepository.EventType.EVENT_ORDER_STATUS, twsStatus, false);
        }
    }

    @Override
    public void openOrder(int orderId, Contract contract, Order order, OrderState state) {
        IbOrder twsOrder = new IbOrder(orderId, contract, order, state);

        log.debug("openOrder: requestId={}, contract={}, order={}, orderState={}",
                  orderId, contract.symbol(), order.orderId(), state.status());

        if (cache.addOrder(twsOrder)) {
            log.info("New order: requestId={}, contract={}, order={}, orderState={}",
                     orderId, contract.symbol(), order.orderId(), state.status());
            requests.onNextAndConfirm(RequestRepository.Event.REQ_ORDER_PLACE, orderId, twsOrder);
        }
    }

    @Override
    public void openOrderEnd() {
        requests.onNextAndConfirm(RequestRepository.Event.REQ_ORDER_LIST, null, cache.getOrders());
    }

    @Override
    public void position(String account, Contract contract, double pos, double avgCost) {
        log.info("Position change: {}/{},{}/{}", account, contract.conid(), contract.localSymbol(), pos);

        IbPosition position = new IbPosition(account, contract, pos, avgCost);
        cache.updatePosition(position);
        subscriptions.onNext(SubscriptionsRepository.EventType.EVENT_POSITION, position, true);
    }

    @Override
    public void positionEnd() {
        subscriptions.onNext(SubscriptionsRepository.EventType.EVENT_POSITION, IbPosition.COMPLETE, true);
    }

    @Override
    public void managedAccounts(String accountsList) {
        // Documentation said that connection fully established after `managedAccounts` and `nextValidId` called
        connectionMonitor.confirmConnection();

        log.debug("Managed accounts are: {}", accountsList);
        this.managedAccounts = new HashSet<>(Splitter.on(",").splitToList(accountsList));
    }

    @Override
    public void pnlSingle(int reqId, int pos, double dailyPnL, double unrealizedPnL, double realizedPnL, double value) {
        log.debug("pnlSingle: reqId={}, pos={}, dailyPnL={}, unrealizedPnL={}, realizedPnL={}, value={}",
                  reqId, pos, dailyPnL, unrealizedPnL, realizedPnL, value);

        Double dailyPnLObj = doubleToDouble("dailyPnL", dailyPnL);
        Double unrealizedPnLObj = doubleToDouble("unrealizedPnL", unrealizedPnL);
        Double realizedPnLObj = doubleToDouble("realizedPnL", realizedPnL);
        Double valueObj = doubleToDouble("value", value);

        IbPnl pnl = new IbPnl(pos, dailyPnLObj, unrealizedPnLObj, realizedPnLObj, valueObj);
        subscriptions.onNext(SubscriptionsRepository.EventType.EVENT_CONTRACT_PNL, reqId, pnl, true);
    }

    @Override
    public void pnl(int reqId, double dailyPnL, double unrealizedPnL, double realizedPnL) {
        log.trace("pnl: reqId={}, dailyPnL={}, unrealizedPnL={}, realizedPnL={}",
                  reqId, dailyPnL, unrealizedPnL, realizedPnL);

        IbPnl pnl = new IbPnl(null, dailyPnL, unrealizedPnL, realizedPnL, null);
        subscriptions.onNext(SubscriptionsRepository.EventType.EVENT_ACCOUNT_PNL, reqId, pnl, true);
    }

    @Override
    public void tickSize(int tickerId, int field, int value) {
        if (value == -1) {
            publishNoData(tickerId);
            return;
        }

        IbTick result = cache.updateTick(tickerId, (tick) -> tick.setIntValue(field, value));
        publishNewTick(tickerId, result);
    }

    @Override
    public void tickPrice(int tickerId, int field, double value, TickAttr attrib) {
        if (value == -1) {
            publishNoData(tickerId);
            return;
        }

        IbTick result = cache.updateTick(tickerId, (tick) -> tick.setPriceValue(field, value, attrib));
        publishNewTick(tickerId, result);
    }

    @Override
    public void tickString(int tickerId, int field, String value) {
        IbTick result = cache.updateTick(tickerId, (tick) -> tick.setStringValue(field, value));
        publishNewTick(tickerId, result);
    }

    @Override
    public void tickGeneric(int tickerId, int field, double value) {
        IbTick result = cache.updateTick(tickerId, (tick) -> tick.setGenericValue(field, value));
            publishNewTick(tickerId, result);
    }

    @Override
    public void tickSnapshotEnd(final int tickerId) {
        log.trace("tickSnapshotEnd({})", tickerId);

        IbTick tick = cache.getTick(tickerId);
        if (tick == null) {
            log.info("No ticks for ticker {}", tickerId);
            requests.onError(tickerId, new IbExceptions.NoTicks());
            return;
        }

        requests.onNextAndConfirm(RequestRepository.Event.REQ_MARKET_DATA, tickerId, tick);
    }

    @Override
    public void updatePortfolio(Contract contract,
                                double position,
                                double marketPrice,
                                double marketValue,
                                double averageCost,
                                double unrealizedPNL,
                                double realizedPNL,
                                String accountName) {

        Double positionObj = doubleToDouble("position", position);
        Double marketPriceObj = doubleToDouble("marketPrice", marketPrice);
        Double marketValueObj = doubleToDouble("marketValue", marketValue);
        Double averageCostObj = doubleToDouble("averageCost", averageCost);
        Double unrealizedPNLObj = doubleToDouble("unrealizedPNL", unrealizedPNL);
        Double realizedPNLObj = doubleToDouble("realizedPNL", realizedPNL);

        log.debug("updatePortfolio: contract={}, position={}, marketPrice={}, marketValue={}, averageCost={}, " +
                          "unrealizedPNL={}, realizedPNL={}, accountName={}",
                  contract, position, marketPrice, marketValue, averageCost, unrealizedPNL, realizedPNL, accountName);

        IbPortfolio portfolio = new IbPortfolio(contract, positionObj, marketPriceObj, marketValueObj, averageCostObj,
                                                unrealizedPNLObj, realizedPNLObj, accountName);

        cache.updatePortfolio(portfolio);
        subscriptions.onNext(SubscriptionsRepository.EventType.EVENT_PORTFOLIO, portfolio, true);
    }

    @Override
    public void accountDownloadEnd(String accountName) {
        subscriptions.onNext(SubscriptionsRepository.EventType.EVENT_PORTFOLIO, IbPortfolio.COMPLETE, true);
    }

    @Override
    public void error(final Exception e) {
        if (e instanceof SocketException) {

            final ConnectionMonitor.Status connectionStatus = connectionMonitor.status();

            if (connectionStatus == ConnectionMonitor.Status.DISCONNECTING ||
                    connectionStatus == ConnectionMonitor.Status.DISCONNECTED) {

                log.debug("Socket has been closed at shutdown");
                return;
            }

            log.warn("Connection lost");
            connectionMonitor.reconnect();
            return;
        }
        // Ugly TWS client library try to read from null stream even it doesn't created when TWS is unreachable
        else if (e instanceof NullPointerException && e.getStackTrace().length > 0) {
            StackTraceElement element = e.getStackTrace()[0];
            if (Objects.equals(element.getClassName(), "com.ib.client.EClientSocket") &&
                Objects.equals(element.getMethodName(), "readInt")) {

                log.debug("Ignoring error of uninitialized stream");
                return;
            }
        }

        log.error("TWS exception", e);
    }

    @Override
    public void error(final int id, final int code, final String message) {
        errorHandler.handle(id, code, message);
    }

    @Override
    public void nextValidId(int id) {
        log.debug("New request ID: {}", id);
        idGenerator.setOrderId(id);
    }

    private void publishNewTick(int tickerId, IbTick result) {
        subscriptions.onNext(SubscriptionsRepository.EventType.EVENT_MARKET_DATA, tickerId, result, false);
    }

    private void publishNoData(int tickerId) {
        subscriptions.onError(SubscriptionsRepository.EventType.EVENT_MARKET_DATA, tickerId, new IbExceptions.NoTicks());
    }

    @Override
    public void contractDetails(final int reqId, final ContractDetails contractDetails) {
        requests.onNext(RequestRepository.Event.REQ_CONTRACT_DETAIL, reqId, contractDetails);
    }

    @Override
    public void contractDetailsEnd(final int reqId) {
        requests.onComplete(RequestRepository.Event.REQ_CONTRACT_DETAIL, reqId);
    }

    @Override
    public void currentTime(final long time) {
        requests.onNextAndConfirm(RequestRepository.Event.REQ_CURRENT_TIME, null, time);
    }

    @Override
    public void error(final String str) {
        log.error("Shouldn't be there. err={}", str);
    }

    @Override
    public void positionMulti(final int reqId,
                              final String account,
                              final String modelCode,
                              final Contract contract,
                              final double pos,
                              final double avgCost) {

        log.info("Position change for account {}, model='{}': contractId={}, symbol={}, pos={}",
                 account, modelCode != null ? modelCode : "", contract.conid(), contract.localSymbol(), pos);

        IbPosition position = new IbPosition(account, contract, pos, avgCost);
        cache.updatePosition(position);

        subscriptions.onNext(SubscriptionsRepository.EventType.EVENT_POSITION_MULTI, reqId, position, true);
    }

    @Override
    public void positionMultiEnd(final int reqId) {
        subscriptions.onNext(SubscriptionsRepository.EventType.EVENT_POSITION_MULTI, reqId, IbPosition.COMPLETE, true);
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
    public void updateAccountValue(final String key,
                                   final String value,
                                   final String currency,
                                   final String accountName) {
        log.trace("updateAccountValue: NOT IMPLEMENTED");
    }

    @Override
    public void updateAccountTime(final String timeStamp) {
        log.debug("updateAccountTime: {}", timeStamp);
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
    public void accountUpdateMulti(final int reqId,
                                   final String account,
                                   final String modelCode,
                                   final String key,
                                   final String value,
                                   final String currency) {


        log.debug("accountUpdateMulti (NOT IMPLEMENTED): " +
                          "reqId={}, account={}, modelCode={}, key={}, value={}, currency={}",
                  reqId, account, modelCode, key, value, currency);
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
    public void tickNews(int tickerId,
                         long timeStamp,
                         String providerCode,
                         String articleId,
                         String headline,
                         String extraData) {
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

    public Set<String> getManagedAccounts() {
        return managedAccounts;
    }

    /**
     * TWS API describes that double equals Double.MAX_VALUE should be threaded as unset
     */
    private Double doubleToDouble(String name, double value) {
        if (value == Double.MAX_VALUE) {
            log.debug("Missing value for '{}'", name);
            return null;
        }

        return value;
    }
}
