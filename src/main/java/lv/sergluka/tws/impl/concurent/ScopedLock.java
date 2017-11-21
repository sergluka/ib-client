package lv.sergluka.tws.impl.concurent;

import java.util.concurrent.locks.ReentrantLock;

public class ScopedLock {
    private ReentrantLock lock = new ReentrantLock();

    public void block(Runnable runnable) {
        try {
            lock.lock();
            runnable.run();
        } finally {
            lock.unlock();
        }
    }
}
