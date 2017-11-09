package lv.sergluka.tws.connection;

import com.ib.client.EClientSocket;
import lv.sergluka.tws.TwsReader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionMonitor {

    private static final Logger log = LoggerFactory.getLogger(ConnectionMonitor.class);

    private final EClientSocket socket;
    private final TwsReader reader;
    private String ip;
    private int port;
    private int connId;

    boolean isConnected = false;

    public ConnectionMonitor(@NotNull EClientSocket socket, @NotNull TwsReader reader) {
        this.socket = socket;
        this.reader = reader;
    }

    public void connect(@NotNull String ip, int port, int connId) {
        this.ip = ip;
        this.port = port;
        this.connId = connId;

        log.debug("Connecting to {}:{}, id={}", ip, port, connId);

        socket.setAsyncEConnect(false);
        socket.eConnect(ip, port, connId);
        socket.setServerLogLevel(5); // TODO
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
}
