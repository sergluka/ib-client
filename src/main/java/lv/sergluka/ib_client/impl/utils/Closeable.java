package lv.sergluka.ib_client.impl.utils;

public interface Closeable extends AutoCloseable {

    @Override
    void close();
}