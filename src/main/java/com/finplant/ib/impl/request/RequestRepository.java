package com.finplant.ib.impl.request;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import io.reactivex.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.finplant.ib.IbClient;
import com.finplant.ib.IbExceptions;
import com.finplant.ib.impl.IdGenerator;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;

public class RequestRepository implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RequestRepository.class);
    private final IbClient client;
    private final IdGenerator idGenerator;

    private final Map<RequestKey, Request> requests = new ConcurrentHashMap<>();

    public RequestRepository(IbClient client, IdGenerator idGenerator) {
        this.client = client;
        this.idGenerator = idGenerator;
    }

    @Override
    public void close() {
        requests.values().forEach(Request::unregister);
        requests.clear();
        log.debug("RequestRepository is closed");
    }

    public <T> RequestBuilder<T> builder() {
        return new RequestBuilder<>();
    }

    public <Param> void onNext(Type type, Integer reqId, Param data, Boolean shouldExists) {
        get(type, reqId, shouldExists).ifPresent(request -> request.onNext(data));
    }

    public void onError(Type type, Integer reqId, Throwable throwable, Boolean shouldExists) {
        get(type, reqId, shouldExists).ifPresent(request -> request.onError(throwable));
    }

    public void onError(Type type, Integer reqId, Throwable throwable) {
        onError(type, reqId, throwable, true);
    }

    public void onError(Integer reqId, Throwable throwable) {
        get(null, reqId, true).ifPresent(request -> request.onError(throwable));
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

    public Object getUserData(Type type, int reqId) {
        return get(type, reqId, true).map(Request::getUserData).orElse(null);
    }

    private <T> Optional<Request<T>> get(Type type, Integer reqId, Boolean shouldExists) {
        @SuppressWarnings("unchecked")
        Request<T> request = requests.get(new RequestKey(type, reqId));
        if (request == null) {
            if (shouldExists) {
                log.error("Got event '{}' with unknown request id {}", type, reqId);
            }
        }
        return Optional.ofNullable(request);
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
        EVENT_MARKET_DATA_LVL2,
        EVENT_PORTFOLIO,
        EVENT_CONNECTION_STATUS,
        EVENT_HISTORICAL_DATA,
        REQ_MARKET_DATA,
        REQ_MARKET_DEPTH_EXCHANGES,
        REQ_CURRENT_TIME,
        REQ_ORDER_PLACE,
        REQ_ORDER_CANCEL,
        REQ_ORDER_LIST,
        REQ_CONTRACT_DETAIL,
        REQ_HISTORICAL_MIDPOINT_TICK,
        REQ_HISTORICAL_BID_ASK_TICK,
        REQ_HISTORICAL_TRADE,
        REQ_HISTORICAL_DATA,
        REQ_ACCOUNT_SUMMARY,
    }

    public class RequestBuilder<T> {
        private RequestRepository.Type type;
        private Consumer<Integer> register;
        private Consumer<Integer> unregister;
        private Object userData;
        private boolean withId = false;
        private Integer id;

        public RequestBuilder<T> type(RequestRepository.Type type) {
            this.type = type;
            return this;
        }

        public RequestBuilder<T> register(Consumer<Integer> register) {
            this.register = register;
            withId = true;
            return this;
        }

        public RequestBuilder<T> register(Runnable register) {
            this.register = unused -> register.run();
            return this;
        }

        public RequestBuilder<T> unregister(Consumer<Integer> unregister) {
            this.unregister = unregister;
            withId = true;
            return this;
        }

        public RequestBuilder<T> unregister(Runnable unregister) {
            this.unregister = unused -> unregister.run();
            return this;
        }

        public RequestBuilder<T> userData(Object userData) {
            this.userData = userData;
            return this;
        }

        public RequestBuilder<T> id(int id) {
            this.id = id;
            withId = true;
            return this;
        }

        public Observable<T> subscribe() {
            return subscribe(Schedulers.io());
        }

        public Observable<T> subscribe(Scheduler subscribeScheduler) {

            if (register == null) {
                throw new IllegalArgumentException("Registration function is mandatory");
            }
            if (type == null) {
                throw new IllegalArgumentException("Request type is mandatory");
            }

            return Observable.<T>create(emitter -> {

                if (withId && id == null) {
                    id = idGenerator.nextRequestId();
                }

                RequestKey key = new RequestKey(type, id);
                Request<T> request = new Request<>(emitter, key, register, unregister, userData);

                if (!client.isConnected()) {
                    throw new IbExceptions.NotConnectedError();
                }

                Request old = requests.putIfAbsent(key, request);
                if (old != null) {
                    log.error("Duplicated request: {}", key);
                    throw new IbExceptions.DuplicatedRequestError(key);
                }

                emitter.setCancellable(() -> {
                    if (client.isConnected()) {
                        log.debug("Unregister from {}", request);
                        request.unregister();
                    } else {
                        log.warn("Have no connection at unregister of {}", key);
                    }
                    remove(key);
                });

                request.register();
                log.info("Register to {}", request);
            }).subscribeOn(subscribeScheduler);
        }
    }
}
