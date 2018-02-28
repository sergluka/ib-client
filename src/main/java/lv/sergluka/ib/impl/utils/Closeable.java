package lv.sergluka.ib.impl.utils;

public interface Closeable extends AutoCloseable {

    @Override
    void close();
}