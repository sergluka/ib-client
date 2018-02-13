package lv.sergluka.tws;

import com.google.common.collect.ImmutableSet;
import com.ib.client.*;
import lv.sergluka.tws.impl.ConnectionMonitor;
import lv.sergluka.tws.impl.TwsReader;
import lv.sergluka.tws.impl.Wrapper;
import lv.sergluka.tws.impl.cache.CacheRepository;
import lv.sergluka.tws.impl.sender.RequestRepository;
import lv.sergluka.tws.impl.subscription.SubscriptionsRepository;
import lv.sergluka.tws.impl.subscription.TwsSubscription;
import lv.sergluka.tws.impl.subscription.TwsSubscriptionPromise;
import lv.sergluka.tws.impl.types.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public class TwsClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TwsClient.class);

    private TwsReader reader;
    private Wrapper wrapper;
    private EClientSocket socket;

    private RequestRepository requests;
    private CacheRepository cache;
    private ConnectionMonitor connectionMonitor;
    private IdGenerator idGenerator;

    protected SubscriptionsRepository subscriptions;

    public TwsClient() {
        this(Executors.newCachedThreadPool());
    }

    public TwsClient(ExecutorService subscriptionsExecutor) {
        super();
        idGenerator = new IdGenerator();
        subscriptions = new SubscriptionsRepository(subscriptionsExecutor, idGenerator);
    }

    public CacheRepository getCache() {
        return cache;
    }

    public void connect(final @NotNull String ip, final int port, final int connId) {
        log.debug("Connecting to {}:{}, id={} ...", ip, port, connId);

        if (isConnected()) {
            log.warn("Already is connected");
            return;
        }

        requests = new RequestRepository(this);
        cache = new CacheRepository();

        connectionMonitor = new ConnectionMonitor() {

            @Override
            protected void connectRequest(boolean reconnect) {
                init();
                socket.setAsyncEConnect(false);
                socket.eConnect(ip, port, connId);
                socket.setServerLogLevel(5); // TODO
            }

            @Override
            protected void disconnectRequest(boolean reconnect) {
                if (!reconnect) {
                    subscriptions.close();
                }
                socket.eDisconnect();
                reader.close();
            }

            @Override
            protected void afterConnect() {
                subscriptions.resubscribe();
            }

            @Override
            protected void onStatusChange(Status status) {
                subscriptions.eventOnData(SubscriptionsRepository.EventType.EVENT_CONNECTION_STATUS, status, false);
            }
        };

        wrapper = new Wrapper(connectionMonitor, socket, reader, cache, subscriptions, idGenerator);

        connectionMonitor.start();
        connectionMonitor.connect();
        // TODO return Future
    }

    @Override
    public void close() {
        disconnect();
    }

    public void waitForConnect(long time, TimeUnit unit) throws TimeoutException {
        connectionMonitor.waitForStatus(ConnectionMonitor.Status.CONNECTED, time, unit);
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

    @NotNull
    public TwsSubscription subscribeOnOrderNewStatus(Consumer<TwsOrderStatus> callback) {
        return subscriptions.addUnique(SubscriptionsRepository.EventType.EVENT_ORDER_STATUS, callback, null, null);
    }

    public synchronized TwsSubscriptionPromise<ImmutableSet<TwsPosition>> subscribeOnPositionChange(Consumer<TwsPosition> callback) {
        return subscriptions.addPromiseUnique(SubscriptionsRepository.EventType.EVENT_POSITION, callback,
                                              (unused) -> {
                                                  shouldBeConnected();
                                                  return requests.postSingleRequest(RequestRepository.Event.REQ_POSITIONS,
                                                                                    null,
                                                                                    () -> socket.reqPositions());
                                              },
                                              (unused) -> {
                                                  shouldBeConnected();
                                                  socket.cancelPositions();
                                              });
    }

    public Set<String> getManagedAccounts() {
        return wrapper.getManagedAccounts();
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

    public synchronized void reqMarketDataType(MarketDataType type) {
        socket.reqMarketDataType(type.getValue());
    }

    public synchronized CompletableFuture<TwsTick> reqMktDataSnapshot(Contract contract) {
        shouldBeConnected();

        int tickerId = idGenerator.nextRequestId();
        return requests.postSingleRequest(RequestRepository.Event.REQ_MARKET_DATA,
                                          tickerId,
                                          () -> socket.reqMktData(tickerId, contract, "", true, false, null));
    }

    public synchronized TwsSubscription subscribeOnMarketData(Contract contract, Consumer<TwsTick> callback) {
        return subscriptions.add(SubscriptionsRepository.EventType.EVENT_MARKET_DATA, callback,
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

    public synchronized TwsSubscription subscribeOnContractPnl(int contractId,
                                                               String account,
                                                               Consumer<TwsPnl> callback) {
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

    public synchronized TwsSubscription subscribeOnAccountPnl(@NotNull String account, Consumer<TwsPnl> callback) {
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

    public synchronized TwsSubscriptionPromise<ImmutableSet<TwsPortfolio>> subscribeOnAccountPortfolio(
            @NotNull String account,
            @NotNull Consumer<TwsPortfolio> callback) {

        Objects.requireNonNull(account, "'account' parameter is null");
        Objects.requireNonNull(callback, "'callback' parameter is null");

        if (account.isEmpty()) {
            throw new IllegalArgumentException("'account' parameter is empty");
        }

        return subscriptions.addPromiseUnique(SubscriptionsRepository.EventType.EVENT_PORTFOLIO, callback,
                                              (unused) -> {
                                                  shouldBeConnected();
                                                  return requests.postSingleRequest(RequestRepository.Event.REQ_PORTFOLIO,
                                                                                    null,
                                                                                    () -> socket.reqAccountUpdates(true,
                                                                                                                   account));
                                              },
                                              (unused) -> {
                                                  shouldBeConnected();
                                                  socket.reqAccountUpdates(false, account);
                                              });
    }

    public synchronized TwsSubscriptionPromise setOnConnectionStatus(Consumer<ConnectionMonitor.Status> callback) {
        return subscriptions.addPromiseUnique(SubscriptionsRepository.EventType.EVENT_CONNECTION_STATUS,
                                              callback,
                                              null,
                                              null);
    }

    public int nextOrderId() {
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
    public synchronized CompletableFuture<TwsOrder> placeOrder(@NotNull Contract contract, @NotNull Order order) {
        shouldBeConnected();

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
    public synchronized CompletableFuture<List<TwsOrder>> reqAllOpenOrders() {
        shouldBeConnected();

        final Integer id = idGenerator.nextRequestId();
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

    private void init() {
        EJavaSignal signal = new EJavaSignal();

        socket = new EClientSocket(wrapper, signal);
        reader = new TwsReader(socket, signal);
    }

    private void shouldBeConnected() {
        if (!isConnected()) {
            throw new TwsExceptions.NotConnected();
        }
    }
}
