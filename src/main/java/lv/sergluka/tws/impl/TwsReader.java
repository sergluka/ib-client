package lv.sergluka.tws.impl;

import com.ib.client.EClientSocket;
import com.ib.client.EJavaSignal;
import com.ib.client.EReader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TwsReader {

    private static final Logger log = LoggerFactory.getLogger(TwsReader.class);

    private static final int STOP_TIMEOUT_MS = 1000;
    private static final int WAIT_TIMEOUT_MS = 100;

    private final Thread processor = new Thread(this::processMessages);
    private final EJavaSignal signal;
    private final EClientSocket socket;

    private EReader reader;

    public TwsReader(@NotNull EClientSocket socket, @NotNull EJavaSignal signal) {
        this.socket = socket;
        this.signal = signal;

        processor.setName("Processor");
    }

    public void start() {
        reader = new EReader(socket, signal);
        reader.start();
        processor.start();
    }

    public synchronized void close() {
        processor.interrupt();
        if (reader != null) {
            reader.interrupt();
        }

        stopThread(processor);
        if (reader != null) {
            stopThread(reader);
        }
    }

    private void processMessages() {
        while (!Thread.interrupted()) {
            if (socket.isConnected()) {
                signal.waitForSignal();
                try {
                    reader.processMsgs();
                } catch (Exception e) {
                    log.error("Reader error", e);
                }
            } else {
                try {
                    Thread.sleep(WAIT_TIMEOUT_MS);
                } catch (InterruptedException e) {
                    log.debug("Reader thread has been interrupted");
                    break;
                }
            }
        }
    }

    private void stopThread(Thread thread) {
        try {
            thread.join(STOP_TIMEOUT_MS);
        } catch (InterruptedException e) {
            log.debug("Current thread has been interrupted");
            return;
        }
        if (thread.isAlive()) {
            log.warn("Fail to shutdown thread '{}'", thread.getName());
        }
    }
}
