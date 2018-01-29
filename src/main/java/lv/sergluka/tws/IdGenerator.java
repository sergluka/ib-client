package lv.sergluka.tws;

import java.util.concurrent.atomic.AtomicInteger;

public class IdGenerator {

    private static final int INVALID_ID = -1;

    private AtomicInteger orderId = new AtomicInteger(INVALID_ID);
    private AtomicInteger requestId = new AtomicInteger(100_000_000);

    public void setOrderId(Integer id) {
        orderId.set(id);
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
