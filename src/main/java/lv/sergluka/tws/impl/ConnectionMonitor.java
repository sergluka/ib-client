package lv.sergluka.tws.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class ConnectionMonitor implements AutoCloseable {

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
    private static final int RECONNECT_DELAY = 1_000;

    private Thread thread;

    private AtomicReference<Status> status;
    private AtomicReference<Command> command;

    private final Lock statusLock = new ReentrantLock();
    private final Condition statusCondition = statusLock.newCondition();

    protected abstract void connectRequest();

    protected abstract void disconnectRequest();

    protected abstract void onStatusChange(Status status);

    public void start() {
        status = new AtomicReference<>(Status.UNKNOWN);
        command = new AtomicReference<>(Command.NONE);

        thread = new Thread(this::run);
        thread.setName("Connection monitor");
        thread.start();

        waitForStatus(Status.DISCONNECTED, 1, TimeUnit.MINUTES);
    }

    @Override
    public void close() {
        if (status.get() != Status.DISCONNECTED) {
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

    public void waitForStatus(Status status, long time, TimeUnit unit) {
        try {
            statusLock.lock();
            while (this.status.get() != status) {
                statusCondition.await(time, unit);
            }
        } catch (InterruptedException e) {
            log.error("waitForStatus has been interrupted", e);
        } finally {
            statusLock.unlock();
        }
    }

    public void connect() {
        setCommand(Command.CONNECT);
        waitForStatus(Status.CONNECTED, 1, TimeUnit.MINUTES);
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
    }

    private void run() {
        try {
            setStatus(Status.DISCONNECTED);

            while (!Thread.interrupted()) {
                Command commandLocal = command.getAndSet(Command.NONE);
                switch (commandLocal) {
                    case CONNECT:
                        setStatus(Status.CONNECTING);
                        connectRequest();
                        break;
                    case RECONNECT:
                        setStatus(Status.DISCONNECTING);
                        disconnectRequest();
                        setStatus(Status.DISCONNECTED);
                        Thread.sleep(RECONNECT_DELAY);
                        setStatus(Status.CONNECTING);
                        connectRequest();
                        break;
                    case CONFIRM_CONNECT:
                        setStatus(Status.CONNECTED);
                        break;
                    case DISCONNECT:
                        setStatus(Status.DISCONNECTING);
                        disconnectRequest();
                        setStatus(Status.DISCONNECTED);
                        break;
                }

                if (commandLocal == Command.NONE) {
                    Thread.sleep(100);
                }
            }
        } catch (InterruptedException ignored) {
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

        onStatusChange(newStatus);

        statusLock.lock();
        try {
            statusCondition.signal();
        } finally {
            statusLock.unlock();
        }
    }
}
