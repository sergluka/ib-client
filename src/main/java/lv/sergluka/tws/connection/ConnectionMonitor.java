package lv.sergluka.tws.connection;

import com.ib.client.EClientSocket;
import lv.sergluka.tws.TwsReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionMonitor {

    private static final Logger log = LoggerFactory.getLogger(ConnectionMonitor.class);
    public static final int RECONNECT_DELAY_MS = 1000;

    private final EClientSocket socket;
    private final TwsReader reader;
    private String ip;
    private int port;
    private int connId;

    boolean isConnected = false;

    public ConnectionMonitor(EClientSocket socket, TwsReader reader) {
        this.socket = socket;
        this.reader = reader;
    }

    public void connect(String ip, int port, int connId) {
        this.ip = ip;
        this.port = port;
        this.connId = connId;

        log.debug("Connecting to {}:{}, id={}", ip, port, connId);

        socket.setAsyncEConnect(false);
        socket.eConnect(ip, port, connId);
        socket.setServerLogLevel(5); // TODO

//        reader.start();
    }

    public void reconnect(int delay_ms) {
        log.warn("Reconnecting");
        disconnect();

        try {
            Thread.sleep(delay_ms);
        } catch (InterruptedException ignored) {
        }

        connect(this.ip, this.port, this.connId);
    }

    public void disconnect() {
        reader.close();
        socket.eDisconnect();
    }

    public boolean isConnected() {
        return isConnected;
    }

//    public void confirm() {
//        log.info("Connected to {}:{}[{}], server version: {}", ip, port, connId, socket.serverVersion());
//
//    }

//    void start() {
//        readerThread = new Thread(this::processMessages);
//        readerThread.start();
//
//        reader = new EReader(socket, m_signal);
//        reader.start();
//
//        status = TwsClientWrapper.Status.CONNECTED;
//        log.info("Connected to {}:{}[{}], server version: {}", credentials.getIp(), credentials.getPort(),
//                credentials.getConnId(), socket.serverVersion());
//
//    }
}
