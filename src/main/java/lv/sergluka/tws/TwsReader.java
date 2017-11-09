package lv.sergluka.tws;

import com.ib.client.EClientSocket;
import com.ib.client.EJavaSignal;
import com.ib.client.EReader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TwsReader implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TwsReader.class);

    private static final int STOP_TIMEOUT_MS = 1000;
    private static final int WAIT_TIMEOUT_MS = 100;

    private final Thread readerThread = new Thread(this::processMessages);
    private final EJavaSignal signal;
    private final EClientSocket socket;

    private EReader reader;

    TwsReader(@NotNull EClientSocket socket, @NotNull EJavaSignal signal) {
        this.socket = socket;
        this.signal = signal;
    }

    public void start() {
        reader = new EReader(socket, signal);
        reader.start();
        readerThread.start();
    }

    @Override
    public void close() {
        readerThread.interrupt();
        try {
            readerThread.join(STOP_TIMEOUT_MS);
        } catch (InterruptedException e) {
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
                    log.info("Reader thread has been interrupted");
                    break;
                }
            }
        }
    }
}
