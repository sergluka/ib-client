package lv.sergluka.tws.impl;

import com.ib.client.EClientSocket;
import com.ib.client.EJavaSignal;
import com.ib.client.EReader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
        reader.interrupt();

        try {
            reader.join(10_000);
        } catch (InterruptedException e) {
            log.debug("Reader has been interrupted");
        }
        if (reader.isAlive()) {
            log.warn("Fail to shutdown reader thread");
            try {
                reader.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        try {
            processor.join(STOP_TIMEOUT_MS);
        } catch (InterruptedException e) {
            log.debug("Reader thread has been interrupted");
        }
        if (processor.isAlive()) {
            log.warn("Timeout of reader thread shutdown");
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
}
