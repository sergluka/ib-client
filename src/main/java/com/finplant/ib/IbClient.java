package com.finplant.ib;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.finplant.ib.impl.IbReader;
import com.finplant.ib.impl.Wrapper;
import com.finplant.ib.impl.cache.CacheRepository;
import com.finplant.ib.impl.connection.ConnectionMonitor;
import com.finplant.ib.impl.subscription.SubscriptionsRepository;
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

public class IbClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(IbClient.class);
    private IbReader reader;
    private Wrapper wrapper;
    private EClientSocket socket;
    private CacheRepository cache;
    private ConnectionMonitor connectionMonitor;
    private IdGenerator idGenerator;
    private SubscriptionsRepository subscriptions;

    public IbClient() {
        super();
        idGenerator = new IdGenerator();
        subscriptions = new SubscriptionsRepository(this, idGenerator);
    }

    @Override
    public void close() {
        disconnect();
    }

    public CacheRepository getCache() {
        return cache;
    }

    public Completable connect(final @NotNull String ip, final int port, final int connId) {
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

                    subscriptions.resubscribe();
                    emitter.onComplete();
                }

                @Override
                protected void onConnectStatusChange(Boolean status) {
                    subscriptions.onNext(SubscriptionsRepository.Type.EVENT_CONNECTION_STATUS, null, status, false);
                }
            };

            wrapper = new Wrapper(connectionMonitor, cache, subscriptions, idGenerator);

            connectionMonitor.start();
            connectionMonitor.connect();
        });
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

    public Set<String> getManagedAccounts() {
        return wrapper.getManagedAccounts();
    }

    @NotNull
    public Observable<IbOrderStatus> subscribeOnOrderNewStatus() {
        return subscriptions.addSubscriptionWithoutId(SubscriptionsRepository.Type.EVENT_ORDER_STATUS, null, null);
    }

    public Observable<IbPosition> subscribeOnPositionChange() {
        return subscriptions.addSubscriptionWithoutId(
              SubscriptionsRepository.Type.EVENT_POSITION,
              (unused) -> socket.reqPositions(),
              (unused) -> socket.cancelPositions());
    }

    public synchronized Observable<IbPosition> subscribeOnPositionChange(String account) {

        return subscriptions.addSubscriptionWithId(SubscriptionsRepository.Type.EVENT_POSITION_MULTI,
                                                   (id) -> socket.reqPositionsMulti(id, account, ""),
                                                   (id) -> socket.cancelPositionsMulti(id));
    }

    public synchronized void setMarketDataType(MarketDataType type) {
        socket.reqMarketDataType(type.getValue());
    }

    public Single<IbTick> reqMktData(Contract contract) {

        return subscriptions.<IbTick>addSubscriptionWithId(SubscriptionsRepository.Type.REQ_MARKET_DATA,
                                                           (id) -> socket.reqMktData(id, contract, "", true, false, null),
                                                           (id) -> socket.cancelPositionsMulti(id)).firstOrError();
    }

    public synchronized Observable<IbTick> subscribeOnMarketData(Contract contract) {
        return subscriptions.addSubscriptionWithId(SubscriptionsRepository.Type.EVENT_MARKET_DATA,
                                                   (id) -> socket.reqMktData(id, contract, "", false, false, null),
                                                   (id) -> socket.cancelMktData(id));
    }

    public synchronized Observable<IbPnl> subscribeOnContractPnl(int contractId, String account) {
        return subscriptions.addSubscriptionWithId(SubscriptionsRepository.Type.EVENT_CONTRACT_PNL,
                                                   (id) -> socket.reqPnLSingle(id, account, "", contractId),
                                                   (id) -> socket.cancelPnLSingle(id));
    }

    public synchronized Observable<IbPnl> subscribeOnAccountPnl(@NotNull String account) {
        // TODO: Validate parameters for all funcs (use apache validator?)
        Objects.requireNonNull(account, "'account' parameter is null");
        if (account.isEmpty()) {
            throw new IllegalArgumentException("'account' parameter is empty");
        }

        return subscriptions.addSubscriptionWithId(SubscriptionsRepository.Type.EVENT_ACCOUNT_PNL,
                                                   (id) -> socket.reqPnL(id, account, ""),
                                                   (id) -> socket.cancelPnL(id));
    }

    public synchronized Observable<IbPortfolio> subscribeOnAccountPortfolio(@NotNull String account) {

        Objects.requireNonNull(account, "'account' parameter is null");
        if (account.isEmpty()) {
            throw new IllegalArgumentException("'account' parameter is empty");
        }

        return subscriptions.addSubscriptionWithoutId(SubscriptionsRepository.Type.EVENT_PORTFOLIO,
                                                      (unused) -> socket.reqAccountUpdates(true, account),
                                                      (unused) -> socket.reqAccountUpdates(false, account));
    }

    public synchronized Observable<Boolean> status() {
        return subscriptions.addSubscriptionWithoutId(SubscriptionsRepository.Type.EVENT_CONNECTION_STATUS,
                                                      null, null);
    }

    public synchronized int nextOrderId() {
        return idGenerator.nextOrderId();
    }

    @NotNull
    public synchronized Single<Long> getCurrentTime() {
        return subscriptions.<Long>addSubscriptionWithoutId(SubscriptionsRepository.Type.REQ_CURRENT_TIME,
                                                            (unused) -> socket.reqCurrentTime(), null).firstOrError();
    }

    @NotNull
    public synchronized Single<IbOrder> placeOrder(@NotNull Contract contract, @NotNull Order order) {
        if (order.orderId() == 0) {
            order.orderId(idGenerator.nextOrderId());
        }

        return subscriptions.<IbOrder>addSubscription(SubscriptionsRepository.Type.REQ_ORDER_PLACE,
                                                      order.orderId(),
                                                      (unused) -> socket.placeOrder(order.orderId(), contract, order),
                                                      null).firstOrError();
    }

    public synchronized void cancelOrder(int orderId) {
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
        return subscriptions.<Map<Integer, IbOrder>>addSubscriptionWithoutId(SubscriptionsRepository.Type.REQ_ORDER_LIST,
                                                                             (unused) -> socket.reqAllOpenOrders(),
                                                                             null).firstOrError();
    }

    @NotNull
    public Observable<ContractDetails> reqContractDetails(@NotNull Contract contract) {
        Objects.requireNonNull(contract);

        return subscriptions.addSubscriptionWithId(SubscriptionsRepository.Type.REQ_CONTRACT_DETAIL,
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
