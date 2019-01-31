package com.finplant.ib;

import com.finplant.ib.impl.IbReader;
import com.finplant.ib.impl.Wrapper;
import com.finplant.ib.impl.cache.CacheRepository;
import com.finplant.ib.impl.cache.CacheRepositoryImpl;
import com.finplant.ib.impl.connection.ConnectionMonitor;
import com.finplant.ib.impl.request.RequestRepository;
import com.finplant.ib.impl.types.*;
import com.ib.client.*;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IbClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(IbClient.class);

    private IbReader reader;
    private Wrapper wrapper;
    private EClientSocket socket;
    private CacheRepositoryImpl cache;
    private ConnectionMonitor connectionMonitor;
    private IdGenerator idGenerator;
    private RequestRepository requests;


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
                protected void connectRequest(boolean reconnect) {

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
                    socket.eDisconnect();
                    reader.close();
                    if (!reconnect) {
                        cache.clear();
                    }
                }

                @Override
                protected void afterConnect() {
                    socket.setServerLogLevel(IbClient.LogLevel.DETAIL.ordinal());
                    emitter.onComplete();
                }

                @Override
                protected void onConnectStatusChange(Boolean status) {
                    requests.onNext(RequestRepository.Type.EVENT_CONNECTION_STATUS, null, status, false);
                }
            };

            wrapper = new Wrapper(connectionMonitor, cache, requests, idGenerator);

            connectionMonitor.start();
            connectionMonitor.connect();

        }).doOnError(e -> connectionMonitor.close());
    }

    public void disconnect() {
        log.debug("Disconnecting...");
        requests.close();
        connectionMonitor.close();
        log.info("Disconnected");
    }

    public boolean isConnected() {
        return socket != null &&
               socket.isConnected() &&
               connectionMonitor.status() == ConnectionMonitor.Status.CONNECTED;
    }

    public Set<String> getManagedAccounts() {
        return wrapper.getManagedAccounts();
    }

    @NotNull
    public Observable<IbOrderStatus> subscribeOnOrderNewStatus() {
        return requests.<IbOrderStatus>builder()
              .type(RequestRepository.Type.EVENT_ORDER_STATUS)
              .register(() -> {}) // statuses are received without registration
              .subscribe();
    }

    public Observable<IbPosition> subscribeOnPositionChange() {
        return requests.<IbPosition>builder()
              .type(RequestRepository.Type.EVENT_POSITION)
              .register(() -> socket.reqPositions())
              .unregister(() -> socket.cancelPositions())
              .subscribe();
    }

    public Observable<IbPosition> subscribeOnPositionChange(String account) {
        Validators.stringShouldNotBeEmpty(account, "Account should be defined");

        return requests.<IbPosition>builder()
              .type(RequestRepository.Type.EVENT_POSITION_MULTI)
              .register(id -> socket.reqPositionsMulti(id, account, ""))
              .unregister(id -> socket.cancelPositionsMulti(id))
              .subscribe();
    }

    public void setMarketDataType(MarketDataType type) {
        Validators.shouldNotBeNull(type, "Type should be defined");

        socket.reqMarketDataType(type.getValue());
    }

    public Single<IbTick> reqMktData(Contract contract) {
        Validators.shouldNotBeNull(contract, "Contract should be defined");

        return requests.<IbTick>builder()
              .type(RequestRepository.Type.REQ_MARKET_DATA)
              .register(id -> socket.reqMktData(id, contract, "", true, false, null))
              .unregister(id -> socket.cancelPositionsMulti(id))
              .subscribe()
              .firstOrError();
    }

    public Observable<IbDepthMktDataDescription> reqMktDepthExchanges() {

        return requests.<List<IbDepthMktDataDescription>>builder()
              .type(RequestRepository.Type.REQ_MARKET_DEPTH_EXCHANGES)
              .register(() -> socket.reqMktDepthExchanges())
              .subscribe()
              .flatMap(Observable::fromIterable);
    }

    /**
     * Get current order book snapshot.
     *
     * Since there doesn't exist single request function in IB API for recent data receiving only, we subscribe to
     * order book (aka market data lvl 2) update stream, and as soon full book is received, stop subscription
     *
     * @param contract IB contract
     * @param numRows Order book max depth
     * @return Observable with order book levels
     */
    public Observable<IbMarketDepth> getMarketDepth(Contract contract, int numRows) {
        Validators.contractWithIdShouldExist(contract);
        Validators.intShouldBePositive(numRows, "Number of rows should be positive");

        return requests.<IbMarketDepth>builder()
              .type(RequestRepository.Type.EVENT_MARKET_DATA_LVL2)
              .register(id -> socket.reqMktDepth(id, contract, numRows, false, null))
              .unregister(id -> socket.cancelMktDepth(id, false))
              .userData(contract)
              .subscribe();
    }

    public Observable<IbTick> subscribeOnMarketData(Contract contract) {
        Validators.contractWithIdShouldExist(contract);

        return requests.<IbTick>builder()
              .type(RequestRepository.Type.EVENT_MARKET_DATA)
              .register(id -> socket.reqMktData(id, contract, "", false, false, null))
              .unregister(id -> socket.cancelMktData(id))
              .subscribe();
    }

    public Observable<IbPnl> subscribeOnContractPnl(int contractId, String account) {
        Validators.intShouldBePositive(contractId, "Contract ID should be positive");
        Validators.stringShouldNotBeEmpty(account, "Account should be defined");

        return requests.<IbPnl>builder()
              .type(RequestRepository.Type.EVENT_CONTRACT_PNL)
              .register(id -> socket.reqPnLSingle(id, account, "", contractId))
              .unregister(id -> socket.cancelPnLSingle(id))
              .subscribe();
    }

    public Observable<IbPnl> subscribeOnAccountPnl(String account) {
        Validators.stringShouldNotBeEmpty(account, "Account should be defined");

        return requests.<IbPnl>builder()
              .type(RequestRepository.Type.EVENT_ACCOUNT_PNL)
              .register(id -> socket.reqPnL(id, account, ""))
              .unregister(id -> socket.cancelPnL(id))
              .subscribe();
    }

    public Observable<IbPortfolio> subscribeOnAccountPortfolio(String account) {
        Validators.stringShouldNotBeEmpty(account, "Account should be defined");

        return requests.<IbPortfolio>builder()
              .type(RequestRepository.Type.EVENT_PORTFOLIO)
              .register(() -> socket.reqAccountUpdates(true, account))
              .unregister(() -> socket.reqAccountUpdates(false, account))
              .subscribe();
    }

    public Observable<Boolean> connectionStatus() {
        return requests.<Boolean>builder()
              .type(RequestRepository.Type.EVENT_CONNECTION_STATUS)
              .register(() -> {}) // statuses are received without registration
              .subscribe();
    }

    public Single<Long> getCurrentTime() {
        return requests.<Long>builder()
              .type(RequestRepository.Type.REQ_CURRENT_TIME)
              .register(() -> socket.reqCurrentTime())
              .subscribe()
              .firstOrError();
    }

    public Single<IbOrder> placeOrder(Contract contract, Order order) {
        Validators.shouldNotBeNull(contract, "Contract should be defined");
        Validators.shouldNotBeNull(order, "Order should be defined");

        if (order.orderId() == 0) {
            order.orderId(idGenerator.nextOrderId());
        }

        return requests.<IbOrder>builder()
              .id(order.orderId())
              .type(RequestRepository.Type.REQ_ORDER_PLACE)
              .register(id -> socket.placeOrder(id, contract, order))
              .subscribe()
              .firstOrError();
    }

    public Completable cancelOrder(int orderId) {
        Validators.intShouldBePositive(orderId, "Order ID should be positive");

        log.info("Canceling order {}", orderId);

        return requests.<Boolean>builder()
              .id(orderId)
              .type(RequestRepository.Type.REQ_ORDER_CANCEL)
              .register(id -> socket.cancelOrder(id))
              .subscribe()
              .ignoreElements();
    }

    public Completable cancelAll() {

        return Observable.create(emitter -> {
            Integer sumOfOrders = reqAllOpenOrders()
                  .flatMapObservable(map -> Observable.fromIterable(map.entrySet()))
                  .filter(entry -> !entry.getValue().getLastStatus().isCanceled() &&
                                   !entry.getValue().getLastStatus().isFilled() &&
                                   !entry.getValue().getLastStatus().isInactive())
                  .map(Map.Entry::getKey)
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

    public Single<Map<Integer, IbOrder>> reqAllOpenOrders() {
        return requests.<Map<Integer, IbOrder>>builder()
              .type(RequestRepository.Type.REQ_ORDER_LIST)
              .register(() -> socket.reqAllOpenOrders())
              .subscribe()
              .firstOrError();
    }

    @NotNull
    public Observable<ContractDetails> reqContractDetails(Contract contract) {
        Validators.shouldNotBeNull(contract, "Contract should be defined");

        return requests.<ContractDetails>builder()
              .type(RequestRepository.Type.REQ_CONTRACT_DETAIL)
              .register(id -> socket.reqContractDetails(id, contract))
              .subscribe();
    }

    @NotNull
    public Observable<HistoricalTick> reqHistoricalMidpoints(@NotNull Contract contract,
                                                             @Nullable LocalDateTime from,
                                                             @Nullable LocalDateTime to,
                                                             @Nullable Integer limit) {
        return reqHistoricalTicks(contract, from, to, limit,
                                  RequestRepository.Type.REQ_HISTORICAL_MIDPOINT_TICK, "MIDPOINT");
    }

    @NotNull
    public Observable<HistoricalTickBidAsk> reqHistoricalBidAsks(@NotNull Contract contract,
                                                                 @Nullable LocalDateTime from,
                                                                 @Nullable LocalDateTime to,
                                                                 @Nullable Integer limit) {
        return reqHistoricalTicks(contract, from, to, limit,
                                  RequestRepository.Type.REQ_HISTORICAL_BID_ASK_TICK, "BID_ASK");
    }

    @NotNull
    public Observable<HistoricalTickLast> reqHistoricalTrades(@NotNull Contract contract,
                                                              @Nullable LocalDateTime from,
                                                              @Nullable LocalDateTime to,
                                                              @Nullable Integer limit) {

        /*
         Calling reqHistoricalTicks() with whatToShow=TRADES and contract EUR.USD,
         it was expected that historicalTicksLast() callback will be called, as it described in documentation.
         Instead historicalTicks() callback is called, the same way as if to define whatToShow=MIDPOINT.

         For stocks this request behaves as expected. IB support told "it might be special case for Forex"
         */

        if (contract.secType() == Types.SecType.CASH) {
            throw new IllegalArgumentException("IB doesn't return historical trades for FOREX contracts");
        }

        return reqHistoricalTicks(contract, from, to, limit,
                                  RequestRepository.Type.REQ_HISTORICAL_TRADE, "TRADES");
    }

    public int nextOrderId() {
        return idGenerator.nextOrderId();
    }

    private void shouldBeConnected() {
        if (!isConnected()) {
            throw new IbExceptions.NotConnected();
        }
    }

    private <T> Observable<T> reqHistoricalTicks(Contract contract,
                                                 LocalDateTime from,
                                                 LocalDateTime to,
                                                 Integer limit,
                                                 RequestRepository.Type type,
                                                 String typeStr) {
        Validators.shouldNotBeNull(contract, "Contract should be defined");

        final int MAX_ALLOWED_TICKS_COUNT = 1000;
        final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss");

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

    public enum MarketDataType {
        LIVE(1),
        FROZEN(2),
        DELAYED(3),
        DELAYED_FROZEN(4);

        private int value;

        MarketDataType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
