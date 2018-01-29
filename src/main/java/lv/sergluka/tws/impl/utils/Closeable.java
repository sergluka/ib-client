package lv.sergluka.tws.impl.utils;

public interface Closeable extends AutoCloseable {

    @Override
    void close();
}