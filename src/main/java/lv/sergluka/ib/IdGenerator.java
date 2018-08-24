package lv.sergluka.ib;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdGenerator {

    private static final Logger log = LoggerFactory.getLogger(IdGenerator.class);

    private static final int INVALID_ID = -1;

    private AtomicInteger orderId = new AtomicInteger(INVALID_ID);
    private AtomicInteger requestId = new AtomicInteger(100_000_000);

    public void setOrderId(Integer newValue) {
        int oldValue = orderId.getAndSet(newValue);

        if (newValue < oldValue) {
            log.warn("TWS resets request ID: {} => {}", oldValue, newValue);
        }
    }

    public int nextOrderId() {
        if (orderId.get() == INVALID_ID) {
            throw new IllegalStateException("Has no request ID from TWS"); // TODO: Wait until we got one
        }

        return orderId.getAndIncrement();
    }

    public int nextRequestId() {
        return requestId.getAndIncrement();
    }
}
