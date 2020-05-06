package lv.sergluka.ib_client.params;

import java.time.Duration;

public class IbClientOptions {

    private static final int DEFAULT_DELAY_S = 10;

    private Duration connectionDelay = Duration.ofSeconds(DEFAULT_DELAY_S);

    /**
     * Delay before connection to TWS.
     *
     * <p>In case we connect to TWS too frequently, it can return "Bad Message Length" because new connection
     * somehow interfere with the previous one. To workaround it, we wait a bit before every connect
     *
     * @param delay Connection delay
     * @return this
     */
    public IbClientOptions connectionDelay(Duration delay) {
        this.connectionDelay = delay;
        return this;
    }

    public Duration getConnectionDelay() {
        return connectionDelay;
    }
}
