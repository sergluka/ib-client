package com.finplant.ib.impl.request;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;

import com.finplant.ib.IbClient;
import com.finplant.ib.IbExceptions;
import com.finplant.ib.IdGenerator;

public class RequestRepository implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RequestRepository.class);
    private final IbClient client;
    private final IdGenerator idGenerator;
    private final Map<RequestKey, Request> requests = new ConcurrentHashMap<>();
    public RequestRepository(@NotNull IbClient client, IdGenerator idGenerator) {
        this.client = client;
        this.idGenerator = idGenerator;
    }

    @Override
    public void close() {
        requests.values().forEach(Request::unregister);
        requests.clear();
        log.debug("RequestRepository is closed");
    }

    @NotNull
    public <RetResult> Observable<RetResult> addRequestWithId(@NotNull Type type,
                                                              @Nullable Consumer<Integer> register,
                                                              @Nullable Consumer<Integer> unregister) {
        int id = idGenerator.nextRequestId();
        return addRequest(type, id, register, unregister);
    }

    @NotNull
    public <RetResult> Observable<RetResult> addRequestWithoutId(@NotNull Type type,
                                                                 @Nullable Consumer<Integer> register,
                                                                 @Nullable Consumer<Integer> unregister) {
        return addRequest(type, null, register, unregister);
    }

    @NotNull
    public <RetResult> Observable<RetResult> addRequest(@NotNull Type type,
                                                        @Nullable Integer id,
                                                        @Nullable Consumer<Integer> register,
                                                        @Nullable Consumer<Integer> unregister) {

        Observable<RetResult> observable = Observable.create(emitter -> {

            if (!client.isConnected()) {
                throw new IbExceptions.NotConnected();
            }

            RequestKey key = new RequestKey(type, id);
            Request request = new Request<>(emitter, key, register, unregister);

            Request old = requests.putIfAbsent(key, request);
            if (old != null) {
                log.error("Duplicated request: {}", key);
                throw new IbExceptions.DuplicatedRequest(key);
            }

            emitter.setCancellable(() -> {
                if (client.isConnected()) {
                    request.unregister();
                } else {
                    log.warn("Have no connection at unregister of {}", key);
                }
                remove(key);
            });

            request.register();
            log.info("Register to {}", request);
        });

        return observable.observeOn(Schedulers.io());
    }

    public <Param> void onNext(Type type, Integer reqId, Param data, Boolean shouldExists) {
        get(type, reqId, shouldExists).ifPresent(request -> request.onNext(data));
    }

    public void onError(Type type, Integer reqId, Throwable throwable) {
        get(type, reqId, true).ifPresent(request -> request.onError(throwable));
    }

    public void onError(Integer reqId, Throwable throwable) {
        get(null, reqId, true).ifPresent(request -> request.onError(throwable));
    }

    public <T> Optional<Request<T>> get(Type type, Integer reqId, Boolean shouldExists) {
        @SuppressWarnings("unchecked")
        Request<T> request = requests.get(new RequestKey(type, reqId));
        if (request == null) {
            if (shouldExists) {
                log.error("Got event '{}' with unknown request id {}", type, reqId);
            }
        }
        return Optional.ofNullable(request);
    }

    public void onComplete(Type type, Integer reqId, Boolean shouldExists) {
        get(type, reqId, shouldExists).ifPresent(Request::onComplete);
    }

    public <Param> void onNextAndComplete(Type type, Integer reqId, Param data, Boolean shouldExists) {
        get(type, reqId, shouldExists).ifPresent(request -> {
            request.onNext(data);
            request.onComplete();
        });
    }

    private void remove(RequestKey key) {
        requests.remove(key);
    }

    public enum Type {
        EVENT_CONTRACT_PNL,
        EVENT_ACCOUNT_PNL,
        EVENT_POSITION,
        EVENT_POSITION_MULTI,
        EVENT_ORDER_STATUS,
        EVENT_MARKET_DATA,
        EVENT_PORTFOLIO,
        EVENT_CONNECTION_STATUS,
        REQ_MARKET_DATA,
        REQ_CURRENT_TIME,
        REQ_ORDER_PLACE,
        REQ_ORDER_LIST,
        REQ_CONTRACT_DETAIL
    }
}
