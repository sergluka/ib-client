package com.finplant.ib.impl.connection;

class SleepTimer {

    private Long triggerTime;
    private Runnable runnable;

    synchronized void start(Long delay, Runnable runnable) {
        this.triggerTime = System.currentTimeMillis() + delay;
        this.runnable = runnable;
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
