package connection;

public interface IConnectable {

    void requestConnect(String ip, int port, int connId);
    void requestDisconnect();

    interface Events {
        void onConnect();
        void onDisconnect();
    }
}
