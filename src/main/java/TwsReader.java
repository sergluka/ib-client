import com.ib.client.EClientSocket;
import com.ib.client.EJavaSignal;
import com.ib.client.EReader;
import com.sun.xml.internal.ws.Closeable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TwsReader implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(TwsReader.class);

    private final Thread readerThread = new Thread(this::processMessages);
    private final EReader reader;
    private final EJavaSignal signal;
    private final EClientSocket socket;

    TwsReader(EClientSocket socket, final EJavaSignal signal) {
        this.socket = socket;
        this.signal = signal;
        reader = new EReader(socket, signal);
    }

    void start() {
        readerThread.start();
    }

    void stop() {
        signal.issueSignal();
        readerThread.interrupt();
        try {
            readerThread.join(1000);
        } catch (InterruptedException e) {
            log.warn("Timeout of reader thread shutdown");
        }
    }

    @Override
    public void close() {
        stop();
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
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }
}
