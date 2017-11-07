import com.ib.client.EClientSocket;
import com.ib.client.EJavaSignal;
import com.ib.client.EReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TwsReader {

    private static final Logger log = LoggerFactory.getLogger(TwsReader.class);

    private final Thread readerThread = new Thread(this::processMessages);
    private final EJavaSignal m_signal = new EJavaSignal();
    private final EReader reader;
    private final EClientSocket socket;

    public TwsReader(EClientSocket socket) {
        this.socket = socket;
        reader = new EReader(socket, m_signal);
    }

    void start() {
        readerThread.start();
    }

    private void processMessages() {

        while (!Thread.interrupted()) {
            if (socket.isConnected()) {
                m_signal.waitForSignal();
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
