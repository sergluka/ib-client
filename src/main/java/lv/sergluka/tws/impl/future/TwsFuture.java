package lv.sergluka.tws.impl.future;

import lv.sergluka.tws.TwsExceptions;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TwsFuture<T> {

    protected T value;

    private final Condition condition;
    private final Lock lock;
    private final Runnable onTimeout;

    private boolean done = false;
    private RuntimeException exception;

    public TwsFuture(@NotNull Runnable onTimeout) {
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

        if (exception != null) {
            throw exception;
        }
        return value;
    }

    public T get(long timeout, TimeUnit unit) {
        try {
            lock.lock();
            while (!isDone()) {
                if (!condition.await(timeout, unit)) {
                    onTimeout.run();
                    throw new TwsExceptions.ResponseTimeout("Request timeout");
                }
            }
        } catch (InterruptedException e) {
            return null;
        } finally {
            lock.unlock();
        }

        if (exception != null) {
            throw exception;
        }
        return value;
    }

    public void setDone(T value) {
        lock.lock();
        try {
            if (value != null) {
                this.value = value;
            }
            done = true;
            condition.signal();
        } finally {
            lock.unlock();
        }
    }

    public void setException(RuntimeException e) {
        lock.lock();
        try {
            exception = e;
            done = true;
            condition.signal();
        } finally {
            lock.unlock();
        }
    }
}
