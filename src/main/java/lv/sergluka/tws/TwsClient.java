package lv.sergluka.tws;

import com.ib.client.*;
import lv.sergluka.tws.impl.ConnectionMonitor;
import lv.sergluka.tws.impl.Wrapper;
import lv.sergluka.tws.impl.TwsReader;
import lv.sergluka.tws.impl.promise.TwsPromise;
import lv.sergluka.tws.impl.sender.OrdersRepository;
import lv.sergluka.tws.impl.sender.RequestRepository;
import lv.sergluka.tws.impl.types.TwsOrderStatus;
import lv.sergluka.tws.impl.types.TwsPosition;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class TwsClient extends TwsWrapper implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TwsClient.class);
    private static final int INVALID_ID = -1;
    static private RequestRepository requests;

    private EClientSocket socket;
    private TwsReader reader;
    private OrdersRepository ordersRepository;
    private ConnectionMonitor connectionMonitor;

    private AtomicInteger requestId = new AtomicInteger(0);
    private AtomicInteger orderId;

    private BiConsumer<Integer, TwsOrderStatus> onOrderStatus;
    private Consumer<TwsPosition> onPosition;

    public TwsClient() {
        super();
        setClient(this);
    }

    // TODO: Temporall accessors as a short term solution.
    // After all TWS client functionality will be done, should e removed
    public EClientSocket getSocket() {
        return socket;
    }
    AtomicInteger getOrderId() {
        return orderId;
    }
    BiConsumer<Integer, TwsOrderStatus> getOnOrderStatus() {
        return onOrderStatus;
    }
    Consumer<TwsPosition> getOnPosition() {
        return onPosition;
    }
    ConnectionMonitor getConnectionMonitor() {
        return connectionMonitor;
    }
    TwsReader getReader() {
        return reader;
    }
    RequestRepository getRequests() {
        return requests;
    }
    OrdersRepository getOrdersRepository() {
        return ordersRepository;
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

        ordersRepository = new OrdersRepository();

        connectionMonitor = new ConnectionMonitor() {

            @Override
            protected void onConnect() {
                init();

                requests.postUncheckedRequest(RequestRepository.Event.REQ_CONNECT,
                        () -> {
                            socket.setAsyncEConnect(false);
                            socket.eConnect(ip, port, connId);
                            socket.setServerLogLevel(5); // TODO
                        });
            }

            @Override
            protected void onDisconnect() {
                socket.eDisconnect();
                reader.close();
            }
        };
        connectionMonitor.start();
        connectionMonitor.connect();
        connectionMonitor.waitForStatus(ConnectionMonitor.Status.CONNECTED);
    }

    public void disconnect() throws TimeoutException {
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
        onPosition = callback;
        socket.reqPositions();
    }

    public void unsubscribeOnPosition() {
        socket.cancelPositions();
    }

    public int nextOrderId() {
        if (orderId.get() == INVALID_ID) {
            throw new IllegalStateException("Has no request ID from TWS");
        }

        int id = orderId.getAndSet(INVALID_ID);
        socket.reqIds(-1);
        return id;
    }

    @NotNull
    public TwsPromise<Long> reqCurrentTime() {
        shouldBeConnected();
        return requests.postSingleRequest(RequestRepository.Event.REQ_CURRENT_TIME, null, () -> socket.reqCurrentTime(), null);
    }

    public void reqCurrentTime(Consumer<Integer> consumer) {
        shouldBeConnected();
        requests.postSingleRequest(RequestRepository.Event.REQ_CURRENT_TIME, null, () -> socket.reqCurrentTime(), consumer);
    }

    @NotNull
    public TwsPromise<OrderState> placeOrder(@NotNull Contract contract, @NotNull Order order) {
        shouldBeConnected();

        final Integer id = order.orderId();
        return requests.postSingleRequest(RequestRepository.Event.REQ_ORDER_PLACE, id,
                () -> socket.placeOrder(id, contract, order), null);
    }

    public void placeOrder(@NotNull Contract contract, @NotNull Order order, Consumer<OrderState> consumer) {
        shouldBeConnected();

        final Integer id = order.orderId();
        requests.postSingleRequest(RequestRepository.Event.REQ_ORDER_PLACE, id,
                () -> socket.placeOrder(id, contract, order), consumer);
    }

    @NotNull
    public TwsPromise<List<ContractDetails>> reqContractDetails(@NotNull Contract contract) {
        shouldBeConnected();

        final Integer id = nextOrderId();
        return requests.postListRequest(RequestRepository.Event.REQ_CONTRACT_DETAIL, id,
                () -> socket.reqContractDetails(id, contract), null);
    }

    public void reqContractDetails(@NotNull Contract contract, Consumer<List<ContractDetails>> consumer) {
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
