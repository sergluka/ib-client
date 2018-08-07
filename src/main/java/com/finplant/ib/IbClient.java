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
import com.finplant.ib.impl.sender.RequestRepository;
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
    private RequestRepository requests;
    private CacheRepository cache;
    private ConnectionMonitor connectionMonitor;
    private IdGenerator idGenerator;
    private SubscriptionsRepository subscriptions;

    public IbClient() {
        super();
        idGenerator = new IdGenerator();
        subscriptions = new SubscriptionsRepository(idGenerator);
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
                    emitter.onComplete();
                }

                @Override
                protected void onConnectStatusChange(Boolean status) {
                    subscriptions.onNext(SubscriptionsRepository.EventType.EVENT_CONNECTION_STATUS, status, false);
                }
            };

            wrapper = new Wrapper(connectionMonitor, requests, cache, subscriptions, idGenerator);

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
        return subscriptions.addSubscriptionUnique(SubscriptionsRepository.EventType.EVENT_ORDER_STATUS, null, null);
    }

    public Observable<IbPosition> subscribeOnPositionChange() {
        return subscriptions.addSubscriptionUnique(
              SubscriptionsRepository.EventType.EVENT_POSITION,
              (unused) -> {
                  shouldBeConnected();
                  socket.reqPositions();
                  return null;
              },
              (unused) -> {
                  shouldBeConnected();
                  socket.cancelPositions();
              });
    }

    public synchronized Observable<IbPosition> subscribeOnPositionChange(String account) {

        return subscriptions.addSubscription(SubscriptionsRepository.EventType.EVENT_POSITION_MULTI,
                                             (id) -> {
                                                 shouldBeConnected();
                                                 socket.reqPositionsMulti(id, account, "");
                                                 return null;
                                             },
                                             (id) -> {
                                                 shouldBeConnected();
                                                 socket.cancelPositionsMulti(id);
                                             });
    }

    //    public synchronized Observable<IbPosition> subscribeOnPositionChange(String account) {
//
//        return subscriptions.addSubscription(SubscriptionsRepository.EventType.EVENT_POSITION_MULTI,
//                                             (id) -> {
//                                                 shouldBeConnected();
//                                                 return requests.postRequest(
//                                                       RequestRepository.Event.REQ_POSITIONS_MULTI,
//                                                       id, () -> socket.reqPositionsMulti(id, account, ""));
//                                             },
//                                             (id) -> {
//                                                 shouldBeConnected();
//                                                 socket.cancelPositionsMulti(id);
//                                             });
//    }
//
    public synchronized void setMarketDataType(MarketDataType type) {
        socket.reqMarketDataType(type.getValue());
    }

    public Single<IbTick> reqMktData(Contract contract) {
        shouldBeConnected();

        int tickerId = idGenerator.nextRequestId();
        return requests.<IbTick>postRequest(RequestRepository.Event.REQ_MARKET_DATA,
                                            tickerId,
                                            () -> socket.reqMktData(tickerId,
                                                                    contract,
                                                                    "",
                                                                    true,
                                                                    false,
                                                                    null)).firstOrError();
    }

    public synchronized Observable<IbTick> subscribeOnMarketData(Contract contract) {
        return subscriptions.addSubscription(SubscriptionsRepository.EventType.EVENT_MARKET_DATA,
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

    public synchronized Observable<IbPnl> subscribeOnContractPnl(int contractId, String account) {
        return subscriptions.addSubscription(SubscriptionsRepository.EventType.EVENT_CONTRACT_PNL,
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

    public synchronized Observable<IbPnl> subscribeOnAccountPnl(@NotNull String account) {
        Objects.requireNonNull(account, "'account' parameter is null");
        if (account.isEmpty()) {
            throw new IllegalArgumentException("'account' parameter is empty");
        }

        return subscriptions.addSubscription(SubscriptionsRepository.EventType.EVENT_ACCOUNT_PNL,
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

    public synchronized Observable<IbPortfolio> subscribeOnAccountPortfolio(@NotNull String account) {

        Objects.requireNonNull(account, "'account' parameter is null");
        if (account.isEmpty()) {
            throw new IllegalArgumentException("'account' parameter is empty");
        }

        return subscriptions.addSubscriptionUnique(SubscriptionsRepository.EventType.EVENT_PORTFOLIO,
                                                   (unused) -> {
                                                       shouldBeConnected();
                                                       socket.reqAccountUpdates(true, account);
                                                       return null;
                                                   },
                                                   (unused) -> {
                                                       shouldBeConnected();
                                                       socket.reqAccountUpdates(false, account);
                                                   });
    }

    public synchronized Observable<Boolean> status() {
        return subscriptions.addSubscriptionUnique(SubscriptionsRepository.EventType.EVENT_CONNECTION_STATUS,
                                                   null,
                                                   null);
    }

    public synchronized int nextOrderId() {
        return idGenerator.nextOrderId();
    }

    @NotNull
    public synchronized Single<Long> getCurrentTime() {
        shouldBeConnected();
        return requests.<Long>postRequest(RequestRepository.Event.REQ_CURRENT_TIME,
                                          null,
                                          () -> socket.reqCurrentTime()).firstOrError();
    }

    @NotNull
    public synchronized Single<IbOrder> placeOrder(@NotNull Contract contract, @NotNull Order order) {
        shouldBeConnected();

        if (order.orderId() == 0) {
            order.orderId(idGenerator.nextOrderId());
        }
        final Integer id = order.orderId();

        return requests.<IbOrder>postRequest(RequestRepository.Event.REQ_ORDER_PLACE, id,
                                             () -> socket.placeOrder(id, contract, order)).firstOrError();
    }

    public synchronized void cancelOrder(int orderId) {
        log.info("Canceling order {}", orderId);
        socket.cancelOrder(orderId);
    }

    public synchronized void reqGlobalCancel() {
        socket.reqGlobalCancel();
    }

    @NotNull
    public synchronized Single<Map<Integer, IbOrder>> reqAllOpenOrders() {
        shouldBeConnected();

        return requests.<Map<Integer, IbOrder>>postRequest(RequestRepository.Event.REQ_ORDER_LIST, null,
                                                           () -> socket.reqAllOpenOrders()).firstOrError();
    }

    @NotNull
    public Observable<ContractDetails> reqContractDetails(@NotNull Contract contract) {
        Objects.requireNonNull(contract);

        shouldBeConnected(); // TODO: move to subscribe
        final Integer id = idGenerator.nextRequestId(); // TODO: move to sibscribe
        return requests.<ContractDetails>postRequest(RequestRepository.Event.REQ_CONTRACT_DETAIL, id,
                                                     () -> socket.reqContractDetails(id, contract));
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
