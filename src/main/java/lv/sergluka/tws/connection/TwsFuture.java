package lv.sergluka.tws.connection;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class TwsFuture<T> {

    private final Condition condition;
    private final Lock lock;
    private final Runnable onTimeout;

    private T value;
    private boolean done;

    TwsFuture(Runnable onTimeout) {
        this.onTimeout = onTimeout;

        lock = new ReentrantLock();
        condition = lock.newCondition();
    }

    public boolean isDone() {
        return done;
    }

    public T get() {
        try {
            lock.lock();
            while (!isDone()) {
                condition.await();
            }
        } catch (InterruptedException e) {
            return null;
        }
        finally {
            lock.unlock();
        }

        return value;
    }

    public T get(final long timeout, final TimeUnit unit) throws TimeoutException {
        try {
            lock.lock();
            while (!isDone()) {
                if (condition.await(timeout, unit)) {
                    break;
                }
                onTimeout.run();
                throw new TimeoutException("Request timeout");
            }
        } catch (InterruptedException e) {
            return null;
        } finally {
            lock.unlock();
        }

        return value;
    }

    void setDone(T value) {
        lock.lock();
        try {
            this.value = value;
            done = true;
            condition.signal();
        } finally {
            lock.unlock();
        }
    }
}
