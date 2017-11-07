import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TwsFuture<T> {

    private final Condition condition;
    private final Lock lock;

    private T value;
    private boolean done;

    TwsFuture() {
        lock = new ReentrantLock();
        condition = lock.newCondition();
    }

    public boolean isDone() {
        return done;
    }

    public T get() throws ExecutionException {
        try {
            lock.lock();
            condition.await();
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
            condition.await(timeout, unit);
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

    void setDone() {
        lock.lock();
        try {
            done = true;
            condition.signal();
        } finally {
            lock.unlock();
        }
    }
}
