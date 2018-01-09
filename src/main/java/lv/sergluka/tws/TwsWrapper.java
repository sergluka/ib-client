package lv.sergluka.tws;

import com.google.common.base.Splitter;
import com.ib.client.Contract;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.TickAttr;
import lv.sergluka.tws.impl.ConnectionMonitor;
import lv.sergluka.tws.impl.Wrapper;
import lv.sergluka.tws.impl.sender.RequestRepository;
import lv.sergluka.tws.impl.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Consumer;

// TODO: unsubscribe for all at close

class TwsWrapper extends Wrapper {

    private static final Logger log = LoggerFactory.getLogger(TwsWrapper.class);
    private final TerminalErrorHandler errorHandler;

    private TwsClient twsClient;

    public TwsWrapper() {
        errorHandler = new TerminalErrorHandler() {

            @Override
            void onError() {
                // TODO: Don't reconnect, if error was at disconnecting
                twsClient.connectionMonitor.reconnect();
            }

            @Override
            void onFatalError() {
                twsClient.connectionMonitor.disconnect();
            }
        };
    }

    void setClient(TwsClient twsClient) {
        this.twsClient = twsClient;
    }

    @Override
    public void connectAck() {
        try {
            log.info("Connection is opened. version: {}", twsClient.getSocket().serverVersion());
            twsClient.reader.start(); // TODO: maybe move it into Conn Manager thread

            twsClient.connectionMonitor.confirmConnection();
            super.connectAck();
        } catch (Exception e) {
            log.error("Exception at `connectAck`", e);
        }
    }

    @Override
    public void connectionClosed() {
        log.error("TWS closes the connection");
        twsClient.connectionMonitor.reconnect();
    }

    @Override
    public void orderStatus(int orderId, String status, double filled, double remaining, double avgFillPrice,
                            int permId, int parentId, double lastFillPrice, int clientId, String whyHeld,
                            double mktCapPrice) {

        final TwsOrderStatus twsStatus = new TwsOrderStatus(orderId,
                                                            status,
                                                            filled,
                                                            remaining,
                                                            avgFillPrice,
                                                            permId,
                                                            parentId,
                                                            lastFillPrice,
                                                            clientId,
                                                            whyHeld,
                                                            mktCapPrice);

        log.debug("orderStatus: {}", twsStatus);

        if (twsClient.cache.addNewStatus(twsStatus)) {

            log.info("New order status: {}", twsStatus);

            if (twsClient.onOrderStatus != null) {
                twsClient.executors.submit(() -> twsClient.onOrderStatus.accept(orderId, twsStatus));
            }
        }
    }

    @Override
    public void openOrder(int orderId, Contract contract, Order order, OrderState state) {
        TwsOrder twsOrder = new TwsOrder(orderId, contract, order, state);

        log.debug("openOrder: requestId={}, contract={}, order={}, orderState={}",
                  orderId, contract.symbol(), order.orderId(), state.status());

        if (twsClient.cache.addOrder(twsOrder)) {
            log.info("New order: requestId={}, contract={}, order={}, orderState={}",
                     orderId, contract.symbol(), order.orderId(), state.status());
            TwsClient.requests.confirmAndRemove(RequestRepository.Event.REQ_ORDER_PLACE, orderId, twsOrder);
        }
    }

    @Override
    public void openOrderEnd() {
        twsClient.requests.confirmAndRemove(RequestRepository.Event.REQ_ORDER_LIST, null, twsClient.cache.getOrders());
    }

    @Override
    public void position(String account, Contract contract, double pos, double avgCost) {
        log.info("Position change: {}/{},{}/{}", account, contract.conid(), contract.localSymbol(), pos);

        twsClient.cache.updatePosition(account, contract, pos, avgCost);
        if (twsClient.onPosition != null) {
            TwsPosition position = new TwsPosition(account, contract, pos, avgCost);
            twsClient.executors.submit(() -> twsClient.onPosition.accept(position));
        }
    }

    @Override
    public void positionEnd() {
        twsClient.executors.submit(() -> twsClient.onPosition.accept(null));
        twsClient.requests.confirmAndRemove(RequestRepository.Event.REQ_POSITIONS, null, null);
    }

    @Override
    public void managedAccounts(String accountsList) {
        twsClient.managedAccounts = new HashSet<>(Splitter.on(",").splitToList(accountsList));
    }

    @Override
    public void pnlSingle(int reqId, int pos, double dailyPnL, double unrealizedPnL, double realizedPnL, double value) {
        log.debug("pnlSingle: reqId={}, pos={}, dailyPnL={}, unrealizedPnL={}, realizedPnL={}, value={}",
                  reqId, pos, dailyPnL, unrealizedPnL, realizedPnL, value);

        Double dailyPnLObj;
        Double unrealizedPnLObj;
        Double realizedPnLObj;
        Double valueObj;

        dailyPnLObj = dailyPnL;
        if (dailyPnL == Double.MAX_VALUE) {
            log.debug("Missing value for 'dailyPnL'");
            dailyPnLObj = null;
        }
        unrealizedPnLObj = unrealizedPnL;
        if (unrealizedPnL == Double.MAX_VALUE) {
            log.debug("Missing value for 'unrealizedPnL'");
            unrealizedPnLObj = null;
        }
        realizedPnLObj = realizedPnL;
        if (realizedPnL == Double.MAX_VALUE) {
            log.debug("Missing value for 'realizedPnL'");
            realizedPnLObj = null;
        }
        valueObj = value;
        if (value == Double.MAX_VALUE) {
            log.debug("Missing value for 'value'");
            valueObj = null;
        }

        TwsPnl pnl = new TwsPnl(pos, dailyPnLObj, unrealizedPnLObj, realizedPnLObj, valueObj);
        twsClient.executors.submit(() -> {
            final Consumer<TwsPnl> consumer = twsClient.onPnlPerContractMap.get(reqId);
            if (consumer == null) {
                log.error("Got 'pnlSingle' with unknown request id {}", reqId);
                return;
            }
            consumer.accept(pnl);
        });
    }

    @Override
    public void pnl(int reqId, double dailyPnL, double unrealizedPnL, double realizedPnL) {
        log.trace("pnl: reqId={}, dailyPnL={}, unrealizedPnL={}, realizedPnL={}, value={}",
                  reqId, dailyPnL, unrealizedPnL, realizedPnL);

        TwsPnl pnl = new TwsPnl(null, dailyPnL, unrealizedPnL, realizedPnL, null);
        twsClient.executors.submit(() -> {
            final Consumer<TwsPnl> consumer = twsClient.onPnlPerAccountMap.get(reqId);
            if (consumer == null) {
                log.error("Got 'pnl' with unknown id %d", reqId);
                return;
            }
            consumer.accept(pnl);
        });
    }

    @Override
    public void tickSize(int tickerId, int field, int value) {
        TwsTick result = twsClient.cache.updateTick(tickerId, (tick) -> {
            tick.setIntValue(field, value);
        });
        publishNewTick(tickerId, result);
    }

    @Override
    public void tickPrice(int tickerId, int field, double value, TickAttr attrib) {
        TwsTick result = twsClient.cache.updateTick(tickerId, (tick) -> {
            tick.setPriceValue(field, value);
        });
        publishNewTick(tickerId, result);
    }

    @Override
    public void tickString(int tickerId, int field, String value) {
        TwsTick result = twsClient.cache.updateTick(tickerId, (tick) -> {
            tick.setStringValue(field, value);
        });
        publishNewTick(tickerId, result);
    }

    @Override
    public void tickGeneric(int tickerId, int field, double value) {
        TwsTick result = twsClient.cache.updateTick(tickerId, (tick) -> {
            tick.setGenericValue(field, value);
        });
        publishNewTick(tickerId, result);
    }

    @Override
    public void tickSnapshotEnd(final int tickerId) {
        log.trace("tickSnapshotEnd({})", tickerId);

        TwsTick tick = twsClient.cache.getTick(tickerId);
        twsClient.requests.confirmAndRemove(RequestRepository.Event.REQ_MAKET_DATA, tickerId, tick);
    }

    @Override
    public void error(final Exception e) {
        if (e instanceof SocketException) {

            final ConnectionMonitor.Status connectionStatus = twsClient.connectionMonitor.status();

            if (connectionStatus == ConnectionMonitor.Status.DISCONNECTING ||
                    connectionStatus == ConnectionMonitor.Status.DISCONNECTED) {

                log.debug("Socket has been closed at shutdown");
                return;
            }

            log.warn("Connection lost", e);
            twsClient.connectionMonitor.reconnect();
        }

        log.error("TWS error", e);
    }

    @Override
    public void error(final int id, final int code, final String message) {
        errorHandler.handle(id, code, message);
    }

    @Override
    public void nextValidId(int id) {
        log.debug("New request ID: {}", id);
        twsClient.getOrderId().set(id);
    }

    private void publishNewTick(int tickerId, TwsTick result) {
        twsClient.executors.submit(() -> {
            final Consumer<TwsTick> consumer = twsClient.onMarketDataMap.get(tickerId);
            if (consumer == null) {
                return;
            }
            consumer.accept(result);
        });
    }
}
