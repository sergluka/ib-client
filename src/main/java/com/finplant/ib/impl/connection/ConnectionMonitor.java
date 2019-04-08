package com.finplant.ib.impl.connection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
        CONFIRM_CONNECT
    }

    public enum Status {
        UNKNOWN,
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        SLEEP,
    }

    private static final Logger log = LoggerFactory.getLogger(ConnectionMonitor.class);

    private static final int CLOSE_TIMEOUT_MS = 10_000;
    private static final long RECONNECT_DELAY = 1_000;

    private final Lock statusLock = new ReentrantLock();
    private final Condition statusCondition = statusLock.newCondition();
    private final SleepTimer timer = new SleepTimer();

    private boolean isConnected = false;
    private Status expectedStatus = null;

    private Thread thread;
    private AtomicReference<Status> status;
    private AtomicReference<Command> command;

    protected abstract void connectRequest();
    protected abstract void disconnectRequest(boolean reconnect);
    protected abstract void afterConnect();
    protected abstract void onConnectStatusChange(Boolean connected);

    public void start() {
        status = new AtomicReference<>(Status.UNKNOWN);
        command = new AtomicReference<>(Command.NONE);
        isConnected = false;

        thread = new Thread(this::run);
        thread.setName("Connection monitor");
        thread.start();

        try {
            waitForStatus(Status.DISCONNECTED, 1, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            log.error("Too long expectation for DISCONNECT status");
        }
    }

    @Override
    public void close() {
        if (status.get() != Status.DISCONNECTED) {
            disconnect();
        }
        waitForStatus(Status.DISCONNECTED);

        thread.interrupt();
        try {
            thread.join(CLOSE_TIMEOUT_MS);
        } catch (InterruptedException e) {
            log.error("Current thread '{}' has been interrupted at shutdown", this);
        }
    }

    public Status status() {
        return status.get();
    }

    public synchronized void connect() {
        setCommand(Command.CONNECT);
    }

    public synchronized void confirmConnection() {
        setCommand(Command.CONFIRM_CONNECT);
    }

    public synchronized void reconnect() {
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
                commandLocal = handleCommand(commandLocal);

                if (commandLocal == Command.NONE) {
                    Thread.sleep(100);
                }
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            log.error("Exception in Connection Monitor: {}", e.getMessage(), e);
        }
    }

    private Command handleCommand(Command command) {
        switch (command) {
            case CONNECT:
                setStatus(Status.CONNECTING);
                connectRequest();
                break;
            case RECONNECT:
                setStatus(Status.DISCONNECTING);
                disconnectRequest(true);
                setStatus(Status.DISCONNECTED);

                timer.start(RECONNECT_DELAY, () -> {
                    setStatus(Status.CONNECTING);
                    connectRequest();
                });

                break;
            case CONFIRM_CONNECT:
                /* Right after connect, an error 507 (Bad Message Length) can occur, so we re-read command
                   to be sure valid connection still persist */
                timer.start(RECONNECT_DELAY, () -> {
                    setStatus(Status.CONNECTED);
                    afterConnect();
                });

                break;
            case DISCONNECT:
                setStatus(Status.DISCONNECTING);
                disconnectRequest(false);
                setStatus(Status.DISCONNECTED);
                break;
            case NONE:
                if (timer.isRunning()) {
                    setStatus(Status.SLEEP);
                    if (timer.run()) {
                        timer.reset();
                    }
                }
                break;

        }
        return command;
    }

    private void setCommand(Command newCommand) {
        timer.reset();
        this.command.set(newCommand);
        log.debug("Command: {}", newCommand.name());
    }

    private void setStatus(Status newStatus) {
        Status oldStatus = this.status.getAndSet(newStatus);
        if (oldStatus == newStatus) {
            return;
        }

        log.debug("Status change: {} => {}", oldStatus.name(), newStatus.name());
        triggerStatusOnChange(newStatus);

        statusLock.lock();
        try {
            if (expectedStatus == newStatus) {
                statusCondition.signal();
            }
        } finally {
            statusLock.unlock();
        }
    }

    private void triggerStatusOnChange(Status newStatus) {
        if (!isConnected && newStatus == Status.CONNECTED) {
            isConnected = true;
            onConnectStatusChange(true);
        } else if (isConnected && newStatus != Status.CONNECTED) {
            isConnected = false;
            onConnectStatusChange(false);
        }
    }

    private void waitForStatus(Status status, long time, TimeUnit unit) throws TimeoutException {
        try {
            statusLock.lock();
            expectedStatus = status;

            while (this.status.get() != expectedStatus) {
                if (!statusCondition.await(time, unit)) {
                    throw new TimeoutException(String.format(
                          "Timeout of '%s' status. Actual status is '%s'", expectedStatus, this.status));
                }
            }
        } catch (InterruptedException e) {
            log.error("waitForStatus has been interrupted", e);
        } finally {
            statusLock.unlock();
        }
    }

    private void waitForStatus(Status status) {
        try {
            statusLock.lock();
            expectedStatus = status;
            while (this.status.get() != expectedStatus) {
                statusCondition.await();
            }
        } catch (InterruptedException e) {
            log.warn("waitForStatus has been interrupted", e);
        } finally {
            statusLock.unlock();
        }
    }
}
