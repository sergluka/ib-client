package lv.sergluka.tws;

import com.ib.client.*;
import lv.sergluka.tws.impl.ConnectionMonitor;
import lv.sergluka.tws.impl.TwsReader;
import lv.sergluka.tws.impl.promise.TwsPromise;
import lv.sergluka.tws.impl.sender.EntriesRepository;
import lv.sergluka.tws.impl.sender.RequestRepository;
import lv.sergluka.tws.impl.types.TwsOrder;
import lv.sergluka.tws.impl.types.TwsOrderStatus;
import lv.sergluka.tws.impl.types.TwsPosition;
import lv.sergluka.tws.impl.types.TwsTick;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public class TwsClient extends TwsWrapper implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TwsClient.class);
    private static final int INVALID_ID = -1;
    protected static RequestRepository requests;
    final ExecutorService executors;

    private EClientSocket socket;
    protected TwsReader reader;
    protected EntriesRepository repository;
    protected ConnectionMonitor connectionMonitor;

    //    private AtomicInteger requestId = new AtomicInteger(0);
    private AtomicInteger orderId;

    protected BiConsumer<Integer, TwsOrderStatus> onOrderStatus;
    protected Consumer<TwsPosition> onPosition;
    protected Consumer<TwsTick> onMarketData;
    protected Consumer<TwsTick> onMarketDepth;
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

    AtomicInteger getOrderId() {
        return orderId;
    }

    @Override
    public void close() throws Exception {
        connectionMonitor.disconnect();
        connectionMonitor.close();
    }

    public void connect(final @NotNull String ip, final int port, final int connId) {
        log.debug("Connecting...");

        if (isConnected()) {
            log.warn("Already is connected");
            return;
        }

        requests = new RequestRepository(this);
        setRequests(requests);

        repository = new EntriesRepository();

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

    public void waitForConnect() {
        connectionMonitor.waitForStatus(ConnectionMonitor.Status.CONNECTED);
    }

    public void disconnect() {
        log.debug("Disconnecting...");
        connectionMonitor.disconnect();
        log.info("Disconnected");
    }

    public boolean isConnected() {
        return socket != null &&
                socket.isConnected() &&
                connectionMonitor.status() == ConnectionMonitor.Status.CONNECTED;
    }

    public void subscribeOnOrderStatus(BiConsumer<Integer, TwsOrderStatus> callback) {
        onOrderStatus = callback;
    }

    public void subscribeOnPosition(Consumer<TwsPosition> callback) {
        shouldBeConnected();
        onPosition = callback;
        socket.reqPositions();
    }

    public void unsubscribeOnPosition() {
        shouldBeConnected();
        socket.cancelPositions();
        onPosition = null;
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
        return requests.postSingleRequest(RequestRepository.Event.REQ_MAKET_DATA_SNAPSHOT,
                tickerId, () -> socket.reqMktData(tickerId, contract, "", true, false, null), null);
    }

    public synchronized int subscribeOnMarketDepth(Contract contract, int depth, Consumer<TwsTick> callback) {
        shouldBeConnected();
        onMarketDepth = callback;

        int tickerId = nextOrderId();
        final TwsPromise<Object> promise = requests.postSingleRequest(
                RequestRepository.Event.REQ_MAKET_DEPTH,
                tickerId, () -> socket.reqMktDepth(tickerId, contract, depth, null), null);
        promise.get();
        return tickerId;
    }

    public synchronized void unsubscribeOnMarketDepth(int tickerId) {
        shouldBeConnected();
        socket.cancelMktDepth(tickerId);
        onMarketDepth = null;
    }

    public synchronized void setOnConnectionStatus(Consumer<ConnectionMonitor.Status> callback) {
        onConnectionStatus = callback;
    }

    public int nextOrderId() {
        if (orderId.get() == INVALID_ID) {
            throw new IllegalStateException("Has no request ID from TWS");
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
        socket.cancelOrder(orderId);
    }

    public synchronized void reqGlobalCancel(int orderId) {
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
