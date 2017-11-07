import com.ib.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketException;
import java.util.Objects;
import java.util.Set;

public class TwsClientWrapper implements EWrapper, Closeable {

    private static final Logger log = LoggerFactory.getLogger(TwsClientWrapper.class);

    private EJavaSignal m_signal = new EJavaSignal();
    final EClientSocket client = new EClientSocket(this, m_signal);

    private EReader reader = null;
    private Thread readerThread;

    private enum Status {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        CONNECTION_LOST,
        DISCONNECTING,
    }

    private Status status = Status.DISCONNECTED;

    private Credentials credentials = null;

    public void connect(final String ip, final int port, final int connId) {

        status = Status.CONNECTING;

        credentials = new Credentials(ip, port, connId);
        connect();
    }

    public void disconnect() {
        log.debug("Disconnecting...");

        status = Status.DISCONNECTING;
        client.eDisconnect();

        m_signal.issueSignal();
        readerThread.interrupt();
        if (readerThread.isAlive()) {
            try {
                readerThread.join(1000);
            } catch (InterruptedException e) {
                log.warn("Timeout of reader thread shutdown");
            }
        }
    }

    public boolean isConnected() {
        return client.isConnected();
    }

    @Override
    public void close() throws IOException {
        disconnect();
    }

    private void connect() {
        log.debug("Connecting...");

        client.setAsyncEConnect(false);
        client.eConnect(credentials.getIp(), credentials.getPort(), credentials.getConnId());
        client.setServerLogLevel(5); // TODO

        readerThread = new Thread(this::processMessages);
        readerThread.start();

        reader = new EReader(client, m_signal);
        reader.start();
    }

    private void reconnect() {
        log.warn("Reconnecting");
        disconnect();
        connect();
    }

    private void processMessages() {

        while (!Thread.interrupted()) {
            if (client.isConnected()) {
                m_signal.waitForSignal();
                try {
                    reader.processMsgs();
                } catch (Exception e) {
                    log.error("Reader error", e);
                }
            } else {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    @Override
    public void tickPrice(final int tickerId, final int field, final double price, final int canAutoExecute) {
        log.debug("tickPrice");
    }

    @Override
    public void tickSize(final int tickerId, final int field, final int size) {
        log.debug("tickSize");
    }

    @Override
    public void tickOptionComputation(final int tickerId,
                                      final int field,
                                      final double impliedVol,
                                      final double delta,
                                      final double optPrice,
                                      final double pvDividend,
                                      final double gamma,
                                      final double vega,
                                      final double theta,
                                      final double undPrice) {
        log.debug("tickOptionComputation");
    }

    @Override
    public void tickGeneric(final int tickerId, final int tickType, final double value) {
        log.debug("tickGeneric");
    }

    @Override
    public void tickString(final int tickerId, final int tickType, final String value) {
        log.debug("tickString");
    }

    @Override
    public void tickEFP(final int tickerId,
                        final int tickType,
                        final double basisPoints,
                        final String formattedBasisPoints,
                        final double impliedFuture,
                        final int holdDays,
                        final String futureLastTradeDate,
                        final double dividendImpact,
                        final double dividendsToLastTradeDate) {
        log.debug("tickEFP");
    }

    @Override
    public void orderStatus(final int orderId, final String status, final double filled, final double remaining,
                            final double avgFillPrice, final int permId, final int parentId, final double lastFillPrice,
                            final int clientId, final String whyHeld) {
        log.info("orderStatus: orderId={}, status={}, filled={}, remaining={}, avgFillPrice={}, permId={}, " +
                 "parentId={}, lastFillPrice={}, clientId={}, whyHeld={}",
                orderId, status, filled, remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld);
    }

    @Override
    public void openOrder(final int orderId, final Contract contract, final Order order, final OrderState orderState) {
        log.info("openOrder: orderId={}, contract={}, order={}, orderState={}", orderId, contract, order, orderState);
    }

    @Override
    public void openOrderEnd() {
        log.info("openOrderEnd");
    }

    @Override
    public void updateAccountValue(final String key,
                                   final String value,
                                   final String currency,
                                   final String accountName) {
        log.debug("updateAccountValue");
    }

    @Override
    public void updatePortfolio(final Contract contract,
                                final double position,
                                final double marketPrice,
                                final double marketValue,
                                final double averageCost,
                                final double unrealizedPNL,
                                final double realizedPNL,
                                final String accountName) {
        log.debug("updatePortfolio");
    }

    @Override
    public void updateAccountTime(final String timeStamp) {
        log.debug("updateAccountTime");
    }

    @Override
    public void accountDownloadEnd(final String accountName) {
        log.debug("accountDownloadEnd");
    }

    @Override
    public void nextValidId(final int orderId) {
        log.info("next ID: {}", orderId);
    }

    @Override
    public void contractDetails(final int reqId, final ContractDetails contractDetails) {
        log.debug("contractDetails");
    }

    @Override
    public void bondContractDetails(final int reqId, final ContractDetails contractDetails) {
        log.debug("bondContractDetails");
    }

    @Override
    public void contractDetailsEnd(final int reqId) {
        log.debug("contractDetailsEnd");
    }

    @Override
    public void execDetails(final int reqId, final Contract contract, final Execution execution) {
        log.debug("execDetails");
    }

    @Override
    public void execDetailsEnd(final int reqId) {
        log.debug("execDetailsEnd");
    }

    @Override
    public void updateMktDepth(final int tickerId,
                               final int position,
                               final int operation,
                               final int side,
                               final double price,
                               final int size) {
        log.debug("updateMktDepth");
    }

    @Override
    public void updateMktDepthL2(final int tickerId,
                                 final int position,
                                 final String marketMaker,
                                 final int operation,
                                 final int side,
                                 final double price,
                                 final int size) {
        log.debug("public");
    }

    @Override
    public void updateNewsBulletin(final int msgId,
                                   final int msgType,
                                   final String message,
                                   final String origExchange) {
        log.debug("public");
    }

    @Override
    public void managedAccounts(final String accountsList) {
        log.debug("managedAccounts");
    }

    @Override
    public void receiveFA(final int faDataType, final String xml) {
        log.debug("receiveFA");
    }

    @Override
    public void historicalData(final int reqId,
                               final String date,
                               final double open,
                               final double high,
                               final double low,
                               final double close,
                               final int volume,
                               final int count,
                               final double WAP,
                               final boolean hasGaps) {
        log.debug("historicalData");
    }

    @Override
    public void scannerParameters(final String xml) {
        log.debug("scannerParameters");
    }

    @Override
    public void scannerData(final int reqId,
                            final int rank,
                            final ContractDetails contractDetails,
                            final String distance,
                            final String benchmark,
                            final String projection,
                            final String legsStr) {

    }

    @Override
    public void scannerDataEnd(final int reqId) {
        log.debug("scannerDataEnd");
    }

    @Override
    public void realtimeBar(final int reqId,
                            final long time,
                            final double open,
                            final double high,
                            final double low,
                            final double close,
                            final long volume,
                            final double wap,
                            final int count) {
        log.debug("realtimeBar");
    }

    @Override
    public void currentTime(final long time) {
        log.debug("currentTime");
    }

    @Override
    public void fundamentalData(final int reqId, final String data) {
        log.debug("fundamentalData");
    }

    @Override
    public void deltaNeutralValidation(final int reqId, final DeltaNeutralContract underComp) {
        log.debug("deltaNeutralValidation");
    }

    @Override
    public void tickSnapshotEnd(final int reqId) {
        log.debug("tickSnapshotEnd");
    }

    @Override
    public void marketDataType(final int reqId, final int marketDataType) {
        log.debug("marketDataType");
    }

    @Override
    public void commissionReport(final CommissionReport commissionReport) {
        log.debug("commissionReport");
    }

    @Override
    public void position(final String account, final Contract contract, final double pos, final double avgCost) {
        log.debug("position");
    }

    @Override
    public void positionEnd() {
        log.debug("positionEnd");
    }

    @Override
    public void accountSummary(final int reqId,
                               final String account,
                               final String tag,
                               final String value,
                               final String currency) {
        log.debug("accountSummary");
    }

    @Override
    public void accountSummaryEnd(final int reqId) {
        log.debug("accountSummaryEnd");
    }

    @Override
    public void verifyMessageAPI(final String apiData) {
        log.debug("verifyMessageAPI");
    }

    @Override
    public void verifyCompleted(final boolean isSuccessful, final String errorText) {
        log.debug("verifyCompleted");
    }

    @Override
    public void verifyAndAuthMessageAPI(final String apiData, final String xyzChallange) {
        log.debug("verifyAndAuthMessageAPI");
    }

    @Override
    public void verifyAndAuthCompleted(final boolean isSuccessful, final String errorText) {
        log.debug("verifyAndAuthCompleted");
    }

    @Override
    public void displayGroupList(final int reqId, final String groups) {
        log.debug("displayGroupList");
    }

    @Override
    public void displayGroupUpdated(final int reqId, final String contractInfo) {
        log.debug("displayGroupUpdated");
    }

    @Override
    public void error(final Exception e) {
        if (e instanceof SocketException) {
            if (status == Status.DISCONNECTING) {
                log.debug("Socket has been closed at shutdown");
                return;
            } else {
                log.debug(">>: {}", status);
                status = Status.CONNECTION_LOST;
                reconnect();
            }
        }

        log.error("Terminal returns an error", e);
    }

    @Override
    public void error(final String str) {
    }

    @Override
    public void error(final int id, final int errorCode, final String errorMsg) {
        if (id == -1 && (errorCode == 2104 || errorCode == 2106)) {
            log.debug("Connection is OK: {}", errorMsg);
            return;
        }

        log.error("Terminal returns an error: id={}, code={}, msg={}", id, errorCode, errorMsg);

//        if (id == -1 && errorCode == 507) {
//            reconnect();
//        }
    }

    @Override
    public void connectionClosed() {
        status = Status.DISCONNECTED;
        log.info("Disconnected");
    }

    @Override
    public void connectAck() {
        status = Status.CONNECTED;
        log.info("Connected to {}:{}[{}], server version: {}", credentials.getIp(), credentials.getPort(),
                credentials.getConnId(), client.serverVersion());
    }

    @Override
    public void positionMulti(final int reqId,
                              final String account,
                              final String modelCode,
                              final Contract contract,
                              final double pos,
                              final double avgCost) {
        log.debug("positionMulti");
    }

    @Override
    public void positionMultiEnd(final int reqId) {
        log.debug("positionMultiEnd");
    }

    @Override
    public void accountUpdateMulti(final int reqId,
                                   final String account,
                                   final String modelCode,
                                   final String key,
                                   final String value,
                                   final String currency) {
        log.debug("accountUpdateMulti");
    }

    @Override
    public void accountUpdateMultiEnd(final int reqId) {
        log.debug("accountUpdateMultiEnd");
    }

    @Override
    public void securityDefinitionOptionalParameter(final int reqId,
                                                    final String exchange,
                                                    final int underlyingConId,
                                                    final String tradingClass,
                                                    final String multiplier,
                                                    final Set<String> expirations,
                                                    final Set<Double> strikes) {
        log.debug("securityDefinitionOptionalParameter");
    }

    @Override
    public void securityDefinitionOptionalParameterEnd(final int reqId) {
        log.debug("securityDefinitionOptionalParameterEnd");
    }

    @Override
    public void softDollarTiers(final int reqId, final SoftDollarTier[] tiers) {
        log.debug("softDollarTiers");
    }

    public void reqCurrentTime() {
        client.reqCurrentTime();
    }

    public int serverVersion() {
        return client.serverVersion();
    }

    static class Credentials {
        private final String ip;
        private final int port;
        private final int connId;

        Credentials(final String ip, final int port, final int connId) {
            this.ip = ip;
            this.port = port;
            this.connId = connId;
        }

        public String getIp() {
            return ip;
        }

        public int getPort() {
            return port;
        }

        public int getConnId() {
            return connId;
        }
    }
}
