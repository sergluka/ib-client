package lv.sergluka.ib;

import com.google.common.collect.ImmutableSet;
import com.ib.client.*;
import lv.sergluka.ib.impl.connection.ConnectionMonitor;
import lv.sergluka.ib.impl.IbReader;
import lv.sergluka.ib.impl.Wrapper;
import lv.sergluka.ib.impl.cache.CacheRepository;
import lv.sergluka.ib.impl.sender.RequestRepository;
import lv.sergluka.ib.impl.subscription.SubscriptionsRepository;
import lv.sergluka.ib.impl.subscription.IbSubscription;
import lv.sergluka.ib.impl.subscription.IbSubscriptionFuture;
import lv.sergluka.ib.impl.types.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class IbClient implements AutoCloseable {

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

    private static final Logger log = LoggerFactory.getLogger(IbClient.class);

    private IbReader reader;
    private Wrapper wrapper;
    private EClientSocket socket;

    private RequestRepository requests;
    private CacheRepository cache;
    private ConnectionMonitor connectionMonitor;
    private IdGenerator idGenerator;
    private SubscriptionsRepository subscriptions;

    public IbClient() {
        this(Executors.newWorkStealingPool());
    }

    public IbClient(ExecutorService subscriptionsExecutor) {
        super();
        idGenerator = new IdGenerator();
        subscriptions = new SubscriptionsRepository(subscriptionsExecutor, idGenerator);
    }

    public CacheRepository getCache() {
        return cache;
    }

    public CompletableFuture<Void> connect(final @NotNull String ip, final int port, final int connId) {
        log.debug("Connecting to {}:{}, id={} ...", ip, port, connId);

        CompletableFuture<Void> result = new CompletableFuture<>();

        if (isConnected()) {
            log.warn("Already is connected");
            result.complete(null);
            return result;
        }

        requests = new RequestRepository(this);
        cache = new CacheRepository();

        connectionMonitor = new ConnectionMonitor() {

            @Override
            protected void connectRequest(boolean reconnect) {

                EJavaSignal signal = new EJavaSignal();

                socket = new EClientSocket(wrapper, signal);
                wrapper.setSocket(socket);
                wrapper.setReader(reader);

                socket.setAsyncEConnect(false);
                socket.eConnect(ip, port, connId);

                reader = new IbReader(socket, signal);
                reader.start();
            }

            @Override
            protected void disconnectRequest(boolean reconnect) {
                socket.eDisconnect();
                reader.close();
            }

            @Override
            protected void afterConnect() {
                socket.setServerLogLevel(IbClient.LogLevel.DETAIL.ordinal());

                subscriptions.resubscribe();
                result.complete(null);
            }

            @Override
            protected void onConnectStatusChange(Boolean status) {
                subscriptions.eventOnData(SubscriptionsRepository.EventType.EVENT_CONNECTION_STATUS, status, false);
            }
        };

        wrapper = new Wrapper(connectionMonitor, requests, cache, subscriptions, idGenerator);

        connectionMonitor.start();
        connectionMonitor.connect();

        return result;
    }

    @Override
    public void close() {
        disconnect();
    }

    public void disconnect() {
        log.debug("Disconnecting...");
        subscriptions.close();
        connectionMonitor.close();
        log.info("Disconnected");
    }

    public boolean isConnected() {
        return socket != null &&
                socket.isConnected() &&
                connectionMonitor.status() == ConnectionMonitor.Status.CONNECTED;
    }

    public ConnectionMonitor.Status status() {
        if (connectionMonitor == null) {
            return ConnectionMonitor.Status.DISCONNECTED;
        }

        return connectionMonitor.status();
    }

    public Set<String> getManagedAccounts() {
        return wrapper.getManagedAccounts();
    }

    @NotNull
    public IbSubscription subscribeOnOrderNewStatus(Consumer<IbOrderStatus> callback) {
        return subscriptions.addUnique(SubscriptionsRepository.EventType.EVENT_ORDER_STATUS, callback, null, null);
    }

    public synchronized IbSubscriptionFuture<ImmutableSet<IbPosition>>
    subscribeOnPositionChange(Consumer<IbPosition> callback) {

        return subscriptions.addFutureUnique(SubscriptionsRepository.EventType.EVENT_POSITION, callback,
                                             (unused) -> {
                                                 shouldBeConnected();
                                                 return requests.postSingleRequest(RequestRepository.Event.REQ_POSITIONS,
                                                                                   null, () -> socket.reqPositions());
                                             },
                                             (unused) -> {
                                                 shouldBeConnected();
                                                 socket.cancelPositions();
                                             });
    }

    public synchronized IbSubscriptionFuture<ImmutableSet<IbPosition>>
    subscribeOnPositionChange(String account, Consumer<IbPosition> callback) {

        return subscriptions.addFuture(SubscriptionsRepository.EventType.EVENT_POSITION_MULTI, callback,
                                       (id) -> {
                                           shouldBeConnected();
                                           return requests.postSingleRequest(
                                                   RequestRepository.Event.REQ_POSITIONS_MULTI,
                                                   id, () -> socket.reqPositionsMulti(id, account, ""));
                                       },
                                       (id) -> {
                                           shouldBeConnected();
                                           socket.cancelPositionsMulti(id);
                                       });
    }

    public synchronized void reqMarketDataType(MarketDataType type) {
        socket.reqMarketDataType(type.getValue());
    }

    public synchronized CompletableFuture<IbTick> reqMktData(Contract contract) {
        shouldBeConnected();

        int tickerId = idGenerator.nextRequestId();
        return requests.postSingleRequest(RequestRepository.Event.REQ_MARKET_DATA_LVL1,
                                          tickerId,
                                          () -> socket.reqMktData(tickerId, contract, "", true, false, null));
    }

    public synchronized IbSubscription subscribeOnMarketData(Contract contract, Consumer<IbTick> callback) {
        return subscriptions.add(SubscriptionsRepository.EventType.EVENT_MARKET_DATA_LVL1, callback,
                                 (id) -> {
                                     shouldBeConnected();
                                     socket.reqMktData(id, contract, "", false, false, null);
                                     return null;
                                 },
                                 (id) -> {
                                     shouldBeConnected();
                                     socket.cancelMktData(id);
                                 });
    }

    /**
     * Get current order book snapshot.
     *
     * Since there doesn't exist single request function in IB API for recent data receiving only, we subscribe to
     * order book (aka market data lvl 2) update stream, and as soon full book is received, stop subscription
     *
     * @param contract  IB contract
     * @param numRows   Order book max depth
     * @return Map<depth, IbOrderBook>
     */
    public synchronized CompletableFuture<Map<IbOrderBook.Key, IbOrderBook>> getMarketDepth(Contract contract, int numRows) {
        shouldBeConnected();

        int tickerId = idGenerator.nextRequestId();
        CompletableFuture<Map<IbOrderBook.Key, IbOrderBook>> future =
              requests.postSingleRequest(RequestRepository.Event.REQ_MARKET_DATA_LVL2, tickerId,
                                         () -> socket.reqMktDepth(tickerId, contract, numRows, null));
        future.whenComplete((val, e) -> {
            log.debug("Unsubscribe from Market Depth");
            socket.cancelMktDepth(tickerId);
        });
        return future;
    }

    public synchronized IbSubscription subscribeOnContractPnl(int contractId,
                                                              String account,
                                                              Consumer<IbPnl> callback) {
        return subscriptions.add(SubscriptionsRepository.EventType.EVENT_CONTRACT_PNL, callback,
                                 (id) -> {
                                     shouldBeConnected();
                                     socket.reqPnLSingle(id, account, "", contractId);
                                     return null;
                                 },
                                 (id) -> {
                                     shouldBeConnected();
                                     socket.cancelPnLSingle(id);
                                 });
    }

    public synchronized IbSubscription subscribeOnAccountPnl(@NotNull String account, Consumer<IbPnl> callback) {
        Objects.requireNonNull(account, "'account' parameter is null");
        Objects.requireNonNull(callback, "'callback' parameter is null");

        if (account.isEmpty()) {
            throw new IllegalArgumentException("'account' parameter is empty");
        }

        return subscriptions.add(SubscriptionsRepository.EventType.EVENT_ACCOUNT_PNL, callback,
                                 (id) -> {
                                     shouldBeConnected();
                                     socket.reqPnL(id, account, "");
                                     return null;
                                 },
                                 (id) -> {
                                     shouldBeConnected();
                                     socket.cancelPnL(id);
                                 });
    }

    public synchronized IbSubscriptionFuture<ImmutableSet<IbPortfolio>> subscribeOnAccountPortfolio(
            @NotNull String account,
            @NotNull Consumer<IbPortfolio> callback) {

        Objects.requireNonNull(account, "'account' parameter is null");
        Objects.requireNonNull(callback, "'callback' parameter is null");

        if (account.isEmpty()) {
            throw new IllegalArgumentException("'account' parameter is empty");
        }

        return subscriptions.addFutureUnique(SubscriptionsRepository.EventType.EVENT_PORTFOLIO, callback,
                                             (unused) -> {
                                                 shouldBeConnected();
                                                 return requests.postSingleRequest(
                                                         RequestRepository.Event.REQ_PORTFOLIO,
                                                         null, () -> socket.reqAccountUpdates(true, account));
                                             },
                                             (unused) -> {
                                                 shouldBeConnected();
                                                 socket.reqAccountUpdates(false, account);
                                             });
    }

    public synchronized IbSubscriptionFuture subscribeOnConnectionStatus(Consumer<Boolean> callback) {
        return subscriptions.addFutureUnique(SubscriptionsRepository.EventType.EVENT_CONNECTION_STATUS,
                                             callback,
                                             null,
                                             null);
    }

    public synchronized int nextOrderId() {
        return idGenerator.nextOrderId();
    }

    @NotNull
    public synchronized CompletableFuture<Long> reqCurrentTime() {
        shouldBeConnected();
        return requests.postSingleRequest(RequestRepository.Event.REQ_CURRENT_TIME,
                                          null,
                                          () -> socket.reqCurrentTime());
    }

    @NotNull
    public synchronized CompletableFuture<IbOrder> placeOrder(@NotNull Contract contract, @NotNull Order order) {
        shouldBeConnected();

        if (order.orderId() == 0) {
            order.orderId(idGenerator.nextOrderId());
        }
        final Integer id = order.orderId();

        return requests.postSingleRequest(RequestRepository.Event.REQ_ORDER_PLACE, id,
                                          () -> socket.placeOrder(id, contract, order));
    }

    public synchronized void cancelOrder(int orderId) {
        log.info("Canceling order {}", orderId);
        socket.cancelOrder(orderId);
    }

    public synchronized void reqGlobalCancel() {
        socket.reqGlobalCancel();
    }

    @NotNull
    public synchronized CompletableFuture<List<IbOrder>> reqAllOpenOrders() {
        shouldBeConnected();

        return requests.postSingleRequest(RequestRepository.Event.REQ_ORDER_LIST, null,
                                          () -> socket.reqAllOpenOrders());
    }

    @NotNull
    public synchronized CompletableFuture<List<ContractDetails>> reqContractDetails(@NotNull Contract contract) {
        shouldBeConnected();

        final Integer id = idGenerator.nextRequestId();
        return requests.postListRequest(RequestRepository.Event.REQ_CONTRACT_DETAIL, id,
                                        () -> socket.reqContractDetails(id, contract));
    }

    private void shouldBeConnected() {
        if (!isConnected()) {
            throw new IbExceptions.NotConnected();
        }
    }
}
