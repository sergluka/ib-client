package lv.sergluka.tws;

import com.ib.client.*;
import lv.sergluka.tws.impl.ConnectionMonitor;
import lv.sergluka.tws.impl.TwsReader;
import lv.sergluka.tws.impl.promise.TwsPromise;
import lv.sergluka.tws.impl.cache.CacheRepository;
import lv.sergluka.tws.impl.sender.RequestRepository;
import lv.sergluka.tws.impl.types.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public class TwsClient extends TwsWrapper implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TwsClient.class);
    private static final int INVALID_ID = -1;

    protected static RequestRepository requests;

    protected final ExecutorService executors;
    protected TwsReader reader;
    protected CacheRepository cache;
    protected ConnectionMonitor connectionMonitor;

    protected BiConsumer<Integer, TwsOrderStatus> onOrderStatus;
    protected Consumer<TwsPosition> onPosition;
    protected Map<Integer, Consumer<TwsPnl>> onPnlPerContractMap = new LinkedHashMap<>();
    protected Map<Integer, Consumer<TwsTick>> onMarketDataMap = new LinkedHashMap<>();
    protected Map<Integer, Consumer<TwsTick>> onMarketDepthMap = new LinkedHashMap<>();
    protected Map<Integer, Consumer<TwsPnl>> onPnlPerAccountMap = new LinkedHashMap<>();
    protected Set<String> managedAccounts;

    private EClientSocket socket;
    private AtomicInteger orderId;
    private Consumer<ConnectionMonitor.Status> onConnectionStatus;

    public TwsClient() {
        this(Executors.newCachedThreadPool());
    }

    public TwsClient(ExecutorService executors) {
        super();
        this.executors = executors;
        setClient(this);
    }

    // TODO: Temporal accessors as a short term solution.
    // After all TWS client functionality will be done, should e removed
    public EClientSocket getSocket() {
        return socket;
    }

    public CacheRepository getCache() {
        return cache;
    }

    AtomicInteger getOrderId() {
        return orderId;
    }

    @Override
    public void close() throws Exception {
        executors.shutdownNow().forEach(runnable -> log.info("Thread still works at shutdown: {}", runnable));
        disconnect();
    }

    public void connect(final @NotNull String ip, final int port, final int connId) {
        log.debug("Connecting to {}:{}, id={} ...", ip, port, connId);

        if (isConnected()) {
            log.warn("Already is connected");
            return;
        }

        requests = new RequestRepository(this);
        setRequests(requests);

        cache = new CacheRepository();

        connectionMonitor = new ConnectionMonitor() {

            @Override
            protected void connectRequest() {
                init();
                socket.setAsyncEConnect(false);
                socket.eConnect(ip, port, connId);
                socket.setServerLogLevel(5); // TODO
            }

            @Override
            protected void disconnectRequest() {
                socket.eDisconnect();
                reader.close();
            }

            @Override
            protected void onStatusChange(Status status) {
                if (onConnectionStatus != null) {
                    executors.submit(() -> onConnectionStatus.accept(status));
                }
            }
        };
        connectionMonitor.start();
        connectionMonitor.connect();
    }

    public void waitForConnect(long time, TimeUnit unit) throws TimeoutException {
        connectionMonitor.waitForStatus(ConnectionMonitor.Status.CONNECTED, time, unit);
    }

    public void disconnect() {
        log.debug("Disconnecting...");
        connectionMonitor.disconnect();
        connectionMonitor.close();
        log.info("Disconnected");
    }

    public boolean isConnected() {
        return socket != null &&
                socket.isConnected() &&
                connectionMonitor.status() == ConnectionMonitor.Status.CONNECTED;
    }

    public void subscribeOnOrderNewStatus(BiConsumer<Integer, TwsOrderStatus> callback) {
        onOrderStatus = callback;
    }

    public synchronized TwsPromise subscribeOnPositionChange(Consumer<TwsPosition> callback) {
        shouldBeConnected();
        onPosition = callback;
        return requests.postSingleRequest(RequestRepository.Event.REQ_POSITIONS,
                                          null, () -> socket.reqPositions(), null);

    }

    public synchronized void unsubscribeOnPositionChange() {
        shouldBeConnected();
        socket.cancelPositions();
        onPosition = null;
    }

    public Set<String> getManagedAccounts() {
        return managedAccounts;
    }

    public enum MarketDataType {
        LIVE(1),
        FROZEN(2),
        DELAYED(3),
        DELAYED_FROZEN(4);

        private int value;

        private MarketDataType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public synchronized void reqMarketDataType(MarketDataType type) {
        socket.reqMarketDataType(type.getValue());
    }

    public synchronized TwsPromise<TwsTick> reqMktDataSnapshot(Contract contract) {
        shouldBeConnected();

        int tickerId = nextOrderId();
        return requests.postSingleRequest(RequestRepository.Event.REQ_MAKET_DATA,
                tickerId, () -> socket.reqMktData(tickerId, contract, "", true, false, null), null);
    }

    public synchronized int subscribeOnMarketData(Contract contract, Consumer<TwsTick> callback) {
        shouldBeConnected();
        int tickerId = nextOrderId();
        onMarketDataMap.put(tickerId, callback);
        socket.reqMktData(tickerId, contract, "", false, false, null);
        log.info("Subscribed to market data, ticker id = {}", tickerId);
        return tickerId;
    }

    public synchronized void unsubscribeOnMarketData(int tickerId) {
        shouldBeConnected();
        socket.cancelMktData(tickerId);
        onMarketDataMap.remove(tickerId);
    }

    public synchronized int subscribeOnMarketDepth(Contract contract, int depth, Consumer<TwsTick> callback) {
        shouldBeConnected();

        int tickerId = nextOrderId();
        onMarketDepthMap.put(tickerId, callback);
        final TwsPromise<Object> promise = requests.postSingleRequest(
                RequestRepository.Event.REQ_MAKET_DEPTH,
                tickerId, () -> socket.reqMktDepth(tickerId, contract, depth, null), null);
        promise.get();
        return tickerId;
    }

    public synchronized void unsubscribeOnMarketDepth(int tickerId) {
        shouldBeConnected();
        socket.cancelMktDepth(tickerId);
        onMarketDepthMap.remove(tickerId);
    }

    public synchronized int subscribeOnContractPnl(int contractId, String account, Consumer<TwsPnl> callback) {
        shouldBeConnected();
        int id = nextOrderId();
        onPnlPerContractMap.put(id, callback);
        socket.reqPnLSingle(id, account, "", contractId);
        return id; // TODO: Make closable object?
    }

    public synchronized void unsubscribeOnContractPnl(int requestId) {
        shouldBeConnected();
        socket.cancelPnLSingle(requestId);
        onPnlPerContractMap.remove(requestId);
    }

    // TODO: Error on re-subscribe. Do it for all managed subscriptions
    // TODO: unsubscribe on disconnect
    public synchronized int subscribeOnAccountPnl(String account, Consumer<TwsPnl> callback) {
        shouldBeConnected();
        int id = nextOrderId();
        onPnlPerAccountMap.put(id, callback);
        socket.reqPnL(id, account, "");
        return id;
    }

    public synchronized void unsubscribeOnAccountPnl(int requestId) {
        shouldBeConnected();
        socket.cancelPnL(requestId);
        onPnlPerAccountMap.remove(requestId);
    }

    public synchronized void setOnConnectionStatus(Consumer<ConnectionMonitor.Status> callback) {
        onConnectionStatus = callback;
    }

    public int nextOrderId() {
        if (orderId.get() == INVALID_ID) {
            throw new IllegalStateException("Has no request ID from TWS"); // TODO: Wait until we got one
        }

        return orderId.getAndIncrement();
    }

    @NotNull
    public synchronized TwsPromise<Long> reqCurrentTime() {
        shouldBeConnected();
        return requests.postSingleRequest(RequestRepository.Event.REQ_CURRENT_TIME,
                                          null,
                                          () -> socket.reqCurrentTime(),
                                          null);
    }

    public synchronized void reqCurrentTime(Consumer<Integer> consumer) {
        shouldBeConnected();
        requests.postSingleRequest(RequestRepository.Event.REQ_CURRENT_TIME,
                                   null,
                                   () -> socket.reqCurrentTime(),
                                   consumer);
    }

    @NotNull
    public synchronized TwsPromise<TwsOrder> placeOrder(@NotNull Contract contract, @NotNull Order order) {
        shouldBeConnected();

        final Integer id = order.orderId();
        return requests.postSingleRequest(RequestRepository.Event.REQ_ORDER_PLACE, id,
                                          () -> socket.placeOrder(id, contract, order), null);
    }

    public synchronized void cancelOrder(int orderId) {
        log.info("Canceling order {}", orderId);
        socket.cancelOrder(orderId);
    }

    public synchronized void reqGlobalCancel() {
        socket.reqGlobalCancel();
    }

    @NotNull
    public synchronized TwsPromise<List<TwsOrder>> reqAllOpenOrders() {
        shouldBeConnected();

        final Integer id = nextOrderId();
        return requests.postSingleRequest(RequestRepository.Event.REQ_ORDER_LIST, null,
                                        () -> socket.reqAllOpenOrders(), null);
    }

    @NotNull
    public synchronized TwsPromise<List<ContractDetails>> reqContractDetails(@NotNull Contract contract) {
        shouldBeConnected();

        final Integer id = nextOrderId();
        return requests.postListRequest(RequestRepository.Event.REQ_CONTRACT_DETAIL, id,
                                        () -> socket.reqContractDetails(id, contract), null);
    }

    public synchronized void reqContractDetails(@NotNull Contract contract, Consumer<List<ContractDetails>> consumer) {
        shouldBeConnected();

        final Integer id = nextOrderId();
        requests.postListRequest(RequestRepository.Event.REQ_CONTRACT_DETAIL, id,
                                 () -> socket.reqContractDetails(id, contract), consumer);
    }

    private void init() {
        orderId = new AtomicInteger(INVALID_ID);

        EJavaSignal signal = new EJavaSignal();

//        final Wrapper wrapper = new TwsWrapper(this, requests);
        socket = new EClientSocket(this, signal);
        reader = new TwsReader(socket, signal);
    }

//    private int nextRequestId() {
//        return requestId.getAndIncrement();
//    }

    private void shouldBeConnected() {
        if (!isConnected()) {
            throw new TwsExceptions.NotConnected();
        }
    }
}
