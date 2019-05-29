package com.finplant.ib.impl;

import com.finplant.ib.IbExceptions;
import com.finplant.ib.impl.cache.CacheRepositoryImpl;
import com.finplant.ib.impl.connection.ConnectionMonitor;
import com.finplant.ib.impl.request.RequestRepository;
import com.finplant.ib.types.*;
import com.finplant.ib.utils.PrettyPrinters;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.ib.client.*;
import io.reactivex.Observer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.SocketException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Wrapper implements EWrapper {

    private static final Logger log = LoggerFactory.getLogger(Wrapper.class);
    private final TerminalErrorHandler errorHandler;
    private final ConnectionMonitor connectionMonitor;
    private final CacheRepositoryImpl cache;
    private final RequestRepository requests;
    private final IdGenerator idGenerator;
    private Set<String> managedAccounts;
    private EClientSocket socket;

    public Wrapper(ConnectionMonitor connectionMonitor,
                   CacheRepositoryImpl cache,
                   RequestRepository requests,
                   IdGenerator idGenerator,
                   Observer<IbLogRecord> logObserver) {

        errorHandler = new TerminalErrorHandler(requests) {

            @Override
            void onLog(IbLogRecord record) {
                logObserver.onNext(record);
            }

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
        this.cache = cache;
        this.requests = requests;
        this.idGenerator = idGenerator;
    }

    @Override
    public void tickPrice(int tickerId, int field, double price, TickAttrib attribs) {
        if (price == -1) {
            log.debug("Got absent `tickPrice` for field %d");
            return;
        }

        IbTick result = cache.updateTick(tickerId, (tick) ->
                tick.setPriceValue(field, BigDecimal.valueOf(price), attribs));
        publishNewTick(tickerId, result);
    }

    @Override
    public void tickSize(int tickerId, int field, int value) {
        if (value == -1) {
            log.debug("Got absent `tickSize` for field %d");
            return;
        }

        IbTick result = cache.updateTick(tickerId, (tick) -> tick.setIntValue(field, value));
        publishNewTick(tickerId, result);
    }

    @Override
    public void tickGeneric(int tickerId, int field, double value) {
        IbTick result = cache.updateTick(tickerId, (tick) -> tick.setGenericValue(field, BigDecimal.valueOf(value)));
        publishNewTick(tickerId, result);
    }

    @Override
    public void tickString(int tickerId, int field, String value) {
        IbTick result = cache.updateTick(tickerId, (tick) -> tick.setStringValue(field, value));
        publishNewTick(tickerId, result);
    }

    @Override
    public void orderStatus(int orderId, String status, double filled, double remaining, double avgFillPrice,
                            int permId, int parentId, double lastFillPrice, int clientId, String whyHeld,
                            double mktCapPrice) {

        final IbOrderStatus twsStatus = new IbOrderStatus(orderId,
                                                          status,
                                                          BigDecimal.valueOf(filled),
                                                          BigDecimal.valueOf(remaining),
                                                          BigDecimal.valueOf(avgFillPrice),
                                                          permId,
                                                          parentId,
                                                          BigDecimal.valueOf(lastFillPrice),
                                                          clientId,
                                                          whyHeld,
                                                          BigDecimal.valueOf(mktCapPrice));

        log.trace("orderStatus: {}", twsStatus);

        if (cache.addNewStatus(twsStatus)) {

            log.info("New order status: {}", twsStatus);
            requests.onNext(RequestRepository.Type.EVENT_ORDER_STATUS, null, twsStatus, false);

            if (twsStatus.isCanceled()) {
                requests.onNextAndComplete(RequestRepository.Type.REQ_ORDER_CANCEL, orderId, true, false);
            }
            if (twsStatus.isFilled()) {
                requests.onError(RequestRepository.Type.REQ_ORDER_CANCEL, orderId,
                                 new IbExceptions.IbClientError("Already filled"), false);
            }
        }
    }

    @Override
    public void openOrder(int orderId, Contract contract, Order order, OrderState state) {
        IbOrder twsOrder = new IbOrder(orderId, contract, order, state);

        log.trace("openOrder: requestId={}, contract={}, order={}, orderState={}",
                  orderId, contract.symbol(), order.orderId(), state.status());

        if (cache.addOrder(twsOrder)) {
            log.info("New order: requestId={}, contract={}, order={}, orderState={}",
                     orderId, contract.symbol(), order.orderId(), state.status());

            if (!state.status().isActive()) {
                requests.onError(RequestRepository.Type.REQ_ORDER_PLACE, orderId,
                                 new IbExceptions.IbClientError("Order is rejected"));
                return;
            }

            requests.onNextAndComplete(RequestRepository.Type.REQ_ORDER_PLACE, orderId, twsOrder, false);
        }
    }

    @Override
    public void openOrderEnd() {
        requests.onNextAndComplete(RequestRepository.Type.REQ_ORDER_LIST, null,
                                   new ArrayList<>(cache.getOrders().values()), false);
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

        BigDecimal positionObj = doubleToBigDecimal("position", position);
        BigDecimal marketPriceObj = doubleToBigDecimal("marketPrice", marketPrice);
        BigDecimal marketValueObj = doubleToBigDecimal("marketValue", marketValue);
        BigDecimal averageCostObj = doubleToBigDecimal("averageCost", averageCost);
        BigDecimal unrealizedPNLObj = doubleToBigDecimal("unrealizedPNL", unrealizedPNL);
        BigDecimal realizedPNLObj = doubleToBigDecimal("realizedPNL", realizedPNL);

        log.trace("updatePortfolio: contract={}, position={}, marketPrice={}, marketValue={}, averageCost={}, " +
                  "unrealizedPNL={}, realizedPNL={}, accountName={}",
                  contract, position, marketPrice, marketValue, averageCost, unrealizedPNL, realizedPNL, accountName);

        IbPortfolio portfolio = new IbPortfolio(contract, positionObj, marketPriceObj, marketValueObj, averageCostObj,
                                                unrealizedPNLObj, realizedPNLObj, accountName);

        cache.updatePortfolio(portfolio);
        requests.onNext(RequestRepository.Type.EVENT_PORTFOLIO, null, portfolio, true);
    }

    @Override
    public void updateAccountTime(final String timeStamp) {
        log.trace("updateAccountTime: {}", timeStamp);
    }

    @Override
    public void accountDownloadEnd(String accountName) {
        requests.onNext(RequestRepository.Type.EVENT_PORTFOLIO, null, IbPortfolio.COMPLETE, true);
    }

    @Override
    public void historicalTicks(int reqId, List<HistoricalTick> ticks, boolean done) {
        requests.onNext(RequestRepository.Type.REQ_HISTORICAL_MIDPOINT_TICK, reqId, ticks, true);
        if (done) {
            requests.onComplete(RequestRepository.Type.REQ_HISTORICAL_MIDPOINT_TICK, reqId, true);
        }
    }

    @Override
    public void historicalTicksBidAsk(int reqId, List<HistoricalTickBidAsk> ticks, boolean done) {
        requests.onNext(RequestRepository.Type.REQ_HISTORICAL_BID_ASK_TICK, reqId, ticks, true);
        if (done) {
            requests.onComplete(RequestRepository.Type.REQ_HISTORICAL_BID_ASK_TICK, reqId, true);
        }
    }

    @Override
    public void historicalTicksLast(int reqId, List<HistoricalTickLast> ticks, boolean done) {
        requests.onNext(RequestRepository.Type.REQ_HISTORICAL_TRADE, reqId, ticks, true);
        if (done) {
            requests.onComplete(RequestRepository.Type.REQ_HISTORICAL_TRADE, reqId, true);
        }
    }

    @Override
    public void historicalData(int reqId, Bar bar) {
        IbBar ibBar = new IbBar(bar);
        log.trace("historicalData: {}", ibBar);
        requests.onNext(RequestRepository.Type.REQ_HISTORICAL_DATA, reqId, ibBar, false);
        requests.onNext(RequestRepository.Type.EVENT_HISTORICAL_DATA, reqId, IbBar.COMPLETE, false);
    }

    @Override
    public void historicalDataEnd(int reqId, String startDateStr, String endDateStr) {
        log.trace("historicalDataEnd: startDateStr={}, endDateStr={}", startDateStr, endDateStr);
        requests.onComplete(RequestRepository.Type.REQ_HISTORICAL_DATA, reqId, false);
        requests.onNext(RequestRepository.Type.EVENT_HISTORICAL_DATA, reqId, IbBar.COMPLETE, false);
    }

    @Override
    public void historicalDataUpdate(int reqId, Bar bar) {
        IbBar ibBar = new IbBar(bar);
        log.trace("historicalDataUpdate: {}", ibBar);
        requests.onNext(RequestRepository.Type.EVENT_HISTORICAL_DATA, reqId, ibBar, false);
    }

    @Override
    public void nextValidId(int id) {
        log.trace("New request ID: {}", id);
        if (idGenerator.setOrderId(id)) {
            cache.clear();
        }
    }

    @Override
    public void contractDetails(final int reqId, final ContractDetails contractDetails) {
        log.trace("contractDetails: reqId={}, details={}",
                  reqId, PrettyPrinters.contractDetailsToString(contractDetails));
        requests.onNext(RequestRepository.Type.REQ_CONTRACT_DETAIL, reqId, contractDetails, false);
    }

    @Override
    public void contractDetailsEnd(final int reqId) {
        requests.onComplete(RequestRepository.Type.REQ_CONTRACT_DETAIL, reqId, false);
    }

    @Override
    public void updateMktDepth(final int tickerId,
                               final int position,
                               final int operation,
                               final int side,
                               final double price,
                               final int size) {
        log.trace("updateMktDepth: tickerId = {}, position = {}, operation = {}, side = {}, price = {}, size = {}",
                  tickerId, position, operation, side, price, size);

        handleUpdateMktDepth(tickerId, position, null, operation, side, price, size);
    }

    public void updateMktDepthL2(int tickerId, int position,
                                 String marketMaker, int operation, int side, double price, int size,
                                 boolean isSmartDepth) {

        log.trace("updateMktDepthL2: tickerId = {}, position = {}, marketMaker = {}, operation = {}, side = {}, " +
                  "price = {}, size = {}", tickerId, position, marketMaker, operation, side, price, size);

        handleUpdateMktDepth(tickerId, position, marketMaker, operation, side, price, size);
    }

    @Override
    public void managedAccounts(String accountsList) {
        // Documentation said that connection fully established after `managedAccounts` and `nextValidId` were called
        connectionMonitor.confirmConnection();

        log.trace("Managed accounts are: {}", accountsList);
        this.managedAccounts = new HashSet<>(Splitter.on(",").splitToList(accountsList));
    }

    @Override
    public void marketRule(int marketRuleId, PriceIncrement[] priceIncrements) {
        log.trace("marketRule: marketRuleId={}, priceIncrements={}",
                  marketRuleId, PrettyPrinters.priceIncrementsToString(priceIncrements));

        List<PriceIncrement> increments = Stream.of(priceIncrements).collect(Collectors.toList());
        requests.onNextAndComplete(RequestRepository.Type.REQ_MARKET_RULE, marketRuleId, increments, true);
    }

    @Override
    public void execDetails(final int reqId, final Contract contract, final Execution execution) {
        log.trace("execDetails: NOT IMPLEMENTED");
    }

    @Override
    public void execDetailsEnd(final int reqId) {
        log.trace("execDetailsEnd: NOT IMPLEMENTED");
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
        log.trace("tickOptionComputation: NOT IMPLEMENTED");
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
        log.trace("dividendsToLastTradeDate: NOT IMPLEMENTED");
    }

    @Override
    public void updateAccountValue(final String key,
                                   final String value,
                                   final String currency,
                                   final String accountName) {
    }

    @Override
    public void bondContractDetails(final int reqId, final ContractDetails contractDetails) {
        log.trace("bondContractDetails: NOT IMPLEMENTED");
    }

    @Override
    public void updateNewsBulletin(final int msgId,
                                   final int msgType,
                                   final String message,
                                   final String origExchange) {
        log.trace("updateNewsBulletin: NOT IMPLEMENTED");
    }


    @Override
    public void receiveFA(final int faDataType, final String xml) {
        log.trace("receiveFA: NOT IMPLEMENTED");
    }

    @Override
    public void scannerParameters(final String xml) {
        log.trace("scannerParameters: NOT IMPLEMENTED");
    }

    @Override
    public void scannerData(final int reqId,
                            final int rank,
                            final ContractDetails contractDetails,
                            final String distance,
                            final String benchmark,
                            final String projection,
                            final String legsStr) {

        log.trace("scannerData: NOT IMPLEMENTED");
    }

    @Override
    public void scannerDataEnd(final int reqId) {
        log.trace("scannerDataEnd: NOT IMPLEMENTED");
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
        log.trace("realtimeBar: NOT IMPLEMENTED");
    }

    @Override
    public void currentTime(final long time) {
        requests.onNextAndComplete(RequestRepository.Type.REQ_CURRENT_TIME, null, time, true);
    }

    @Override
    public void fundamentalData(final int reqId, final String data) {
        log.trace("fundamentalData: NOT IMPLEMENTED");
    }

    @Override
    public void deltaNeutralValidation(final int reqId, final DeltaNeutralContract underComp) {
        log.trace("deltaNeutralValidation: NOT IMPLEMENTED");
    }

    @Override
    public void tickSnapshotEnd(final int tickerId) {
        log.trace("tickSnapshotEnd({})", tickerId);

        IbTick tick = cache.getTick(tickerId);
        if (tick == null) {
            log.info("No ticks for ticker {}", tickerId);
            requests.onError(tickerId, new IbExceptions.NoTicksError(tickerId));
            return;
        }

        requests.onNextAndComplete(null, tickerId, tick, false);
    }

    @Override
    public void marketDataType(final int reqId, final int marketDataType) {
        log.trace("marketDataType: NOT IMPLEMENTED");
    }

    @Override
    public void commissionReport(final CommissionReport commissionReport) {
        log.trace("commissionReport: NOT IMPLEMENTED");
    }

    @Override
    public void position(String account, Contract contract, double pos, double avgCost) {
        log.info("Position change: {}/{},{}/{}", account, contract.conid(), contract.localSymbol(), pos);

        IbPosition position = new IbPosition(account, contract, BigDecimal.valueOf(pos), BigDecimal.valueOf(avgCost));
        cache.updatePosition(position);
        requests.onNext(RequestRepository.Type.EVENT_POSITION, null, position, true);
    }

    @Override
    public void positionEnd() {
        log.trace("All positions have been received");
        requests.onNext(RequestRepository.Type.EVENT_POSITION, null, IbPosition.COMPLETE, true);
    }

    @Override
    public void accountSummary(final int reqId,
                               final String account,
                               final String tag,
                               final String value,
                               final String currency) {

        log.trace("accountSummary: reqId = {}, account = {}, {} = {} {}", reqId, account, tag, value, currency);
        cache.updateAccountSummary(reqId, account, tag, value, currency);
    }

    @Override
    public void accountSummaryEnd(final int reqId) {
        requests.onNextAndComplete(RequestRepository.Type.REQ_ACCOUNT_SUMMARY, reqId,
                                   cache.popAccountSummary(reqId), true);
    }

    @Override
    public void verifyMessageAPI(final String apiData) {
        log.trace("verifyMessageAPI: NOT IMPLEMENTED");
    }

    @Override
    public void verifyCompleted(final boolean isSuccessful, final String errorText) {
        log.trace("verifyCompleted: NOT IMPLEMENTED");
    }

    @Override
    public void verifyAndAuthMessageAPI(final String apiData, final String xyzChallange) {
        log.trace("verifyAndAuthMessageAPI: NOT IMPLEMENTED");
    }

    @Override
    public void verifyAndAuthCompleted(final boolean isSuccessful, final String errorText) {
        log.trace("verifyAndAuthCompleted: NOT IMPLEMENTED");
    }

    @Override
    public void displayGroupList(final int reqId, final String groups) {
        log.trace("displayGroupList: NOT IMPLEMENTED");
    }

    @Override
    public void displayGroupUpdated(final int reqId, final String contractInfo) {
        log.trace("displayGroupUpdated: NOT IMPLEMENTED");
    }

    @Override
    public void error(final Exception e) {
        if (e instanceof SocketException) {

            final ConnectionMonitor.Status connectionStatus = connectionMonitor.status();

            if (connectionStatus == ConnectionMonitor.Status.DISCONNECTING ||
                connectionStatus == ConnectionMonitor.Status.DISCONNECTED) {

                log.trace("Socket has been closed at shutdown");
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

                log.trace("Ignoring error of uninitialized stream");
                return;
            }
        }

        log.error("TWS exception", e);
    }

    @Override
    public void error(final String str) {
        log.error("Shouldn't be there. err={}", str);
    }

    @Override
    public void error(final int id, final int code, final String message) {
        errorHandler.handle(id, code, message);
    }

    @Override
    public void connectionClosed() {
        log.error("TWS closes the connection");
        connectionMonitor.reconnect();
    }

    @Override
    public void connectAck() {
        log.info("Connection is opened. version: {}", socket.serverVersion());
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

        IbPosition position = new IbPosition(account, contract, BigDecimal.valueOf(pos), BigDecimal.valueOf(avgCost));
        cache.updatePosition(position);

        requests.onNext(RequestRepository.Type.EVENT_POSITION_MULTI, reqId, position, true);
    }

    @Override
    public void positionMultiEnd(final int reqId) {
        requests.onNext(RequestRepository.Type.EVENT_POSITION_MULTI, reqId, IbPosition.COMPLETE, true);
    }

    @Override
    public void accountUpdateMulti(final int reqId,
                                   final String account,
                                   final String modelCode,
                                   final String key,
                                   final String value,
                                   final String currency) {

        log.trace("accountUpdateMulti (NOT IMPLEMENTED): " +
                  "reqId={}, account={}, modelCode={}, key={}, value={}, currency={}",
                  reqId, account, modelCode, key, value, currency);
    }

    @Override
    public void accountUpdateMultiEnd(final int reqId) {
        log.trace("accountUpdateMultiEnd: NOT IMPLEMENTED");
    }

    @Override
    public void securityDefinitionOptionalParameter(final int reqId,
                                                    final String exchange,
                                                    final int underlyingConId,
                                                    final String tradingClass,
                                                    final String multiplier,
                                                    final Set<String> expirations,
                                                    final Set<Double> strikes) {
        log.trace("securityDefinitionOptionalParameter: NOT IMPLEMENTED");
    }

    @Override
    public void securityDefinitionOptionalParameterEnd(final int reqId) {
        log.trace("securityDefinitionOptionalParameterEnd: NOT IMPLEMENTED");
    }

    @Override
    public void softDollarTiers(final int reqId, final SoftDollarTier[] tiers) {
        log.trace("softDollarTiers: NOT IMPLEMENTED");
    }

    @Override
    public void familyCodes(FamilyCode[] familyCodes) {
        log.trace("familyCodes: NOT IMPLEMENTED");
    }

    @Override
    public void symbolSamples(int reqId, ContractDescription[] contractDescriptions) {
        log.trace("symbolSamples: NOT IMPLEMENTED");
    }

    @Override
    public void mktDepthExchanges(DepthMktDataDescription[] depthMktDataDescriptions) {
        List<IbDepthMktDataDescription> result =
                Lists.newArrayList(depthMktDataDescriptions).stream()
                     .map(IbDepthMktDataDescription::new)
                     .collect(Collectors.toList());
        log.trace("mktDepthExchanges: {}", result);

        requests.onNextAndComplete(RequestRepository.Type.REQ_MARKET_DEPTH_EXCHANGES, null, result, true);
    }

    @Override
    public void tickNews(int tickerId,
                         long timeStamp,
                         String providerCode,
                         String articleId,
                         String headline,
                         String extraData) {
        log.trace("tickNews: NOT IMPLEMENTED");
    }

    @Override
    public void smartComponents(int reqId, Map<Integer, Map.Entry<String, Character>> theMap) {
        log.trace("smartComponents: NOT IMPLEMENTED");
    }

    @Override
    public void tickReqParams(int tickerId, double minTick, String bboExchange, int snapshotPermissions) {
        log.trace("tickReqParams: NOT IMPLEMENTED");
    }

    @Override
    public void newsProviders(NewsProvider[] newsProviders) {
        log.trace("newsProviders: NOT IMPLEMENTED");
    }

    @Override
    public void newsArticle(int requestId, int articleType, String articleText) {
        log.trace("newsArticle: NOT IMPLEMENTED");
    }

    @Override
    public void historicalNews(int requestId, String time, String providerCode, String articleId, String headline) {
        log.trace("historicalNews: NOT IMPLEMENTED");
    }

    @Override
    public void historicalNewsEnd(int requestId, boolean hasMore) {
        log.trace("historicalNewsEnd: NOT IMPLEMENTED");
    }

    @Override
    public void headTimestamp(int reqId, String headTimestamp) {
        log.trace("headTimestamp: NOT IMPLEMENTED");
    }

    @Override
    public void histogramData(int reqId, List<HistogramEntry> items) {
        log.trace("histogramData: NOT IMPLEMENTED");
    }

    @Override
    public void rerouteMktDataReq(int reqId, int conId, String exchange) {
        log.trace("rerouteMktDataReq: NOT IMPLEMENTED");
    }

    @Override
    public void rerouteMktDepthReq(int reqId, int conId, String exchange) {
        log.trace("rerouteMktDepthReq: NOT IMPLEMENTED");
    }

    public void pnl(int reqId, double dailyPnL, double unrealizedPnL, double realizedPnL) {
        log.trace("pnl: reqId={}, dailyPnL={}, unrealizedPnL={}, realizedPnL={}",
                  reqId, dailyPnL, unrealizedPnL, realizedPnL);

        IbPnl pnl = new IbPnl(null, BigDecimal.valueOf(dailyPnL), BigDecimal.valueOf(unrealizedPnL),
                              BigDecimal.valueOf(realizedPnL), null);
        requests.onNext(RequestRepository.Type.EVENT_ACCOUNT_PNL, reqId, pnl, true);
    }

    @Override
    public void pnlSingle(int reqId, int pos, double dailyPnL, double unrealizedPnL, double realizedPnL, double value) {
        log.trace("pnlSingle: reqId={}, pos={}, dailyPnL={}, unrealizedPnL={}, realizedPnL={}, value={}",
                  reqId, pos, dailyPnL, unrealizedPnL, realizedPnL, value);

        BigDecimal dailyPnLObj = doubleToBigDecimal("dailyPnL", dailyPnL);
        BigDecimal unrealizedPnLObj = doubleToBigDecimal("unrealizedPnL", unrealizedPnL);
        BigDecimal realizedPnLObj = doubleToBigDecimal("realizedPnL", realizedPnL);
        BigDecimal valueObj = doubleToBigDecimal("value", value);

        IbPnl pnl = new IbPnl(pos, dailyPnLObj, unrealizedPnLObj, realizedPnLObj, valueObj);
        requests.onNext(RequestRepository.Type.EVENT_CONTRACT_PNL, reqId, pnl, true);
    }

    @Override
    public void tickByTickAllLast(int reqId, int tickType, long time, double price, int size,
                                  TickAttribLast tickAttribLast, String exchange, String specialConditions) {
        log.trace("tickByTickAllLast: NOT IMPLEMENTED");
    }

    @Override
    public void tickByTickBidAsk(int reqId, long time, double bidPrice, double askPrice, int bidSize, int askSize,
                                 TickAttribBidAsk tickAttribBidAsk) {
        log.trace("tickByTickBidAsk: NOT IMPLEMENTED");
    }

    @Override
    public void tickByTickMidPoint(int reqId, long time, double midPoint) {
        log.trace("tickByTickMidPoint: NOT IMPLEMENTED");
    }

    @Override
    public void orderBound(long orderId, int apiClientId, int apiOrderId) {
        log.trace("orderBound: NOT IMPLEMENTED");
    }

    public void setSocket(EClientSocket socket) {
        this.socket = socket;
    }

    private void publishNewTick(int tickerId, IbTick result) {
        requests.onNext(RequestRepository.Type.EVENT_MARKET_DATA, tickerId, result, false);
    }

    private void publishNoData(int tickerId) {
        requests.onError(null, tickerId, new IbExceptions.NoTicksError(tickerId), false);
    }

    public Set<String> getManagedAccounts() {
        return managedAccounts;
    }

    private void handleUpdateMktDepth(int tickerId, int position, String marketMaker, int operation, int side,
                                      double price, int size) {

        Contract contract = (Contract) requests.getUserData(RequestRepository.Type.EVENT_MARKET_DATA_LVL2, tickerId);
        Objects.requireNonNull(contract);

        IbMarketDepth orderBookDepth = new IbMarketDepth(contract, position, side, BigDecimal.valueOf(price),
                                                         size, marketMaker);
        cache.addMarketDepth(contract, orderBookDepth, IbMarketDepth.Operation.values()[operation]);

        requests.onNext(RequestRepository.Type.EVENT_MARKET_DATA_LVL2, tickerId, orderBookDepth, true);
    }

    /**
     * TWS API describes that double equals Double.MAX_VALUE should be threaded as unset
     */
    private BigDecimal doubleToBigDecimal(String name, double value) {
        if (value == Double.MAX_VALUE) {
            log.trace("Missing value for '{}'", name);
            return null;
        }

        return BigDecimal.valueOf(value);
    }
}
