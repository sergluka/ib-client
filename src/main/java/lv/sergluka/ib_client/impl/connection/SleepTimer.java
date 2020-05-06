package lv.sergluka.ib_client.impl.connection;

class SleepTimer {

    private Long triggerTime;
    private Runnable runnable;

    synchronized void start(Long delay, Runnable callback) {
        if (delay == 0) {
            callback.run();
            return;
        }

        this.triggerTime = System.currentTimeMillis() + delay;
        this.runnable = callback;
    }

    synchronized void reset() {
        triggerTime = null;
    }

    synchronized boolean isRunning() {
        return triggerTime != null;
    }

    synchronized boolean run() {
        if (triggerTime != null && System.currentTimeMillis() >= triggerTime) {
            triggerTime = null;
            runnable.run();

            return true;
        }

        return false;
    }
}
