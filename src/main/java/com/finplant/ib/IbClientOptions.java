package com.finplant.ib;

import java.time.Duration;

public class IbClientOptions {

    private Duration connectionDelay = Duration.ofSeconds(10);

    public IbClientOptions() {
    }

    /**
     * Delay before connection to TWS.
     * <p>
     * In case we connect to TWS too frequently, it can return "Bad Message Length" because new connection
     * somehow interfere with the previous one. To workaround it, we wait a bit before every connect
     *
     * @param connectionDelay Connection delay
     * @return this
     */
    public IbClientOptions connectionDelay(Duration connectionDelay) {
        this.connectionDelay = connectionDelay;
        return this;
    }

    Duration getConnectionDelay() {
        return connectionDelay;
    }
}
