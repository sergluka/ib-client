//package connection;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//public class ConnectionMonitor implements IConnectable.Events {
//
//    private static final Logger log = LoggerFactory.getLogger(ConnectionMonitor.class);
//
//    private final IConnectable.Events client;
//    private String ip;
//    private int port;
//    private int connId;
//
//    boolean isConnected = false;
//
//    public ConnectionMonitor(IConnectable.Events client) {
//        this.client = client;
//    }
//
//    public void connect(String ip, int port, int connId) {
//        this.ip = ip;
//        this.port = port;
//        this.connId = connId;
//
//        log.debug("Connecting...");
//        client.requestConnect(this.ip, this.port, this.connId);
//    }
//
//    public void reconnect() {
//        log.warn("Reconnecting");
//        client.requestDisconnect();
//        client.requestConnect(this.ip, this.port, this.connId);
//    }
//
//    public boolean isConnected() {
//        return isConnected;
//    }
//
//    @Override
//    public void onConnect() {
//        isConnected = true;
////        log.info("Connected to {}:{}[{}], server version: {}", ip, port, connId, client.serverVersion());
//    }
//
//    @Override
//    public void onDisconnect() {
//        isConnected = false;
//
//    }
//
////    public void confirm() {
////        log.info("Connected to {}:{}[{}], server version: {}", ip, port, connId, client.serverVersion());
////
////    }
//
////    void start() {
////        readerThread = new Thread(this::processMessages);
////        readerThread.start();
////
////        reader = new EReader(socket, m_signal);
////        reader.start();
////
////        status = TwsClientWrapper.Status.CONNECTED;
////        log.info("Connected to {}:{}[{}], server version: {}", credentials.getIp(), credentials.getPort(),
////                credentials.getConnId(), socket.serverVersion());
////
////    }
//}
