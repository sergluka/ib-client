package com.finplant.ib;

import com.finplant.ib.impl.IbReader;
import com.finplant.ib.impl.IdGenerator;
import com.finplant.ib.impl.Validators;
import com.finplant.ib.impl.Wrapper;
import com.finplant.ib.impl.cache.CacheRepositoryImpl;
import com.finplant.ib.impl.connection.ConnectionMonitor;
import com.finplant.ib.impl.request.RequestRepository;
import com.finplant.ib.types.*;
import com.ib.client.*;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings({"WeakerAccess", "unused"})
public class IbClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(IbClient.class);
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");

    private final Subject<IbLogRecord> logSubject = PublishSubject.create();
    private final PublishSubject<Boolean> connectionStatusSubject = PublishSubject.create();

    private final IdGenerator idGenerator;
    private final RequestRepository requests;

    private IbReader reader;
    private Wrapper wrapper;
    private EClientSocket socket;
    private CacheRepositoryImpl cache;
    private ConnectionMonitor connectionMonitor;

    public IbClient() {
        idGenerator = new IdGenerator();
        requests = new RequestRepository(this, idGenerator);
    }

    @Override
    public void close() {
        disconnect();
    }

    public CacheRepository getCache() {
        return cache;
    }

    /**
     * Connects to TWS or IB Gateway instance
     *
     * @param ip     IP address
     * @param port   port
     * @param connId Connection ID
     * @return Completable that completes when connection will be established
     *
     * <pre>{@code
     * Example:
     *
     * IbClient client = new IbClient();
     * client.connect("127.0.0.1", 7497, 0).timeout(10, TimeUnit.SECONDS).blockingAwait();
     * }</pre>
     *
     * @see <a href="https://interactivebrokers.github.io/tws-api/connection.html">
     * TWS API: Connectivity</a>
     */
    public Completable connect(String ip, int port, int connId) {
        Validators.stringShouldNotBeEmpty(ip, "Hostname should be defined");
        Validators.intShouldBePositive(port, "Port should be positive");
        Validators.intShouldBePositiveOrZero(connId, "Connection ID should be positive or 0");

        log.debug("Connecting to {}:{}, id={} ...", ip, port, connId);

        return Completable.create(emitter -> {

            if (isConnected()) {
                log.warn("Already is connected");
                emitter.onComplete();
                return;
            }

            cache = new CacheRepositoryImpl();

            connectionMonitor = new ConnectionMonitor() {

                @Override
                protected void connectRequest() {

                    EJavaSignal signal = new EJavaSignal();

                    socket = new EClientSocket(wrapper, signal);
                    wrapper.setSocket(socket);

                    socket.setAsyncEConnect(false);
                    socket.eConnect(ip, port, connId);

                    reader = new IbReader(socket, signal);
                    reader.start();
                }

                @Override
                protected void disconnectRequest(boolean reconnect) {
                    if (socket != null) {
                        socket.eDisconnect();
                    }
                    if (reader != null) {
                        reader.close();
                    }
                    if (!reconnect) {
                        cache.clear();
                    }

                    socket = null;
                    reader = null;
                }

                @Override
                protected void afterConnect() {
                    socket.setServerLogLevel(IbClient.LogLevel.DETAIL.ordinal());
                    emitter.onComplete();
                }

                @Override
                protected void onConnectStatusChange(Boolean status) {
                    connectionStatusSubject.onNext(status);
                }
            };

            wrapper = new Wrapper(connectionMonitor, cache, requests, idGenerator, logSubject);

            connectionMonitor.start();
            connectionMonitor.connect();

        }).doOnError(e -> connectionMonitor.close());
    }

    /**
     * Disconnects from TWS
     */
    public void disconnect() {
        log.debug("Disconnecting...");
        requests.close();
        connectionMonitor.close();
        log.info("Disconnected");
    }

    /**
     * Returns connection status
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return socket != null &&
               socket.isConnected() &&
               connectionMonitor.status() == ConnectionMonitor.Status.CONNECTED;
    }

    /**
     * Returns managed accounts.
     * <p>
     * This information is received right after connect to TWS
     *
     * @return Set of accounts
     *
     * @see <a href="https://interactivebrokers.github.io/tws-api/managed_accounts.html">
     * TWS API: Managed Accounts</a>
     */
    public Set<String> getManagedAccounts() {
        return wrapper.getManagedAccounts();
    }

    /**
     * Request for account information for all possible tags
     *
     * @param group  If "All" - get account summary data for all accounts,
     *               or specific Advisor Account Group name that has already been created in TWS Global Configuration.
     * @param ledger If "All" - relay all cash balance tags in all currencies,
     *               or specific currency
     * @return Single with account summary
     * <p>
     * // TODO
     */
    public Single<IbAccountSummary> reqAccountSummary(String group, String ledger) {
        return reqAccountSummary(group, ledger, EnumSet.allOf(AccountSummaryTags.class));
    }

    /**
     * Request for account information data
     *
     * @param group  If "All" - get account summary data for all accounts,
     *               or specific Advisor Account Group name that has already been created in TWS Global Configuration.
     * @param ledger If "All" - relay all cash balance tags in all currencies,
     *               or specific currency
     * @param tags   Set of the desired tags:
     * @return Single with account summary
     * <p>
     * // TODO Add examples
     */
    // TODO: Make one function with parameters builder to avoid magic strings
    public Single<IbAccountSummary> reqAccountSummary(String group, String ledger, EnumSet<AccountSummaryTags> tags) {

        Validators.stringShouldNotBeEmpty(group, "Group should be defined");
        Validators.stringShouldNotBeEmpty(ledger, "Ledger should be defined");
        Validators.collectionShouldNotBeEmpty(tags, "Should be defined at least one tag");

        String ledgerTag;
        if (ledger.equals("All")) {
            ledgerTag = "$LEDGER:ALL";
        } else {
            ledgerTag = "$LEDGER:" + ledger;
        }

        String tagsString = Stream.concat(tags.stream().map(Enum::name), Stream.of(ledgerTag))
                                  .collect(Collectors.joining(","));


        return requests.<IbAccountSummary>builder()
                .type(RequestRepository.Type.REQ_ACCOUNT_SUMMARY)
                .register(id -> socket.reqAccountSummary(id, group, tagsString))
                .subscribe()
                .singleOrError();
    }

    /**
     * Subscription to order statuses
     *
     * <pre>{@code
     * Example:
     *
     *  given:
     *  def contract = createContractEUR()
     *  def order = createOrderStp()
     *  def observer = client.subscribeOnOrderNewStatus().test()
     *
     *  when:
     *  client.placeOrder(contract, order).blockingGet()
     *
     *  then:
     *  observer.awaitCount(2)
     *  observer.assertNotComplete()
     *  observer.assertNoErrors()
     *  observer.assertValueAt 0, { it.status == OrderStatus.PreSubmitted } as Predicate
     *  observer.assertValueAt 1, { it.status in [OrderStatus.Submitted, OrderStatus.Filled] } as Predicate
     * }</pre>
     *
     * @return Observable that emits stream of statuses
     *
     * @see <a href="https://interactivebrokers.github.io/tws-api/order_submission.html#order_status">
     * TWS API: The orderStatus callback</a>
     */
    public Observable<IbOrderStatus> subscribeOnOrderNewStatus() {
        return requests.<IbOrderStatus>builder()
                .type(RequestRepository.Type.EVENT_ORDER_STATUS)
                .register(() -> {}) // statuses are received without registration
                .subscribe();
    }

    /**
     * Subscription to position changes for a single account.
     *
     * <pre>{@code
     * Example:
     *
     * when:
     *     def positions = client.subscribeOnPositionChange("AA000000")
     *             .takeUntil({ it == IbPosition.COMPLETE } as Predicate)
     *             .toList()
     *             .timeout(3, TimeUnit.SECONDS)
     *             .blockingGet()
     *
     * then:
     *    positions
     * }</pre>
     *
     * @return Observable that emits stream of {@link com.finplant.ib.types.IbPosition}
     *
     * @implNote After subscription, client sends snapshot with already existing positions. Then
     * only updates will be send. To separate these two cases, special entry will be sent
     * between them - {@link com.finplant.ib.types.IbPosition#COMPLETE}
     * @see <a href="https://interactivebrokers.github.io/tws-api/positions.html#position_request">
     * TWS API: reqPositions</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#ab2159a39733d8967928317524875aa62">
     * TWS API: cancelPositions</a>
     */
    public Observable<IbPosition> subscribeOnPositionChange() {
        return requests.<IbPosition>builder()
                .type(RequestRepository.Type.EVENT_POSITION)
                .register(() -> socket.reqPositions())
                .unregister(() -> socket.cancelPositions())
                .subscribe();
    }

    /**
     * Subscription to position changes for multiple accounts.
     *
     * @param account Account number
     * @return Observable that emits stream of @see IbPosition
     *
     * @implNote After subscription, client sends snapshot with already existing positions. Then
     * only updates will be send. To separate these two cases, special entry will be sent
     * between them - {@link com.finplant.ib.types.IbPosition#COMPLETE}
     *
     * <pre>{@code
     * Example:
     *
     * when:
     * def positions = client.subscribeOnPositionChange("AA99999")
     *         .takeUntil({ it == IbPosition.COMPLETE } as Predicate)
     *         .toList()
     *         .timeout(3, TimeUnit.SECONDS)
     *         .blockingGet()
     *
     * then:
     * positions
     * positions.size() > 0
     * }</pre>
     * @see <a href=https://interactivebrokers.github.io/tws-api/positions.html#position_multi>
     * TWS API: reqPositionsMulti</a>
     * @see
     * <a href=https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#ae919658c15bceba6b6cf2a1336d0acbf>
     * TWS API: cancelPositionsMulti</a>
     */
    public Observable<IbPosition> subscribeOnPositionChange(String account) {
        Validators.stringShouldNotBeEmpty(account, "Account should be defined");

        return requests.<IbPosition>builder()
                .type(RequestRepository.Type.EVENT_POSITION_MULTI)
                .register(id -> socket.reqPositionsMulti(id, account, ""))
                .unregister(id -> socket.cancelPositionsMulti(id))
                .subscribe();
    }

    /**
     * Switch delayed streaming data mode
     *
     * @param type Data feed subscription mode
     * @see com.finplant.ib.IbClient#reqMktData
     * @see <a href=https://interactivebrokers.github.io/tws-api/delayed_data.html>TWS API: Delayed Streaming Data</a>
     */
    public void setMarketDataType(MarketDataType type) {
        Validators.shouldNotBeNull(type, "Type should be defined");

        socket.reqMarketDataType(type.getValue());
    }

    /**
     * Get snapshot of market data
     *
     * @param contract IB contract
     * @return Single with snapshot of market data
     *
     * @see com.finplant.ib.IbClient#setMarketDataType
     * @see <a href=https://interactivebrokers.github.io/tws-api/delayed_data.html>TWS API: Delayed Streaming Data</a>
     * @see
     * <a href=https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#a7a19258a3a2087c07c1c57b93f659b63>TWS
     * API: reqMktData</a>
     * @see
     * <a href=https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#ae919658c15bceba6b6cf2a1336d0acbf>TWS
     * API: cancelPositionsMulti</a>
     */
    public Single<IbTick> reqMktData(Contract contract) {
        Validators.shouldNotBeNull(contract, "Contract should be defined");

        return requests.<IbTick>builder()
                .type(RequestRepository.Type.REQ_MARKET_DATA)
                .register(id -> socket.reqMktData(id, contract, "", true, false, null))
                .unregister(id -> socket.cancelPositionsMulti(id))
                .subscribe()
                .firstOrError();
    }

    /**
     * Requests venues for which market data is returned by {@link com.finplant.ib.IbClient#subscribeOnMarketDepth}
     * subscription
     *
     * @return Observable with descriptions
     *
     * @see <a href=https://interactivebrokers.github.io/tws-api/market_depth.html#reqmktdepthexchanges>
     * TWS API: Market Maker or Exchange</a>
     * @see
     * <a href=https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#a5c40b7bf20ba269530807b093dc51853>
     * TWS API: reqMktDepthExchanges</a>
     */
    public Observable<IbDepthMktDataDescription> reqMktDepthExchanges() {

        return requests.<List<IbDepthMktDataDescription>>builder()
                .type(RequestRepository.Type.REQ_MARKET_DEPTH_EXCHANGES)
                .register(() -> socket.reqMktDepthExchanges())
                .subscribe()
                .flatMap(Observable::fromIterable);
    }

    /**
     * Get market rule for specific ID
     *
     * @param marketRuleId IB contract
     * @return Observable with PriceIncrement. Completes as soon all data will be received.
     *
     * <pre>{@code
     * Example:
     *
     * when:
     *     def observer = client.reqMarketRule(98).test()
     *
     * then:
     *     observer.awaitTerminalEvent()
     *     observer.assertNoErrors()
     *     observer.assertValueCount(1)
     *
     *     observer.assertValueAt 0, { value ->
     *         assert value.lowEdge() == 0.0
     *         assert value.increment() ==  0.001
     *         return true
     *     } as Predicate
     * }</pre>
     *
     * @see com.finplant.ib.IbClient#setMarketDataType
     * @see
     * <a href=https://interactivebrokers.github.io/tws-api/minimum_increment.html>TWS API: Minimum Price Increment</a>
     * @see
     * <a href=https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#af4e8c23a0d1e480ce84320b9e6b525dd>TWS
     * API: reqMarketRule</a>
     */
    public Observable<PriceIncrement> reqMarketRule(int marketRuleId) {
        Validators.intShouldBePositiveOrZero(marketRuleId, "ID should be defined");

        return requests.<List<PriceIncrement>>builder()
                .type(RequestRepository.Type.REQ_MARKET_RULE)
                .register(marketRuleId, () -> socket.reqMarketRule(marketRuleId))
                .unregister(() -> {})
                .subscribe()
                .flatMap(Observable::fromIterable);
    }

    /**
     * Subscription to contract order book (Market Depth Level II)
     *
     * <pre>{@code
     * Example:
     *
     *    given:
     *    def contract = createContractEUR()
     *
     *    when:
     *    def observer = client.subscribeOnMarketDepth(contract, 2).test()
     *
     *    then:
     *    observer.awaitCount(2, BaseTestConsumer.TestWaitStrategy.SLEEP_100MS, 5000)
     *    observer.assertNoErrors()
     *    observer.assertValueAt 0, { IbMarketDepth level ->
     *        level.position == 0
     *        level.side == IbMarketDepth.Side.BUY
     *        level.price > 0.0
     *        level.size > 0
     *    } as Predicate
     *
     *    observer.assertValueAt 1, { IbMarketDepth level ->
     *        level.position == 1
     *        level.side == IbMarketDepth.Side.BUY
     *        level.price > 0.0
     *        level.size > 0
     *    } as Predicate
     * }</pre>
     *
     * @param contract IB contract
     * @param numRows  Order book max depth
     * @return Observable with order book levels
     *
     * @see <a href="https://interactivebrokers.github.io/tws-api/market_depth.html">
     * TWS API: Market Depth (Level II)</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#a8b1f37dfe964369f53d7f2d7ebcf17ca">
     * TWS API: reqMarketDepth</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#aee1213e04e1d22830e337c9735e995db">
     * TWS API: cancelMktDepth</a>
     */
    public Observable<IbMarketDepth> subscribeOnMarketDepth(Contract contract, int numRows) {
        Validators.contractWithIdShouldExist(contract);
        Validators.intShouldBePositive(numRows, "Number of rows should be positive");

        return requests.<IbMarketDepth>builder()
                .type(RequestRepository.Type.EVENT_MARKET_DATA_LVL2)
                .register(id -> socket.reqMktDepth(id, contract, numRows, false, null))
                .unregister(id -> socket.cancelMktDepth(id, false))
                .userData(contract)
                .subscribe();
    }

    /**
     * Subscription to contract ticks (Market Depth Level I)
     *
     * <pre>{@code
     * Example:
     *
     *    when:
     *    def testObserver = client.subscribeOnMarketData(createContractEUR()).test()
     *
     *    then:
     *    testObserver.awaitCount(10)
     *    testObserver.assertNoErrors()
     *    testObserver.assertNotComplete()
     *    testObserver.assertValueAt 0, { it.closePrice > 0 } as Predicate
     * }</pre>
     *
     * @param contract IB contract
     * @return Observable with contract ticks
     *
     * @see <a href="https://interactivebrokers.github.io/tws-api/top_data.html">
     * TWS API: Market Depth (Level I)</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#a7a19258a3a2087c07c1c57b93f659b63">
     * TWS API: reqMktData</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#af443a1cd993aee33ce67deb7bc39e484">
     * TWS API: cancelMktData</a>
     */
    public Observable<IbTick> subscribeOnMarketData(Contract contract) {
        Validators.contractWithIdShouldExist(contract);

        return requests.<IbTick>builder()
                .type(RequestRepository.Type.EVENT_MARKET_DATA)
                .register(id -> socket.reqMktData(id, contract, "", false, false, null))
                .unregister(id -> socket.cancelMktData(id))
                .subscribe();
    }

    /**
     * Subscription to PnL of a specific contract
     *
     * <pre>{@code
     * Example:
     *
     *    given:
     *    def contract = createContractGC()
     *
     *    when:
     *    client.placeOrder(contract, createOrderMkt()).blockingGet()
     *    def observer = client.subscribeOnContractPnl(contract.conid(), "AA000000").test()
     *
     *    then:
     *    observer.awaitCount(1, BaseTestConsumer.TestWaitStrategy.SLEEP_1000MS)
     *    observer.assertNoErrors()
     *    observer.assertNotComplete()
     *    observer.assertValueCount(1)
     *    observer.assertValueAt 0, { it.positionId > 0 } as Predicate
     * }</pre>
     *
     * @param contractId IB contract ID ({@link Contract#conid()} )
     * @param account    User's account
     * @return Observable with PnL data
     *
     * @see <a href="https://interactivebrokers.github.io/tws-api/pnl.html">
     * TWS API: Profit And Loss (P&amp;L)</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#a9ba0dc47f80ff9e836a235a0dea791b3">
     * TWS API: reqPnLSingle</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#accd383725ef440070775654a9ab4bf47">
     * TWS API: cancelPnLSingle</a>
     * @see com.finplant.ib.IbClient#subscribeOnAccountPnl
     */
    public Observable<IbPnl> subscribeOnContractPnl(int contractId, String account) {
        Validators.intShouldBePositive(contractId, "Contract ID should be positive");
        Validators.stringShouldNotBeEmpty(account, "Account should be defined");

        return requests.<IbPnl>builder()
                .type(RequestRepository.Type.EVENT_CONTRACT_PNL)
                .register(id -> socket.reqPnLSingle(id, account, "", contractId))
                .unregister(id -> socket.cancelPnLSingle(id))
                .subscribe();
    }

    /**
     * Subscription to PnL
     *
     * <pre>{@code
     * Example:
     *
     *    given:
     *    def contract = createContractGC()
     *
     *    when:
     *    client.placeOrder(contract, createOrderMkt()).blockingGet()
     *    def observer = client.subscribeOnContractPnl(contract.conid(), "AA000000").test()
     *
     *    then:
     *    observer.awaitCount(1, BaseTestConsumer.TestWaitStrategy.SLEEP_1000MS)
     *    observer.assertNoErrors()
     *    observer.assertNotComplete()
     *    observer.assertValueCount(1)
     *    observer.assertValueAt 0, { it.positionId > 0 } as Predicate
     * }</pre>
     *
     * @param account User's account
     * @return Observable with PnL data
     *
     * @see <a href="https://interactivebrokers.github.io/tws-api/pnl.html">
     * TWS API: Profit And Loss (P&amp;L)</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#a0351f22a77b5ba0c0243122baf72fa45">
     * TWS API: reqPnL</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#a5a805731fedd8f40130a51a459328572">
     * TWS API: cancelPnL</a>
     * @see com.finplant.ib.IbClient#subscribeOnContractPnl
     */
    public Observable<IbPnl> subscribeOnAccountPnl(String account) {
        Validators.stringShouldNotBeEmpty(account, "Account should be defined");

        return requests.<IbPnl>builder()
                .type(RequestRepository.Type.EVENT_ACCOUNT_PNL)
                .register(id -> socket.reqPnL(id, account, ""))
                .unregister(id -> socket.cancelPnL(id))
                .subscribe();
    }

    /**
     * Subscription to user's portfolio updates
     *
     * @param account User's account
     * @return Observable with portfolio information
     *
     * <pre>{@code
     * Example:
     *
     *    when:
     *    def portfolio = client.subscribeOnAccountPortfolio("DU22993")
     *            .takeUntil({ it == IbPortfolio.COMPLETE } as Predicate)
     *            .toList()
     *            .timeout(3, TimeUnit.MINUTES)
     *            .blockingGet()
     *
     *    then:
     *    portfolio.size() > 0
     * }</pre>
     *
     * @implNote This information is the same as displayed in TWS Account Window. Data updates once per 3 min
     * @see <a href="https://interactivebrokers.github.io/tws-api/account_updates.html">
     * TWS API: Account Updates</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#aea1b0d9b6b85a4e0b18caf13a51f837f">
     * TWS API: reqAccountUpdates</a>
     * @see com.finplant.ib.IbClient#subscribeOnContractPnl
     */
    public Observable<IbPortfolio> subscribeOnAccountPortfolio(String account) {
        Validators.stringShouldNotBeEmpty(account, "Account should be defined");

        return requests.<IbPortfolio>builder()
                .type(RequestRepository.Type.EVENT_PORTFOLIO)
                .register(() -> socket.reqAccountUpdates(true, account))
                .unregister(() -> socket.reqAccountUpdates(false, account))
                .subscribe();
    }

    /**
     * Subscription to connection status to TWS
     * <p>
     * As soon connection with TWS is lost or restored, respective status is published
     *
     * @return Observable with booleans
     */
    public Observable<Boolean> connectionStatus() {
        return connectionStatusSubject;
    }

    /**
     * Subscription to TWS events.
     *
     * @return Observable with TWS events
     *
     * @implNote Internally {@code ib-client} handles incoming TWS events according actual request type with severity
     * detection, as soon TWS sends them in many cases as an errors, warning, or as a regular notifications.
     * In normal work this function won't be needed, but it can be used if necessary to handle
     * event that {@code ib-client} don't handle yet, or just for TWS events logging.
     * @see <a href="https://interactivebrokers.github.io/tws-api/error_handling.html">
     * TWS API: Error Handling</a>
     * @see com.finplant.ib.types.IbLogRecord
     */
    public Observable<IbLogRecord> subscribeOnEvent() {
        return logSubject;
    }

    /**
     * Returns current TWS time
     *
     * <pre>{@code
     * Example:
     *
     *    when:
     *    long time = client.getCurrentTime().blockingGet()
     *
     *    then:
     *    time > 1510320971
     * }</pre>
     *
     * @return Single with a time in UNIX format
     *
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#ad1ecfd4fb31841ce5817e0c32f44b639">
     * TWS API: reqCurrentTime</a>
     */
    public Single<Long> getCurrentTime() {
        return requests.<Long>builder()
                .type(RequestRepository.Type.REQ_CURRENT_TIME)
                .register(() -> socket.reqCurrentTime())
                .subscribe()
                .firstOrError();
    }

    /**
     * Places an order
     *
     * @param contract IB contract
     * @param order    IB order
     * @return Single with a IB result
     *
     * @implNote TWS API requires every new order has to have unique and incremented {@link com.ib.client.Order#orderId}
     * that is build based on "nextValidId" returned on API at connect.
     * {@code ib-client} makes this step optional. If {@link com.ib.client.Order#orderId} is not assigned or zero,
     * library generates ID by itself. In some cases Developer need to define ID explicitly, then he is able to
     * generate new value with {@link #nextOrderId}
     *
     * <pre>{@code
     * Example:
     *    given:
     *    def contract = new Contract()
     *    contract.symbol("EUR")
     *    contract.currency("USD")
     *    contract.exchange("IDEALPRO")
     *    contract.secType(Types.SecType.CASH)
     *
     *    def order = new Order()
     *    order.action("BUY")
     *    order.orderType(OrderType.STP)
     *    order.auxPrice(auxPrice)
     *    order.tif("GTC")
     *    order.totalQuantity(quantity)
     *
     *    when:
     *    def single = client.placeOrder(contract, order).test()
     *
     *    then:
     *    single.awaitTerminalEvent(10, TimeUnit.SECONDS)
     *    single.assertNoErrors()
     *    single.assertComplete()
     *    single.assertValueAt 0, { it.orderId > 0 } as Predicate
     * }</pre>
     * @see <a href="https://interactivebrokers.github.io/tws-api/order_submission.html">
     * TWS API: Placing Orders</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/interfaceIBApi_1_1EWrapper.html#a09c07727efd297e438690ab42838d332">
     * TWS API: nextValidId</a>
     * @see #placeOrder(Contract, Order, Scheduler)
     * @see #nextOrderId
     */
    public Single<IbOrder> placeOrder(Contract contract, Order order) {
        return placeOrder(contract, order, Schedulers.io());
    }

    /**
     * Places an order with custom schedule for `.subscribeOn`
     *
     * @param contract           IB contract
     * @param order              IB order
     * @param subscribeScheduler RxJava2 scheduler used to replace default scheduler {@code .subscribeOn(Schedulers
     *                           .io())}
     * @return Single with a IB result
     *
     * @see #placeOrder(Contract, Order)
     */
    public Single<IbOrder> placeOrder(Contract contract, Order order, Scheduler subscribeScheduler) {
        Validators.shouldNotBeNull(contract, "Contract should be defined");
        Validators.shouldNotBeNull(order, "Order should be defined");

        if (order.orderId() == 0) {
            order.orderId(idGenerator.nextOrderId());
        }

        return requests.<IbOrder>builder()
                .id(order.orderId())
                .type(RequestRepository.Type.REQ_ORDER_PLACE)
                .register(id -> socket.placeOrder(id, contract, order))
                .subscribe(subscribeScheduler)
                .firstOrError();
    }

    /**
     * Cancels an order
     *
     * @param orderId order ID. See {@link com.ib.client.Order#orderId}
     * @return Completable
     *
     * @apiNote Library checks cancel result by checking statuses of the order.
     * First it looks, does order doesn't already canceled or filled.
     * Then library request IB for canceling and waits for respective status or error.
     * @see <a href="https://interactivebrokers.github.io/tws-api/cancel_order.html">
     * TWS API: Cancelling Orders</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#adb0a6963779be4904439fb1f24a8cce8">
     * TWS API: cancelOrder</a>
     */
    public Completable cancelOrder(int orderId) {
        Validators.intShouldBePositive(orderId, "Order ID should be positive");

        log.info("Canceling order {}", orderId);

        // Checking does order doesn't already filled or canceled
        Completable preconditions = Completable.create(emitter -> {
            IbOrder order = cache.getOrders().getOrDefault(orderId, null);
            if (order == null) {
                emitter.onComplete();
                return;
            }

            IbOrderStatus lastStatus = order.getLastStatus();
            if (lastStatus.isFilled()) {
                emitter.onError(new IbExceptions.OrderAlreadyFilledError(orderId));
                return;
            }
            if (lastStatus.isCanceled()) {
                log.warn("Order {} already has been canceled", orderId);
                emitter.onComplete();
                return;
            }

            emitter.onComplete();
        });

        Completable request = Completable.defer(() -> {
            return requests.builder()
                           .id(orderId)
                           .type(RequestRepository.Type.REQ_ORDER_CANCEL)
                           .register(id -> socket.cancelOrder(id))
                           .subscribe()
                           .ignoreElements();
        });

        return preconditions.andThen(request);
    }

    /**
     * Cancels all opened orders
     *
     * @return Completable
     *
     * @implNote To provide result, library uses a bit tangled logic to wait until all opened orders will be canceled.
     * Some corner case are possible.
     * @see <a href="https://interactivebrokers.github.io/tws-api/cancel_order.html">
     * TWS API: Cancelling Orders</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#a66ad7a4820c5be21ebde521d59a50053">
     * TWS API: reqGlobalCancel</a>
     */
    public Completable cancelAll() {

        return Observable.create(emitter -> {
            Integer sumOfOrders = reqAllOpenOrders()
                    .filter(order -> !order.getLastStatus().isCanceled() &&
                                     !order.getLastStatus().isFilled() &&
                                     !order.getLastStatus().isInactive())
                    .map(IbOrder::getOrderId)
                    .reduce(0, Integer::sum)
                    .blockingGet();

            socket.reqGlobalCancel();

            if (sumOfOrders == 0) {
                emitter.onComplete();
                return;
            }

            subscribeOnOrderNewStatus()
                    .filter(IbOrderStatus::isCanceled)
                    .map(IbOrderStatus::getOrderId)
                    .scan(sumOfOrders, (a, b) -> a - b)
                    .filter(sum -> sum == 0)
                    .take(1)
                    .blockingLast();

            emitter.onComplete();
        }).ignoreElements();
    }

    /**
     * Requests for all open orders
     *
     * <pre>{@code
     * Example:
     *
     *    given:
     *    def contract = createContractEUR()
     *    def order = createOrderStp()
     *
     *    when:
     *    client.placeOrder(contract, order).blockingGet()
     *    def list = client.reqAllOpenOrders().timeout(3, TimeUnit.SECONDS).toList().blockingGet()
     *
     *    then:
     *    list.size() > 0
     * }</pre>
     *
     * @return Observable with orders. Completes as TWS sends all orders.
     *
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#aa2c9a012884dd53311a7a7c6a326306c">
     * TWS API: reqAllOpenOrders</a>
     */
    public Observable<IbOrder> reqAllOpenOrders() {
        return requests.<List<IbOrder>>builder()
                .type(RequestRepository.Type.REQ_ORDER_LIST)
                .register(() -> socket.reqAllOpenOrders())
                .subscribe()
                .flatMap(Observable::fromIterable);
    }

    /**
     * Requests for contract details.
     *
     * @param contract IB contract
     * @return Observable with contract details. Completes as soon TWS sends all data.
     *
     * <pre>{@code
     * Example:
     *    given:
     *    def contract = new Contract()
     *    contract.symbol("EUR")
     *    contract.currency("USD")
     *    contract.secType(Types.SecType.CASH)
     *
     *    when:
     *    def list = client.reqContractDetails(contract).toList().blockingGet()
     *
     *    then:
     *    list.size() == 1
     * }</pre>
     *
     * @implNote Note that IB can return more then one ContractDetails.
     * @see <a href="https://interactivebrokers.github.io/tws-api/contract_details.html">
     * TWS API: Requesting Contract Details</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#ade440c6db838b548594981d800ea5ca9">
     * TWS API: reqContractDetails</a>
     */
    public Observable<ContractDetails> reqContractDetails(Contract contract) {
        Validators.shouldNotBeNull(contract, "Contract should be defined");

        return requests.<ContractDetails>builder()
                .type(RequestRepository.Type.REQ_CONTRACT_DETAIL)
                .register(id -> socket.reqContractDetails(id, contract))
                .subscribe();
    }

    /**
     * Returns historical midpoint for specific time period
     *
     * @param contract IB contract
     * @param from     Period start. Uses TWS timezone specified at login. Cannot be used with "<strong>to</strong>"
     * @param to       Period end. Uses TWS timezone specified at login. Cannot be used with "<strong>from</strong>"
     * @param limit    Number of distinct data points. Max is 1000 per request.
     * @return Observable with historical data. Completes as soon IB sends all data.
     *
     * <pre>{@code
     * Example:
     *
     *    given:
     *    def from = LocalDateTime.now().minusWeeks(1)
     *
     *    when:
     *    def observer = client.reqHistoricalMidpoints(createContractEUR(), from, null, null).test()
     *
     *    then:
     *    observer.awaitDone(10, TimeUnit.SECONDS)
     *    observer.assertNoErrors()
     *    observer.assertComplete()
     *    observer.valueCount() >= 1000
     *    observer.assertValueAt 0, { tick ->
     *        def tz = OffsetDateTime.now().getOffset();
     *        assert Math.abs(from.toEpochSecond(tz) - tick.time()) < 100
     *        assert tick.price() > 0.0
     *        true
     *    } as Predicate
     * }</pre>
     *
     * @see <a href="https://interactivebrokers.github.io/tws-api/historical_time_and_sales.html">
     * TWS API: Historical Time and Sales Data</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#a99dbcf80c99b8f262a524de64b2f9c1e">
     * TWS API: reqHistoricalTicks</a>
     * @see #reqHistoricalBidAsks
     * @see #reqHistoricalTrades
     */
    public Observable<HistoricalTick> reqHistoricalMidpoints(@NotNull Contract contract,
                                                             @Nullable LocalDateTime from,
                                                             @Nullable LocalDateTime to,
                                                             @Nullable Integer limit) {
        return reqHistoricalTicks(contract, from, to, limit,
                                  RequestRepository.Type.REQ_HISTORICAL_MIDPOINT_TICK, "MIDPOINT");
    }

    /**
     * Returns historical ticks for specific time period
     *
     * @param contract IB contract
     * @param from     Period start. Uses TWS timezone specified at login. Cannot be used with "<strong>to</strong>"
     * @param to       Period end. Uses TWS timezone specified at login. Cannot be used with "<strong>from</strong>"
     * @param limit    Number of distinct data points. Max is 1000 per request.
     * @return Observable with historical data. Completes as soon IB sends all data.
     *
     * <pre>{@code
     * Example:
     *
     *    given:
     *    def from = LocalDateTime.now().minusWeeks(1)
     *
     *    when:
     *    def observer = client.reqHistoricalBidAsks(createContractEUR(), from, null, null).test()
     *
     *    then:
     *    observer.awaitDone(10, TimeUnit.SECONDS)
     *    observer.assertNoErrors()
     *    observer.assertComplete()
     *    observer.valueCount() >= 1000
     *    observer.assertValueAt 0, { tick ->
     *        def tz = OffsetDateTime.now().getOffset();
     *        assert Math.abs(from.toEpochSecond(tz) - tick.time()) < 100
     *        assert tick.priceBid() > 0.0
     *        assert tick.priceAsk() > 0.0
     *        assert tick.sizeBid() > 0.0
     *        assert tick.sizeAsk() > 0.0
     *        true
     *    } as Predicate
     * }</pre>
     *
     * @see <a href="https://interactivebrokers.github.io/tws-api/historical_time_and_sales.html">
     * TWS API: Historical Time and Sales Data</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#a99dbcf80c99b8f262a524de64b2f9c1e">
     * TWS API: reqHistoricalTicks</a>
     * @see #reqHistoricalMidpoints
     * @see #reqHistoricalTrades
     */
    public Observable<HistoricalTickBidAsk> reqHistoricalBidAsks(@NotNull Contract contract,
                                                                 @Nullable LocalDateTime from,
                                                                 @Nullable LocalDateTime to,
                                                                 @Nullable Integer limit) {
        return reqHistoricalTicks(contract, from, to, limit,
                                  RequestRepository.Type.REQ_HISTORICAL_BID_ASK_TICK, "BID_ASK");
    }

    /**
     * Returns historical trades for specific time period
     *
     * @param contract IB contract
     * @param from     Period start. Uses TWS timezone specified at login. Cannot be used with "<strong>to</strong>"
     * @param to       Period end. Uses TWS timezone specified at login. Cannot be used with "<strong>from</strong>"
     * @param limit    Number of distinct data points. Max is 1000 per request.
     * @return Observable with historical data. Completes as soon IB sends all data.
     *
     * <pre>{@code
     * Example:
     *
     *    given:
     *    def from = LocalDateTime.now().minusWeeks(1)
     *
     *    when:
     *    def observer = client.reqHistoricalTrades(createContractFB(), from, null, null).test()
     *
     *    then:
     *    observer.awaitDone(10, TimeUnit.SECONDS)
     *    observer.assertNoErrors()
     *    observer.assertComplete()
     *    observer.valueCount() >= 1000
     *    observer.assertValueAt 0, { tick ->
     *        def tz = OffsetDateTime.now().getOffset();
     *        assert Math.abs(from.toEpochSecond(tz) - tick.time()) < 100000
     *        assert tick.price() > 0.0
     *        assert tick.size() > 0.0
     *        true
     *    } as Predicate
     * }</pre>
     *
     * @apiNote Note that IB doesn't return trades for Forex contracts, i.e. for contracts with
     * {@link Contract#secType()} is {@link  Types.SecType#CASH}. Calling respective IB API method
     * {@link EClient#reqHistoricalTicks} with parameter {@code whatToShow=TRADES} and Forex contract,
     * it was expected that {@link EWrapper#historicalTicksLast} callback will be called,
     * as it described in documentation. Instead {@link EWrapper#historicalTicks}callback is called,
     * the same way as if you call {@link #reqHistoricalMidpoints} with {@code whatToShow=MIDPOINT}.
     * For stocks this request behaves as expected. IB support told "it's a special case for Forex"
     * <p>
     * So to make library API consistent, all Forex contracts for this method are forbidden. You still able
     * to call {@link #reqHistoricalMidpoints} for them.
     * @see <a href="https://interactivebrokers.github.io/tws-api/historical_time_and_sales.html">
     * TWS API: Historical Time and Sales Data</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#a99dbcf80c99b8f262a524de64b2f9c1e">
     * TWS API: reqHistoricalTicks</a>
     * @see #reqHistoricalMidpoints
     * @see #reqHistoricalBidAsks
     */
    public Observable<HistoricalTickLast> reqHistoricalTrades(@NotNull Contract contract,
                                                              @Nullable LocalDateTime from,
                                                              @Nullable LocalDateTime to,
                                                              @Nullable Integer limit) {

        if (contract.secType() == Types.SecType.CASH) {
            throw new IllegalArgumentException("IB doesn't return historical trades for Forex contracts");
        }

        return reqHistoricalTicks(contract, from, to, limit,
                                  RequestRepository.Type.REQ_HISTORICAL_TRADE, "TRADES");
    }

    /**
     * Request for historical bars (aka candles)
     *
     * @param contract     IB contract
     * @param endDateTime  End date and time
     * @param duration     The amount of time to go back from the request's given end date and time.
     * @param durationUnit <strong>duration</strong> unit
     * @param size         Valid Bar Sizes
     * @param type         The type of data to retrieve
     * @param tradingHours Whether ({@link TradingHours#Within}) or not ({@link TradingHours#Outside}) to retrieve
     *                     data generated only within Regular Trading Hours
     * @return Observable with the bars. Completes as soon all data per period will be received.
     * Can emit errors: IbExceptions.NoPermissions on "No market data permissions" message,
     * Exception - for possible unknown messages
     *
     * <pre>{@code
     * Example:
     *    given:
     *    def endDateTime = LocalDateTime.of(2019, 04, 16, 12, 0, 0);
     *
     *    when:
     *    def observer = client.reqHistoricalData(createContractFB(), endDateTime,
     *                                            120, IbClient.DurationUnit.Second, _1_min,
     *                                            IbClient.Type.TRADES,
     *                                            IbClient.TradingHours.Within).test()
     *    then:
     *    observer.awaitDone(10, TimeUnit.SECONDS)
     *    observer.assertNoErrors()
     *    observer.assertComplete()
     *    observer.valueCount() == 2
     *    observer.assertValueAt 0, { IbBar bar ->
     *        assert bar.time == LocalDateTime.of(2019, 4, 15, 22, 58, 0)
     *        assert bar.open == 179.59
     *        assert bar.high == 179.6
     *        assert bar.low == 179.54
     *        assert bar.close == 179.57
     *        assert bar.volume == 604
     *        assert bar.count == 417
     *        assert bar.wap == 179.566
     *        true
     *    } as Predicate
     *    observer.assertValueAt 1, { IbBar bar ->
     *        assert bar.time == LocalDateTime.of(2019, 4, 15, 22, 59, 0)
     *        assert bar.open == 179.58
     *        assert bar.high == 179.77
     *        assert bar.low == 179.56
     *        assert bar.close == 179.66
     *        assert bar.volume == 1046
     *        assert bar.count == 720
     *        assert bar.wap == 179.685
     *        true
     *    } as Predicate
     * }
     * }</pre>
     *
     * @see <a href="https://interactivebrokers.github.io/tws-api/historical_bars.html">
     * TWS API: Historical Bar Data</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#a5eac5b7908b62c224985cf8577a8350c">
     * TWS API: reqHistoricalData</a>
     * @see #subscribeOnHistoricalData
     */
    public Observable<IbBar> reqHistoricalData(@NotNull Contract contract, LocalDateTime endDateTime,
                                               int duration, DurationUnit durationUnit,
                                               BarSize size,
                                               Type type, TradingHours tradingHours) {

        log.debug("Requesting bars: contract={}, endDateTime={}, duration={} {}, size={}, type={}, hours={}",
                  contract.description(), endDateTime, duration, durationUnit, size, type, tradingHours);

        return requests.<IbBar>builder()
                .type(RequestRepository.Type.REQ_HISTORICAL_DATA)
                .register(id -> {
                    socket.reqHistoricalData(id, contract,
                                             endDateTime != null ? endDateTime.format(dateTimeFormatter) : null,
                                             String.format("%d %s", duration, durationUnit.toString()),
                                             size.toString(), type.toString(),
                                             tradingHours == TradingHours.Within ? 1 : 0, 1, false,
                                             null);
                })
                .subscribe();
    }

    /**
     * Request for historical bars (aka candles) and subscription for actual unfinished candle
     *
     * @param contract     IB contract
     * @param duration     The amount of time to go back from the request's given end date and time.
     * @param durationUnit <strong>duration</strong>
     * @param size         Valid Bar Sizes. Must be above or equals 5 seconds.
     * @param type         The type of data to retrieve
     * @param tradingHours Whether ({@link IbClient.TradingHours#Within}) or not ({@link IbClient.TradingHours#Outside})
     *                     to retrieve
     *                     data generated only within Regular Trading Hours
     * @return Observable with the bars. Never completes. Flow is: 1st historical bars are goes, then one
     * {@link com.finplant.ib.types.IbBar#COMPLETE}, and then constant updates of actual candle. Can emit errors:
     * IbExceptions.NoPermissions on "No market data permissions" message, Exception - for possible unknown messages
     *
     * <pre>{@code
     * Example:
     *    when:
     *    def observer = client.subscribeOnHistoricalData(createContractEUR(),
     *                                                    30, IbClient.DurationUnit.Second,
     *                                                    period,
     *                                                    IbClient.Type.BID,
     *                                                    IbClient.TradingHours.Within)
     *            .skipWhile { it == IbBar.COMPLETE }
     *            .filter { it != IbBar.COMPLETE }
     *            .take(1).test()
     *
     *    then:
     *    observer.awaitCount(1)
     *    observer.assertNoErrors()
     *    observer.assertComplete()
     *    observer.valueCount() == 1
     *    observer.assertValueAt 0, { IbBar bar ->
     *        assert bar.open > 0.0
     *        assert bar.high > 0.0
     *        assert bar.low > 0.0
     *        assert bar.close > 0.0
     *        true
     *    } as Predicate
     * }</pre>
     *
     * @implNote Function is similar to {@link #reqHistoricalData}, it got all historical data as
     * {@link #reqHistoricalData} do, but then emits {@link com.finplant.ib.types.IbBar#COMPLETE} as a separator,
     * and then continue emit actual candle updates
     * @see <a href="https://interactivebrokers.github.io/tws-api/historical_bars.html">
     * TWS API: Historical Bar Data</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#a5eac5b7908b62c224985cf8577a8350c">
     * TWS API: reqHistoricalData</a>
     * @see #reqHistoricalData
     */
    public Observable<IbBar> subscribeOnHistoricalData(@NotNull Contract contract,
                                                       int duration, DurationUnit durationUnit,
                                                       BarSize size,
                                                       Type type, TradingHours tradingHours) {

        if (size == BarSize._1_sec) {
            return Observable.error(new Exception("Too small bar size. Must be >= 5 sec"));
        }

        log.debug("Subscribing to bars: contract={}, size={}, type={}, hours={}",
                  contract.description(), size, type, tradingHours);

        return requests.<IbBar>builder()
                .type(RequestRepository.Type.EVENT_HISTORICAL_DATA)
                .register(id -> {
                    socket.reqHistoricalData(id, contract, null,
                                             String.format("%d %s", duration, durationUnit.toString()),
                                             size.toString(), type.toString(),
                                             tradingHours == TradingHours.Within ? 1 : 0, 1, true,
                                             null);
                })
                .unregister(id -> socket.cancelHistoricalData(id))
                .subscribe();
    }

    /**
     * Generates new incremental order ID, if developer need to define it explicitly.
     *
     * @return incremental ID
     *
     * @apiNote Thread safe.
     * @see #placeOrder(Contract, Order)
     */
    public int nextOrderId() {
        return idGenerator.nextOrderId();
    }

    private <T> Observable<T> reqHistoricalTicks(Contract contract,
                                                 LocalDateTime from,
                                                 LocalDateTime to,
                                                 Integer limit,
                                                 RequestRepository.Type type,
                                                 String typeStr) {
        Validators.shouldNotBeNull(contract, "Contract should be defined");

        final int MAX_ALLOWED_TICKS_COUNT = 1000;

        if (limit != null) {
            Validators.intShouldBeInRange(limit, 1, MAX_ALLOWED_TICKS_COUNT);
        }

        return requests.<List<T>>builder()
                .type(type)
                .register(id -> socket.reqHistoricalTicks(id, contract,
                                                          from != null ? from.format(dateTimeFormatter) : null,
                                                          to != null ? to.format(dateTimeFormatter) : null,
                                                          limit != null ? limit : MAX_ALLOWED_TICKS_COUNT,
                                                          typeStr, 0, true, null))
                .subscribe()
                .flatMap(Observable::fromIterable);
    }

    public enum LogLevel {
        NONE,
        SYSTEM,
        ERROR,
        WARNING,
        INFORMATION,
        DETAIL,
    }

    /**
     * Type of market data feed
     */
    public enum MarketDataType {
        LIVE(1),
        FROZEN(2),
        DELAYED(3),
        DELAYED_FROZEN(4);

        private final int value;

        MarketDataType(int value) {
            this.value = value;
        }

        int getValue() {
            return value;
        }
    }

    public enum AccountSummaryTags {
        AccountType, // Identifies the IB account structure
        NetLiquidation, // The basis for determining the price of the assets in your account. Total cash value +
        // stock value + options value + bond value
        TotalCashValue, // Total cash balance recognized at the time of trade + futures PNL
        SettledCash, // Cash recognized at the time of settlement - purchases at the time of trade - commissions -
        // taxes - fees
        AccruedCash, // Total accrued cash value of stock, commodities and securities
        BuyingPower, // Buying power serves as a measurement of the dollar value of securities that one may purchase
        // in a securities account without depositing additional funds
        EquityWithLoanValue, // Forms the basis for determining whether a client has the necessary assets to either
        // initiate or maintain security positions. Cash + stocks + bonds + mutual funds
        PreviousEquityWithLoanValue, // Marginable Equity with Loan value as of 16:00 ET the previous day
        GrossPositionValue, // The sum of the absolute value of all stock and equity option positions
        RegTEquity, // Regulation T equity for universal account
        RegTMargin, // Regulation T margin for universal account
        SMA, // Special Memorandum Account: Line of credit created when the market value of securities in a
        // Regulation T account increase in value
        InitMarginReq, // Initial Margin requirement of whole portfolio
        MaintMarginReq, // Maintenance Margin requirement of whole portfolio
        AvailableFunds, // This value tells what you have available for trading
        ExcessLiquidity, // This value shows your margin cushion, before liquidation
        Cushion, // Excess liquidity as a percentage of net liquidation value
        FullInitMarginReq, // Initial Margin of whole portfolio with no discounts or intraday credits
        FullMaintMarginReq, // Maintenance Margin of whole portfolio with no discounts or intraday credits
        FullAvailableFunds, // Available funds of whole portfolio with no discounts or intraday credits
        FullExcessLiquidity, // Excess liquidity of whole portfolio with no discounts or intraday credits
        LookAheadNextChange, // Time when look-ahead values take effect
        LookAheadInitMarginReq, // Initial Margin requirement of whole portfolio as of next period's margin change
        LookAheadMaintMarginReq, // Maintenance Margin requirement of whole portfolio as of next period's margin change
        LookAheadAvailableFunds, // This value reflects your available funds at the next margin change
        LookAheadExcessLiquidity, // This value reflects your excess liquidity at the next margin change
        HighestSeverity, // A measure of how close the account is to liquidation
        DayTradesRemaining, // The Number of Open/Close trades a user could put on before Pattern Day Trading is
        // detected. A value of "-1" means that the user can put on unlimited day trades.
        Leverage, // GrossPositionValue / NetLiquidation
    }

    public enum BarSize {
        _1_sec("1 secs"),
        _5_sec("5 secs"),
        _10_sec("10 secs"),
        _15_sec("15 secs"),
        _30_sec("30 secs"),
        _1_min("1 min"),
        _2_min("2 mins"),
        _3_min("3 mins"),
        _5_min("5 mins"),
        _10_min("10 mins"),
        _15_min("15 mins"),
        _20_min("20 mins"),
        _30_min("30 mins"),
        _1_hour("1 hour"),
        _2_hour("2 hours"),
        _3_hour("3 hours"),
        _4_hour("4 hours"),
        _8_hour("8 hours"),
        _1_day("1 day"),
        _1_week("1 week"),
        _1_month("1 month");

        private final String text;

        BarSize(final String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    public enum Type {
        TRADES, //	First traded price
        MIDPOINT, //	Starting midpoint price
        BID, //	Starting bid price
        ASK, //	Starting ask price
        BID_ASK, //	Time average bid
        ADJUSTED_LAST, //	Dividend-adjusted first traded price
        HISTORICAL_VOLATILITY, //	Starting volatility	Highest volatility
        OPTION_IMPLIED_VOLATILITY, //	Starting implied volatility
        REBATE_RATE, //	Starting rebate rate
        FEE_RATE, //	Starting fee rate
        YIELD_BID, //	Starting bid yield
        YIELD_ASK, //	Starting ask yield
        YIELD_BID_ASK, //	Time average bid yield
        YIELD_LAST, //	Starting last yield	Highest
    }

    public enum DurationUnit {
        Second("S"),
        Day("D"),
        Week("W"),
        Month("M"),
        Year("Y");

        private final String text;

        DurationUnit(final String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    public enum TradingHours {
        Within,
        Outside
    }
}
