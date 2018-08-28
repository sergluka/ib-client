package com.finplant.ib;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdGenerator {

    private static final Logger log = LoggerFactory.getLogger(IdGenerator.class);

    private static final int INVALID_ID = -1;

    private AtomicInteger orderId = new AtomicInteger(INVALID_ID);
    private AtomicInteger requestId = new AtomicInteger(100_000_000);

    /**
     * @return true if TWS resets its ID
     */
    public boolean setOrderId(Integer newValue) {
        int oldValue = orderId.getAndSet(newValue);

        if (newValue < oldValue) {
            log.warn("TWS resets request ID: {} => {}", oldValue, newValue);
            return true;
        }

        return false;
    }

    public int nextOrderId() {
        if (orderId.get() == INVALID_ID) {
            throw new IllegalStateException("Has no request ID from TWS");
        }

        return orderId.getAndIncrement();
    }

    public int nextRequestId() {
        return requestId.getAndIncrement();
    }
}
