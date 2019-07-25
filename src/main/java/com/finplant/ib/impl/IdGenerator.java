package com.finplant.ib.impl;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdGenerator {

    private static final Logger log = LoggerFactory.getLogger(IdGenerator.class);

    private static final int INVALID_ID = -1;

    private final AtomicInteger orderId = new AtomicInteger(INVALID_ID);

    // Returns return true if TWS resets its ID
    boolean setId(Integer newValue) {
        int oldValue = orderId.getAndSet(newValue);

        if (newValue < oldValue) {
            log.warn("TWS resets request ID: {} => {}", oldValue, newValue);
            return true;
        }

        return false;
    }

    public int nextId() {
        if (orderId.get() == INVALID_ID) {
            throw new IllegalStateException("Has no request ID from TWS");
        }

        return orderId.getAndIncrement();
    }
}
