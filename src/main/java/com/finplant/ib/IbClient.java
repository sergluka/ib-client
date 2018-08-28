package com.finplant.ib;

import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.finplant.ib.impl.IbReader;
import com.finplant.ib.impl.Wrapper;
import com.finplant.ib.impl.cache.CacheRepository;
import com.finplant.ib.impl.connection.ConnectionMonitor;
import com.finplant.ib.impl.request.RequestRepository;
import com.finplant.ib.impl.types.IbOrder;
import com.finplant.ib.impl.types.IbOrderStatus;
import com.finplant.ib.impl.types.IbPnl;
import com.finplant.ib.impl.types.IbPortfolio;
import com.finplant.ib.impl.types.IbPosition;
import com.finplant.ib.impl.types.IbTick;
import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.EClientSocket;
import com.ib.client.EJavaSignal;
import com.ib.client.Order;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import lv.sergluka.Validators;

public class IbClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(IbClient.class);

    private IbReader reader;
    private Wrapper wrapper;
    private EClientSocket socket;
    private CacheRepository cache;
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
        Validators.intShouldBePositiveOrZero(connId, "Contract ID should be positive or 0");

        log.debug("Connecting to {}:{}, id={} ...", ip, port, connId);

        return Completable.create(emitter -> {

            if (isConnected()) {
                log.warn("Already is connected");
                emitter.onComplete();
                return;
            }

            cache = new CacheRepository();

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
        });
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
        return requests.addRequestWithoutId(RequestRepository.Type.EVENT_ORDER_STATUS, null, null);
    }

    // TODO: Add test
    public Observable<IbPosition> subscribeOnPositionChange() {
        return requests.addRequestWithoutId(
              RequestRepository.Type.EVENT_POSITION,
              (unused) -> socket.reqPositions(),
              (unused) -> socket.cancelPositions());
    }

    public synchronized Observable<IbPosition> subscribeOnPositionChange(String account) {
        Validators.stringShouldNotBeEmpty(account, "Account should be defined");

        return requests.addRequestWithId(RequestRepository.Type.EVENT_POSITION_MULTI,
                                         (id) -> socket.reqPositionsMulti(id, account, ""),
                                         (id) -> socket.cancelPositionsMulti(id));
    }

    public synchronized void setMarketDataType(MarketDataType type) {
        Validators.shouldNotBeNull(type, "Type should be defined");

        socket.reqMarketDataType(type.getValue());
    }

    public Single<IbTick> reqMktData(Contract contract) {
        Validators.shouldNotBeNull(contract, "Contract should be defined");

        return requests.<IbTick>addRequestWithId(RequestRepository.Type.REQ_MARKET_DATA,
                                                 (id) -> socket.reqMktData(id, contract, "", true, false, null),
                                                 (id) -> socket.cancelPositionsMulti(id)).firstOrError();
    }

    public synchronized Observable<IbTick> subscribeOnMarketData(Contract contract) {
        Validators.contractWithIdShouldExist(contract);

        return requests.addRequestWithId(RequestRepository.Type.EVENT_MARKET_DATA,
                                         (id) -> socket.reqMktData(id, contract, "", false, false, null),
                                         (id) -> socket.cancelMktData(id));
    }

    public synchronized Observable<IbPnl> subscribeOnContractPnl(int contractId, String account) {
        Validators.intShouldBePositive(contractId, "Contract ID should be positive");
        Validators.stringShouldNotBeEmpty(account, "Account should be defined");

        return requests.addRequestWithId(RequestRepository.Type.EVENT_CONTRACT_PNL,
                                         (id) -> socket.reqPnLSingle(id, account, "", contractId),
                                         (id) -> socket.cancelPnLSingle(id));
    }

    public synchronized Observable<IbPnl> subscribeOnAccountPnl(String account) {
        Validators.stringShouldNotBeEmpty(account, "Account should be defined");

        return requests.addRequestWithId(RequestRepository.Type.EVENT_ACCOUNT_PNL,
                                         (id) -> socket.reqPnL(id, account, ""),
                                         (id) -> socket.cancelPnL(id));
    }

    public synchronized Observable<IbPortfolio> subscribeOnAccountPortfolio(String account) {
        Validators.stringShouldNotBeEmpty(account, "Account should be defined");

        return requests.addRequestWithoutId(RequestRepository.Type.EVENT_PORTFOLIO,
                                            (unused) -> socket.reqAccountUpdates(true, account),
                                            (unused) -> socket.reqAccountUpdates(false, account));
    }

    public synchronized Observable<Boolean> status() {
        return requests.addRequestWithoutId(RequestRepository.Type.EVENT_CONNECTION_STATUS,
                                            null, null);
    }

    public synchronized int nextOrderId() {
        return idGenerator.nextOrderId();
    }

    @NotNull
    public synchronized Single<Long> getCurrentTime() {
        return requests.<Long>addRequestWithoutId(RequestRepository.Type.REQ_CURRENT_TIME,
                                                  (unused) -> socket.reqCurrentTime(), null).firstOrError();
    }

    @NotNull
    public synchronized Single<IbOrder> placeOrder(Contract contract, Order order) {
        Validators.shouldNotBeNull(contract, "Contract should be defined");
        Validators.shouldNotBeNull(order, "Order should be defined");

        if (order.orderId() == 0) {
            order.orderId(idGenerator.nextOrderId());
        }

        return requests.<IbOrder>addRequest(RequestRepository.Type.REQ_ORDER_PLACE,
                                            order.orderId(),
                                            (unused) -> socket.placeOrder(order.orderId(), contract, order),
                                            null).firstOrError();
    }

    public synchronized void cancelOrder(int orderId) {
        Validators.intShouldBePositive(orderId, "Order ID should be positive");

        // TODO: Check for order canceled status and return Completable

        log.info("Canceling order {}", orderId);
        socket.cancelOrder(orderId);
    }

    public synchronized void reqGlobalCancel() {
        // TODO: Check for all orders canceled status and return Completable

        socket.reqGlobalCancel();
    }

    @NotNull
    public synchronized Single<Map<Integer, IbOrder>> reqAllOpenOrders() {
        return requests.<Map<Integer, IbOrder>>addRequestWithoutId(RequestRepository.Type.REQ_ORDER_LIST,
                                                                   (unused) -> socket.reqAllOpenOrders(),
                                                                   null).firstOrError();
    }

    @NotNull
    public Observable<ContractDetails> reqContractDetails(Contract contract) {
        Validators.shouldNotBeNull(contract, "Contract should be defined");

        return requests.addRequestWithId(RequestRepository.Type.REQ_CONTRACT_DETAIL,
                                         (id) -> socket.reqContractDetails(id, contract),
                                         null);
    }

    private void shouldBeConnected() {
        if (!isConnected()) {
            throw new IbExceptions.NotConnected();
        }
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
