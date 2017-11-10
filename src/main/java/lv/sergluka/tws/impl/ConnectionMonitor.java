package lv.sergluka.tws.impl;

import com.ib.client.EClientSocket;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class ConnectionMonitor implements AutoCloseable {

    public enum Command {
        NONE,
        DISCONNECT,
        CONNECT,
        RECONNECT,
        CONFIRM_CONNECT,
    }

    public enum Status {
        UNKNOWN,
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
    }

    private static final Logger log = LoggerFactory.getLogger(ConnectionMonitor.class);

    private final EClientSocket socket;
    private final TwsReader reader;
    private String ip;
    private int port;
    private int connId;

    private Thread thread;

    private AtomicReference<Status> status;
    private AtomicReference<Command> command;

    public ConnectionMonitor(@NotNull EClientSocket socket, @NotNull TwsReader reader) {
        this.socket = socket;
        this.reader = reader;
    }

    public void start() {
        status = new AtomicReference<>(Status.UNKNOWN);
        command = new AtomicReference<>(Command.NONE);

        thread = new Thread(this::run);
        thread.setName("Connection monitor");
        thread.start();

        waitForStatus(Status.DISCONNECTED);
    }

    @Override
    public void close() throws Exception {
        if (status.get() != Status.DISCONNECTED ) {
            disconnect();
        }

        thread.interrupt();
        try {
            thread.join(10_000);
        } catch (InterruptedException e) {
            log.error("Current thread '{}' has been interrupted at shutdown");
        }
    }

    public Status status() {
        return status.get();
    }

    // TODO: REFACTOR, make condition
    // TODO: add timeout
    public void waitForStatus(Status status) {
        while (this.status.get() != status) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                log.error("", e);
            }
        }
    }

    public void connect(@NotNull String ip, int port, int connId) {

        if (!thread.isAlive()) {
            throw new IllegalStateException("Thread has not been started");
        }

        this.ip = ip;
        this.port = port;
        this.connId = connId;

        log.debug("Connecting to {}:{}, id={}", ip, port, connId);
        setCommand(Command.CONNECT);
        waitForStatus(Status.CONNECTED);
    }

    public void confirmConnection() {
        setCommand(Command.CONFIRM_CONNECT);
    }

    public void reconnect() {
        setCommand(Command.RECONNECT);
    }

    public void disconnect() {
        if (!thread.isAlive()) {
            throw new IllegalStateException("Thread has not been started");
        }

        setCommand(Command.DISCONNECT);
        waitForStatus(Status.DISCONNECTED);
    }

    private void run() {
        try {

            setStatus(Status.DISCONNECTED);

            while (true) {
                Command commandLocal = command.get();
                switch (commandLocal) {
                    case CONNECT:
                        setStatus(Status.CONNECTING);
                        connectSync();
                        break;
                    case RECONNECT:
                        setStatus(Status.DISCONNECTING);
                        disconnectSync();
                        setStatus(Status.CONNECTING);
                        connectSync();
                        break;
                    case CONFIRM_CONNECT:
                        setStatus(Status.CONNECTED);
                        break;
                    case DISCONNECT:
                        setStatus(Status.DISCONNECTING);
                        disconnectSync();
                        setStatus(Status.DISCONNECTED);
                        break;
                }

                if (commandLocal == Command.NONE) {
                    Thread.sleep(100);
                }

                if (commandLocal != Command.NONE) {
                    setCommand(Command.NONE);
                }
            }
        } catch (Exception e) {
            log.error("Exception in Connection Monitor", e);
        }
    }

    private void setCommand(Command newCommand) {
        this.command.set(newCommand);
        log.debug("Command: {}", newCommand.name());
    }

    private void setStatus(Status newStatus) {
        Status oldStatus = this.status.getAndSet(newStatus);
        if (oldStatus != newStatus) {
            log.debug("Status change: {} => {}", oldStatus.name(), newStatus.name());
        }
    }

    private void connectSync() {
        Objects.requireNonNull(ip);
        Objects.requireNonNull(socket);

        socket.setAsyncEConnect(false);
        socket.eConnect(ip, port, connId);
        socket.setServerLogLevel(5); // TODO
    }

//    public void reconnect(int delay_ms) {
//        Future<?> future = Executors.newSingleThreadExecutor().submit(() -> {
//            log.warn("Reconnecting");
//            disconnect();
//
//            try {
//                Thread.sleep(delay_ms);
//            } catch (InterruptedException e) {
//                log.error("Current thread is interrupted at reconnect");
//            }
//
//            connect(this.ip, this.port, this.connId);
//        });
////        try {
////            future.get();
////        } catch (InterruptedException e) {
////            e.printStackTrace();
////        } catch (ExecutionException e) {
////            e.printStackTrace();
////        }
//    }

    private void disconnectSync() {
        Objects.requireNonNull(socket);
        Objects.requireNonNull(reader);

        socket.eDisconnect();
        reader.close();
    }
}
