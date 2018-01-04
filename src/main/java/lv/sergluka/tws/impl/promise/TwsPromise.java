package lv.sergluka.tws.impl.promise;

import lv.sergluka.tws.TwsExceptions;
import lv.sergluka.tws.impl.sender.EventKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class TwsPromise<T> {

    private static final Logger log = LoggerFactory.getLogger(TwsPromise.class);

    protected T value;

    private final Runnable onTimeout;
    private final EventKey event;
    private final Consumer<T> consumer;

    private AtomicBoolean done = new AtomicBoolean(false);
    private final Condition condition;
    private final Lock lock;

    private RuntimeException exception;

    public TwsPromise(EventKey event, Consumer<T> consumer, Runnable onTimeout) {
        this.event = event;
        this.consumer = consumer;
        this.onTimeout = onTimeout;

        lock = new ReentrantLock();
        condition = lock.newCondition();
    }

    public T get() {
        try {
            lock.lock();
            while (!done.get()) {
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
            while (!done.get()) {
                if (!condition.await(timeout, unit)) {
                    onTimeout.run();
                    throw new TwsExceptions.ResponseTimeout(
                            String.format("Request timeout (%ds): %s", unit.toSeconds(timeout), event));
                }
            }
        } catch (InterruptedException e) {
            log.warn("Promise has been interrupted");
            return null;
        } finally {
            lock.unlock();
        }

        if (exception != null) {
            throw exception;
        }
        return value;
    }

    public void modify(Class<T> clazz, Consumer<T> consumer) {
        if (value == null) { // TODO: refactor this mess
            try {
                value = clazz.newInstance();
            } catch (Exception e) {
                log.error("Cannot instantiate class", e);
                return;
            }
        }
        consumer.accept(value);
    }

    public void setDone(T value) {
        lock.lock();
        try {
            if (value != null) {
                this.value = value;
            }
            if (consumer != null) {
                consumer.accept(this.value);
            }

            done.set(true);
            condition.signal();
        } finally {
            lock.unlock();
        }
    }

    public void setException(RuntimeException e) {
        lock.lock();
        try {
            exception = e;
            done.set(true);
            condition.signal();
        } finally {
            lock.unlock();
        }
    }
}
