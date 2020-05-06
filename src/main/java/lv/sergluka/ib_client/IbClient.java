package lv.sergluka.ib_client;

import lv.sergluka.ib_client.impl.IbReader;
import lv.sergluka.ib_client.impl.IdGenerator;
import lv.sergluka.ib_client.impl.Validators;
import lv.sergluka.ib_client.impl.Wrapper;
import lv.sergluka.ib_client.impl.cache.CacheRepositoryImpl;
import lv.sergluka.ib_client.impl.connection.ConnectionMonitor;
import lv.sergluka.ib_client.impl.request.RequestRepository;
import lv.sergluka.ib_client.params.AccountsSummaryParams;
import lv.sergluka.ib_client.params.IbClientOptions;
import com.ib.client.*;
import lv.sergluka.ib_client.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@SuppressWarnings({"unused"})
public class IbClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(IbClient.class);
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");

    private final EmitterProcessor<IbLogRecord> logSubject = EmitterProcessor.create();
    private final EmitterProcessor<Boolean> connectionStatusSubject = EmitterProcessor.create();

    private final IdGenerator idGenerator;
    private final RequestRepository requests;
    private final IbClientOptions options;

    private IbReader reader;
    private Wrapper wrapper;
    private EClientSocket socket;
    private CacheRepositoryImpl cache;
    private ConnectionMonitor connectionMonitor;

    public IbClient() {
        this(new IbClientOptions());
    }

    public IbClient(IbClientOptions options) {
        this.options = options;
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
     * Generates new incremental request/order ID, if developer need to define it explicitly.
     *
     * @return incremental ID
     *
     * @apiNote Thread safe.
     * @see #placeOrder(Contract, Order)
     */
    public int nextOrderId() {
        return idGenerator.nextId();
    }

    /**
     * Connects to TWS or IB Gateway instance.
     *
     * @param ip     IP address
     * @param port   port
     * @param connId Connection ID
     * @return Mono that completes when connection will be established
     *
     * @see <a href="https://interactivebrokers.github.io/tws-api/connection.html">
     * TWS API: Connectivity</a>
     */
    public Mono<Void> connect(String ip, int port, int connId) {
        Validators.stringShouldNotBeEmpty(ip, "Hostname should be defined");
        Validators.intShouldBePositive(port, "Port should be positive");
        Validators.intShouldBePositiveOrZero(connId, "Connection ID should be positive or 0");

        log.debug("Connecting to {}:{}, id={} ...", ip, port, connId);

        return Mono.create(emitter -> {

            if (isConnected()) {
                log.warn("Already is connected");
                emitter.success();
                return;
            }

            cache = new CacheRepositoryImpl();

            connectionMonitor = new ConnectionMonitor(options.getConnectionDelay()) {

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
                }

                @Override
                protected void onConnectStatusChange(Boolean status) {
                    if (status) {
                        emitter.success();
                    }
                    connectionStatusSubject.onNext(status);
                }
            };

            wrapper = new Wrapper(connectionMonitor, cache, requests, idGenerator, logSubject);

            connectionMonitor.start();
            connectionMonitor.connect();

        }).doOnError(e -> connectionMonitor.close())
                   .then();
    }

    /**
     * Disconnects from TWS.
     */
    public void disconnect() {
        log.debug("Disconnecting...");
        requests.close();
        connectionMonitor.close();
        log.info("Disconnected");
    }

    /**
     * Returns connection status.
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
     *
     * <p>This information is received right after connect to TWS
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
     * Request for account summary data.
     *
     * @param params  Consumer with params builder
     * @return Mono with account summary
     *
     * @see <a href="https://interactivebrokers.github.io/tws-api/account_summary.html">
     * TWS API: Account Summary</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#a3e0d55d36cd416639b97ee6e47a86fe9">
     * TWS API: reqAccountSummary</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#af5ec205466b47cdab2cd17931b529e82">
     * TWS API: cancelAccountSummary</a>
     */
    public Mono<IbAccountsSummary> reqAccountSummary(Consumer<AccountsSummaryParams> params) {

        AccountsSummaryParams paramsBuilder = new AccountsSummaryParams();
        params.accept(paramsBuilder);

        paramsBuilder.validate();

        String accountGroup = paramsBuilder.getAccountGroup();
        String tagsString = paramsBuilder.buildTags();

        return requests.<IbAccountsSummary>builder()
                .type(RequestRepository.Type.REQ_ACCOUNT_SUMMARY)
                .register(id -> socket.reqAccountSummary(id, accountGroup, tagsString))
                .unregister(id -> socket.cancelAccountSummary(id))
                .subscribe()
                .single();
    }

    /**
     * Subscription to order statuses.
     *
     * @return Flux that emits stream of statuses
     *
     * @see <a href="https://interactivebrokers.github.io/tws-api/order_submission.html#order_status">
     * TWS API: The orderStatus callback</a>
     */
    public Flux<IbOrderStatus> subscribeOnOrderNewStatus() {
        return requests.<IbOrderStatus>builder()
                .type(RequestRepository.Type.EVENT_ORDER_STATUS)
                .register(() -> {
                }) // statuses are received without registration
                .subscribe();
    }

    /**
     * Subscription to position changes for a single account.
     *
     * @return Flux that emits stream of {@link IbPosition}
     *
     * @implNote After subscription, client sends snapshot with already existing positions. Then
     * only updates will be send. To separate these two cases, special entry will be sent
     * between them - {@link IbPosition#COMPLETE}
     * @see <a href="https://interactivebrokers.github.io/tws-api/positions.html#position_request">
     * TWS API: reqPositions</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#ab2159a39733d8967928317524875aa62">
     * TWS API: cancelPositions</a>
     */
    public Flux<IbPosition> subscribeOnPositionChange() {
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
     * @return Flux that emits stream of @see IbPosition
     *
     * @implNote After subscription, client sends snapshot with already existing positions. Then
     * only updates will be send. To separate these two cases, special entry will be sent
     * between them - {@link IbPosition#COMPLETE}
     * @see <a href=https://interactivebrokers.github.io/tws-api/positions.html#position_multi>
     * TWS API: reqPositionsMulti</a>
     * @see
     * <a href=https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#ae919658c15bceba6b6cf2a1336d0acbf>
     * TWS API: cancelPositionsMulti</a>
     */
    public Flux<IbPosition> subscribeOnPositionChange(String account) {
        Validators.stringShouldNotBeEmpty(account, "Account should be defined");

        return requests.<IbPosition>builder()
                .type(RequestRepository.Type.EVENT_POSITION_MULTI)
                .register(id -> socket.reqPositionsMulti(id, account, ""))
                .unregister(id -> socket.cancelPositionsMulti(id))
                .subscribe();
    }

    /**
     * Switch delayed streaming data mode.
     *
     * @param type Data feed subscription mode
     * @see IbClient#reqMarketData
     * @see <a href=https://interactivebrokers.github.io/tws-api/delayed_data.html>TWS API: Delayed Streaming Data</a>
     */
    public void setMarketDataType(MarketDataType type) {
        Validators.shouldNotBeNull(type, "Type should be defined");

        socket.reqMarketDataType(type.getValue());
    }

    /**
     * Get snapshot of market data.
     *
     * @param contract IB contract
     * @return Mono with snapshot of market data
     *
     * @see IbClient#setMarketDataType
     * @see <a href=https://interactivebrokers.github.io/tws-api/delayed_data.html>TWS API: Delayed Streaming Data</a>
     * @see
     * <a href=https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#a7a19258a3a2087c07c1c57b93f659b63>TWS
     * API: reqMarketData</a>
     * @see
     * <a href=https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#ae919658c15bceba6b6cf2a1336d0acbf>TWS
     * API: cancelPositionsMulti</a>
     */
    public Mono<IbTick> reqMarketData(Contract contract) {
        Validators.shouldNotBeNull(contract, "Contract should be defined");

        return requests.<IbTick>builder()
                .type(RequestRepository.Type.REQ_MARKET_DATA)
                .register(id -> socket.reqMktData(id, contract, "", true, false, null))
                .unregister(id -> socket.cancelPositionsMulti(id))
                .subscribe()
                .single();
    }

    /**
     * Requests venues for which market data is returned by {@link IbClient#subscribeOnMarketDepth}
     * subscription.
     *
     * @return Flux with descriptions
     *
     * @see <a href=https://interactivebrokers.github.io/tws-api/market_depth.html#reqmktdepthexchanges>
     * TWS API: Market Maker or Exchange</a>
     * @see
     * <a href=https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#a5c40b7bf20ba269530807b093dc51853>
     * TWS API: reqMktDepthExchanges</a>
     */
    public Flux<IbDepthMktDataDescription> reqMktDepthExchanges() {

        return requests.<List<IbDepthMktDataDescription>>builder()
                .type(RequestRepository.Type.REQ_MARKET_DEPTH_EXCHANGES)
                .register(() -> socket.reqMktDepthExchanges())
                .subscribe()
                .flatMap(Flux::fromIterable);
    }

    /**
     * Get market rule for specific ID.
     *
     * @param marketRuleId IB contract
     * @return Flux with PriceIncrement. Completes as soon all data will be received.
     *
     * @see IbClient#setMarketDataType
     * @see
     * <a href=https://interactivebrokers.github.io/tws-api/minimum_increment.html>TWS API: Minimum Price Increment</a>
     * @see
     * <a href=https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#af4e8c23a0d1e480ce84320b9e6b525dd>TWS
     * API: reqMarketRule</a>
     */
    public Flux<PriceIncrement> reqMarketRule(int marketRuleId) {
        Validators.intShouldBePositiveOrZero(marketRuleId, "ID should be defined");

        return requests.<List<PriceIncrement>>builder()
                .type(RequestRepository.Type.REQ_MARKET_RULE)
                .register(marketRuleId, () -> socket.reqMarketRule(marketRuleId))
                .unregister(() -> {
                })
                .subscribe()
                .flatMap(Flux::fromIterable);
    }

    /**
     * Subscription to contract order book (Market Depth Level II).
     *
     * @param contract IB contract
     * @param numRows  Order book max depth
     * @return Flux with order book levels
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
    public Flux<IbMarketDepth> subscribeOnMarketDepth(Contract contract, int numRows) {
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
     * Subscription to contract ticks (Market Depth Level I).
     *
     * @param contract IB contract
     * @return Flux with contract ticks
     *
     * @see <a href="https://interactivebrokers.github.io/tws-api/top_data.html">
     * TWS API: Market Depth (Level I)</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#a7a19258a3a2087c07c1c57b93f659b63">
     * TWS API: reqMarketData</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#af443a1cd993aee33ce67deb7bc39e484">
     * TWS API: cancelMktData</a>
     */
    public Flux<IbTick> subscribeOnMarketData(Contract contract) {
        Validators.contractWithIdShouldExist(contract);

        return requests.<IbTick>builder()
                .type(RequestRepository.Type.EVENT_MARKET_DATA)
                .register(id -> socket.reqMktData(id, contract, "", false, false, null))
                .unregister(id -> socket.cancelMktData(id))
                .subscribe();
    }

    /**
     * Subscription to PnL of a specific contract.
     *
     * @param contractId IB contract ID ({@link Contract#conid()} )
     * @param account    User's account
     * @return Flux with PnL data
     *
     * @see <a href="https://interactivebrokers.github.io/tws-api/pnl.html">
     * TWS API: Profit And Loss (P&amp;L)</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#a9ba0dc47f80ff9e836a235a0dea791b3">
     * TWS API: reqPnLMono</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#accd383725ef440070775654a9ab4bf47">
     * TWS API: cancelPnLMono</a>
     * @see IbClient#subscribeOnAccountPnl
     */
    public Flux<IbPnl> subscribeOnContractPnl(int contractId, String account) {
        Validators.intShouldBePositive(contractId, "Contract ID should be positive");
        Validators.stringShouldNotBeEmpty(account, "Account should be defined");

        return requests.<IbPnl>builder()
                .type(RequestRepository.Type.EVENT_CONTRACT_PNL)
                .register(id -> socket.reqPnLSingle(id, account, "", contractId))
                .unregister(id -> socket.cancelPnLSingle(id))
                .subscribe();
    }

    /**
     * Subscription to PnL.
     *
     * @param account User's account
     * @return Flux with PnL data
     *
     * @see <a href="https://interactivebrokers.github.io/tws-api/pnl.html">
     * TWS API: Profit And Loss (P&amp;L)</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#a0351f22a77b5ba0c0243122baf72fa45">
     * TWS API: reqPnL</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#a5a805731fedd8f40130a51a459328572">
     * TWS API: cancelPnL</a>
     * @see IbClient#subscribeOnContractPnl
     */
    public Flux<IbPnl> subscribeOnAccountPnl(String account) {
        Validators.stringShouldNotBeEmpty(account, "Account should be defined");

        return requests.<IbPnl>builder()
                .type(RequestRepository.Type.EVENT_ACCOUNT_PNL)
                .register(id -> socket.reqPnL(id, account, ""))
                .unregister(id -> socket.cancelPnL(id))
                .subscribe();
    }

    /**
     * Subscription to user's portfolio updates.
     *
     * @param account User's account
     * @return Flux with portfolio information
     *
     * @implNote This information is the same as displayed in TWS Account Window. Data updates once per 3 min
     * @see <a href="https://interactivebrokers.github.io/tws-api/account_updates.html">
     * TWS API: Account Updates</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#aea1b0d9b6b85a4e0b18caf13a51f837f">
     * TWS API: reqAccountUpdates</a>
     * @see IbClient#subscribeOnContractPnl
     */
    public Flux<IbPortfolio> subscribeOnAccountPortfolio(String account) {
        Validators.stringShouldNotBeEmpty(account, "Account should be defined");

        return requests.<IbPortfolio>builder()
                .type(RequestRepository.Type.EVENT_PORTFOLIO)
                .register(() -> socket.reqAccountUpdates(true, account))
                .unregister(() -> socket.reqAccountUpdates(false, account))
                .subscribe();
    }

    /**
     * Subscription to connection status to TWS.
     *
     * <p>As soon connection with TWS is lost or restored, respective status is published
     *
     * @return Flux with booleans
     */
    public Flux<Boolean> connectionStatus() {
        return connectionStatusSubject;
    }

    /**
     * Subscription to TWS events.
     *
     * @return Flux with TWS events
     *
     * @implNote Internally {@code ib-client} handles incoming TWS events according actual request type with severity
     * detection, as soon TWS sends them in many cases as an errors, warning, or as a regular notifications.
     * In normal work this function won't be needed, but it can be used if necessary to handle
     * event that {@code ib-client} don't handle yet, or just for TWS events logging.
     * @see <a href="https://interactivebrokers.github.io/tws-api/error_handling.html">
     * TWS API: Error Handling</a>
     * @see IbLogRecord
     */
    public Flux<IbLogRecord> subscribeOnEvent() {
        return logSubject;
    }

    /**
     * Returns current TWS time.
     *
     * @return Mono with a TWS server time assuming that its in GMT
     *
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#ad1ecfd4fb31841ce5817e0c32f44b639">
     * TWS API: reqCurrentTime</a>
     */
    public Mono<LocalDateTime> getCurrentTime() {
        return requests.<Long>builder()
                .type(RequestRepository.Type.REQ_CURRENT_TIME)
                .register(() -> socket.reqCurrentTime())
                .subscribe()
                .map(time -> LocalDateTime.ofEpochSecond(time, 0, ZoneOffset.UTC))
                .single();
    }

    /**
     * Places an order.
     *
     * @param contract IB contract
     * @param order    IB order
     * @return Mono with a IB result
     *
     * @implNote TWS API requires every new order has to have unique and incremented {@link com.ib.client.Order#orderId}
     * that is build based on "nextValidId" returned on API at connect.
     * {@code ib-client} makes this step optional. If {@link com.ib.client.Order#orderId} is not assigned or zero,
     * library generates ID by itself. In some cases Developer need to define ID explicitly, then he is able to
     * generate new value with {@link #nextOrderId}
     * @see <a href="https://interactivebrokers.github.io/tws-api/order_submission.html">
     * TWS API: Placing Orders</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/interfaceIBApi_1_1EWrapper.html#a09c07727efd297e438690ab42838d332">
     * TWS API: nextValidId</a>
     * @see #nextOrderId
     */
    public Mono<IbOrder> placeOrder(Contract contract, Order order) {
        Validators.shouldNotBeNull(contract, "Contract should be defined");
        Validators.shouldNotBeNull(order, "Order should be defined");

        if (order.orderId() == 0) {
            order.orderId(idGenerator.nextId());
        }

        return requests.<IbOrder>builder()
                .id(order.orderId())
                .type(RequestRepository.Type.REQ_ORDER_PLACE)
                .register(id -> socket.placeOrder(id, contract, order))
                .subscribe()
                .single();
    }

    /**
     * Cancels an order.
     *
     * @param orderId order ID. See {@link com.ib.client.Order#orderId}
     * @return Mono
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
    public Mono<Void> cancelOrder(int orderId) {
        log.info("Canceling order {}", orderId);

        // Checking does order doesn't already filled or canceled
        Mono<Void> preconditions = Mono.create(emitter -> {
            IbOrder order = cache.getOrders().getOrDefault(orderId, null);
            if (order == null) {
                emitter.success();
                return;
            }

            IbOrderStatus lastStatus = order.getLastStatus();
            if (lastStatus.isFilled()) {
                emitter.error(new IbExceptions.OrderAlreadyFilledError(orderId));
                return;
            }
            if (lastStatus.isCanceled()) {
                log.warn("Order {} already has been canceled", orderId);
                emitter.success();
                return;
            }

            emitter.success();
        });

        Mono<Void> cancelRequest = requests.builder()
                                           .id(orderId)
                                           .type(RequestRepository.Type.REQ_ORDER_CANCEL)
                                           .register(id -> socket.cancelOrder(id))
                                           .subscribe()
                                           .then();

        return preconditions.then(cancelRequest);
    }

    /**
     * Cancels all opened orders.
     *
     * @return Mono
     *
     * @implNote To provide result, library uses a bit tangled logic to wait until all opened orders will be canceled.
     * Some corner case are possible.
     * @see <a href="https://interactivebrokers.github.io/tws-api/cancel_order.html">
     * TWS API: Cancelling Orders</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#a66ad7a4820c5be21ebde521d59a50053">
     * TWS API: reqGlobalCancel</a>
     */
    public Mono<Void> cancelAll() {


        Flux<IbOrder> openedOrder = reqOpenOrders().filter(order -> !order.getLastStatus().isCanceled() &&
                                                          !order.getLastStatus().isFilled() &&
                                                          !order.getLastStatus().isInactive());

        Flux<IbOrderStatus> cancelAllAndWait = subscribeOnOrderNewStatus()
                .doOnSubscribe(unused -> socket.reqGlobalCancel())
                .filter(IbOrderStatus::isCanceled)
                .share();

        return openedOrder
                .buffer()
                .flatMap(Flux::fromIterable)
                .flatMap(order -> {
                    return cancelAllAndWait.filter(status -> status.getOrderId() == order.getOrderId()).take(1);
                }).then();
    }

    /**
     * Requests for open orders.
     *
     * @return Flux with orders. Completes as TWS sends all orders.
     *
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#aa2c9a012884dd53311a7a7c6a326306c">
     * TWS API: reqOpenOrders</a>
     */
    public Flux<IbOrder> reqOpenOrders() {
        return requests.<List<IbOrder>>builder()
                .type(RequestRepository.Type.REQ_ORDER_LIST)
                .register(() -> socket.reqOpenOrders())
                .subscribe()
                .flatMap(Flux::fromIterable);
    }

    /**
     * Requests status updates about future orders placed from TWS. Can only be used with client ID 0.
     *
     * @param autoBind if set to true, the newly created orders will be assigned an API order ID and implicitly
     *                 associated with this client. If set to false, future orders will not be.
     * @see <a href="https://interactivebrokers.github.io/tws-api/open_orders.html#manually_submitted">
     * TWS API: Manually submitted orders</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#abe8e30367fff33b9a1171a4580029016">
     * TWS API: reqAutoOpenOrders</a>
     */
    public void reqAutoOpenOrders(boolean autoBind) {
        socket.reqAutoOpenOrders(autoBind);
    }

    /**
     * Requests for contract details.
     *
     * @param contract IB contract
     * @return Flux with contract details. Completes as soon TWS sends all data.
     *
     * @implNote Note that IB can return more then one ContractDetails.
     * @see <a href="https://interactivebrokers.github.io/tws-api/contract_details.html">
     * TWS API: Requesting Contract Details</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#ade440c6db838b548594981d800ea5ca9">
     * TWS API: reqContractDetails</a>
     */
    public Flux<ContractDetails> reqContractDetails(Contract contract) {
        Validators.shouldNotBeNull(contract, "Contract should be defined");

        return requests.<ContractDetails>builder()
                .type(RequestRepository.Type.REQ_CONTRACT_DETAIL)
                .register(id -> socket.reqContractDetails(id, contract))
                .subscribe();
    }


    /**
     * Requests for contract descriptions.
     *
     * @param pattern IB contract name pattern
     * @return Flux with contract descriptions. Completes as soon TWS sends all data.
     *
     * @see <a href="https://interactivebrokers.github.io/tws-api/matching_symbols.html">
     * TWS API: Stock Contract Search</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#aa0bff193c5cbaa73e89dccdd0f027195">
     * TWS API: reqMatchingSymbols</a>
     */
    public Flux<IbContractDescription> reqMatchingSymbols(String pattern) {
        return requests.<IbContractDescription>builder()
                .type(RequestRepository.Type.REQ_CONTRACT_DESCRIPTION)
                .register(id -> socket.reqMatchingSymbols(id, pattern))
                .subscribe();
    }

    /**
     * Returns historical midpoint for specific time period.
     *
     * @param contract IB contract
     * @param from     Period start. Uses TWS timezone specified at login. Cannot be used with "<strong>to</strong>"
     * @param to       Period end. Uses TWS timezone specified at login. Cannot be used with "<strong>from</strong>"
     * @param limit    Number of distinct data points. Max is 1000 per request.
     * @return Flux with historical data. Completes as soon IB sends all data.
     *
     * @see <a href="https://interactivebrokers.github.io/tws-api/historical_time_and_sales.html">
     * TWS API: Historical Time and Sales Data</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#a99dbcf80c99b8f262a524de64b2f9c1e">
     * TWS API: reqHistoricalTicks</a>
     * @see #reqHistoricalBidAsks
     * @see #reqHistoricalTrades
     */
    public Flux<HistoricalTick> reqHistoricalMidpoints(Contract contract,
                                                       LocalDateTime from,
                                                       LocalDateTime to,
                                                       Integer limit) {
        return reqHistoricalTicks(contract, from, to, limit,
                                  RequestRepository.Type.REQ_HISTORICAL_MIDPOINT_TICK, "MIDPOINT");
    }

    /**
     * Returns historical ticks for specific time period.
     *
     * @param contract IB contract
     * @param from     Period start. Uses TWS timezone specified at login. Cannot be used with "<strong>to</strong>"
     * @param to       Period end. Uses TWS timezone specified at login. Cannot be used with "<strong>from</strong>"
     * @param limit    Number of distinct data points. Max is 1000 per request.
     * @return Flux with historical data. Completes as soon IB sends all data.
     *
     * @see <a href="https://interactivebrokers.github.io/tws-api/historical_time_and_sales.html">
     * TWS API: Historical Time and Sales Data</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#a99dbcf80c99b8f262a524de64b2f9c1e">
     * TWS API: reqHistoricalTicks</a>
     * @see #reqHistoricalMidpoints
     * @see #reqHistoricalTrades
     */
    public Flux<HistoricalTickBidAsk> reqHistoricalBidAsks(Contract contract,
                                                           LocalDateTime from,
                                                           LocalDateTime to,
                                                           Integer limit) {
        return reqHistoricalTicks(contract, from, to, limit,
                                  RequestRepository.Type.REQ_HISTORICAL_BID_ASK_TICK, "BID_ASK");
    }

    /**
     * Returns historical trades for specific time period.
     *
     * @param contract IB contract
     * @param from     Period start. Uses TWS timezone specified at login. Cannot be used with "<strong>to</strong>"
     * @param to       Period end. Uses TWS timezone specified at login. Cannot be used with "<strong>from</strong>"
     * @param limit    Number of distinct data points. Max is 1000 per request.
     * @return Flux with historical data. Completes as soon IB sends all data.
     *
     * @apiNote Note that IB doesn't return trades for Forex contracts, i.e. for contracts with
     * {@link Contract#secType()} is {@link  Types.SecType#CASH}. Calling respective IB API method
     * {@link EClient#reqHistoricalTicks} with parameter {@code whatToShow=TRADES} and Forex contract,
     * it was expected that {@link EWrapper#historicalTicksLast} callback will be called,
     * as it described in documentation. Instead {@link EWrapper#historicalTicks}callback is called,
     * the same way as if you call {@link #reqHistoricalMidpoints} with {@code whatToShow=MIDPOINT}.
     * For stocks this request behaves as expected. IB support told "it's a special case for Forex".
     *
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
    public Flux<HistoricalTickLast> reqHistoricalTrades(Contract contract,
                                                        LocalDateTime from,
                                                        LocalDateTime to,
                                                        Integer limit) {

        if (contract.secType() == Types.SecType.CASH) {
            throw new IllegalArgumentException("IB doesn't return historical trades for Forex contracts");
        }

        return reqHistoricalTicks(contract, from, to, limit,
                                  RequestRepository.Type.REQ_HISTORICAL_TRADE, "TRADES");
    }

    /**
     * Request for historical bars (aka candles).
     *
     * @param contract     IB contract
     * @param endDateTime  End date and time
     * @param duration     The amount of time to go back from the request's given end date and time.
     * @param durationUnit <strong>duration</strong> unit
     * @param size         Valid Bar Sizes
     * @param type         The type of data to retrieve
     * @param tradingHours Whether ({@link TradingHours#Within}) or not ({@link TradingHours#Outside}) to retrieve
     *                     data generated only within Regular Trading Hours
     * @return Flux with the bars. Completes as soon all data per period will be received.
     * Can emit errors: IbExceptions.NoPermissions on "No market data permissions" message,
     * Exception - for possible unknown messages
     *
     * @see <a href="https://interactivebrokers.github.io/tws-api/historical_bars.html">
     * TWS API: Historical Bar Data</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#a5eac5b7908b62c224985cf8577a8350c">
     * TWS API: reqHistoricalData</a>
     * @see #subscribeOnHistoricalData
     */
    public Flux<IbBar> reqHistoricalData(Contract contract, LocalDateTime endDateTime,
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
     * Request for historical bars (aka candles) and subscription for actual unfinished candle.
     *
     * @param contract     IB contract
     * @param duration     The amount of time to go back from the request's given end date and time.
     * @param durationUnit <strong>duration</strong>
     * @param size         Valid Bar Sizes. Must be above or equals 5 seconds.
     * @param type         The type of data to retrieve
     * @param tradingHours Whether ({@link IbClient.TradingHours#Within}) or not ({@link IbClient.TradingHours#Outside})
     *                     to retrieve
     *                     data generated only within Regular Trading Hours
     * @return Flux with the bars. Never completes. Flow is: 1st historical bars are goes, then one
     * {@link IbBar#COMPLETE}, and then constant updates of actual candle. Can emit errors:
     * IbExceptions.NoPermissions on "No market data permissions" message, Exception - for possible unknown messages
     *
     * @implNote Function is similar to {@link #reqHistoricalData}, it got all historical data as
     * {@link #reqHistoricalData} do, but then emits {@link IbBar#COMPLETE} as a separator,
     * and then continue emit actual candle updates
     * @see <a href="https://interactivebrokers.github.io/tws-api/historical_bars.html">
     * TWS API: Historical Bar Data</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/classIBApi_1_1EClient.html#a5eac5b7908b62c224985cf8577a8350c">
     * TWS API: reqHistoricalData</a>
     * @see #reqHistoricalData
     */
    public Flux<IbBar> subscribeOnHistoricalData(Contract contract,
                                                 int duration, DurationUnit durationUnit,
                                                 BarSize size,
                                                 Type type, TradingHours tradingHours) {

        if (size == BarSize.SEC_1) {
            return Flux.error(new Exception("Too small bar size. Must be >= 5 sec"));
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
     * Subscribes to filled order execution info.
     *
     * @return Flux with {@link IbExecutionReport}. Never completes. Object contains wrapped data from
     * both related callbacks {@link Wrapper#execDetails(int, com.ib.client.Contract, com.ib.client.Execution)} and
     * {@link Wrapper#commissionReport(com.ib.client.CommissionReport)}
     *
     * @see <a href="https://interactivebrokers.github.io/tws-api/executions_commissions.html">
     * TWS API: Executions and Commissions</a>
     * @see
     * <a href="https://interactivebrokers.github.io/tws-api/interfaceIBApi_1_1EWrapper.html#a7ebfc18d5d03189ab5bf895db4a1a204">
     * TWS API: commissionReport</a>
     * <a href="https://interactivebrokers.github.io/tws-api/interfaceIBApi_1_1EWrapper.html#a09f82de3d0666d13b00b5168e8b9313d">
     * TWS API: execDetails</a>
     */
    public Flux<IbExecutionReport> subscribeOnExecutionReport() {
        return requests.<IbExecutionReport>builder()
                .type(RequestRepository.Type.EVENT_EXECUTION_INFO)
                .register(() -> {
                })
                .subscribe();
    }

    private <T> Flux<T> reqHistoricalTicks(Contract contract,
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
                .flatMap(Flux::fromIterable);
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
     * Type of market data feed.
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
        SEC_1("1 secs"),
        SEC_5("5 secs"),
        SEC_10("10 secs"),
        SEC_15("15 secs"),
        SEC_30("30 secs"),
        MIN_1("1 min"),
        MIN_2("2 mins"),
        MIN_3("3 mins"),
        MIN_5("5 mins"),
        MIN_10("10 mins"),
        MIN_15("15 mins"),
        MIN_20("20 mins"),
        MIN_30("30 mins"),
        HOUR_1("1 hour"),
        HOUR_2("2 hours"),
        HOUR_3("3 hours"),
        HOUR_4("4 hours"),
        HOUR_8("8 hours"),
        DAY_1("1 day"),
        WEEK_1("1 week"),
        MONTH_1("1 month");

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
        TRADES,         // First traded price
        MIDPOINT,       // Starting midpoint price
        BID,            // Starting bid price
        ASK,            // Starting ask price
        BID_ASK,        // Time average bid
        ADJUSTED_LAST,  // Dividend-adjusted first traded price
        HISTORICAL_VOLATILITY, // Starting volatility
        OPTION_IMPLIED_VOLATILITY, // Starting implied volatility
        REBATE_RATE,    // Starting rebate rate
        FEE_RATE,       // Starting fee rate
        YIELD_BID,      // Starting bid yield
        YIELD_ASK,      // Starting ask yield
        YIELD_BID_ASK,  // Time average bid yield
        YIELD_LAST,     // Starting last yield
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
