package lv.sergluka.ib_client.impl.request;

import lv.sergluka.ib_client.IbClient;
import lv.sergluka.ib_client.IbExceptions;
import lv.sergluka.ib_client.impl.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

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

    public <T> void onNext(Type type, Integer reqId, T data, Boolean shouldExists) {
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

    public void onError(Integer reqId, Throwable throwable, Boolean shouldExists) {
        get(null, reqId, shouldExists).ifPresent(request -> request.onError(throwable));
    }

    public void onComplete(Type type, Integer reqId, Boolean shouldExists) {
        get(type, reqId, shouldExists).ifPresent(Request::onComplete);
    }

    public <T> void onNextAndComplete(Type type, Integer reqId, T data, Boolean shouldExists) {
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
                log.error("Cannot find request '{}' id={}", type, reqId);
            } else {
                log.trace("Cannot find request '{}' id={}", type, reqId);
            }
        }
        return Optional.ofNullable(request);
    }

    private void remove(RequestKey key) {
        Request<?> prev = requests.remove(key);
        if (prev == null) {
            throw new IllegalArgumentException(String.format("Unknown request: %s", key));
        }
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
        EVENT_HISTORICAL_DATA,
        EVENT_EXECUTION_INFO,
        REQ_MARKET_DATA,
        REQ_MARKET_DEPTH_EXCHANGES,
        REQ_CURRENT_TIME,
        REQ_ORDER_PLACE,
        REQ_ORDER_CANCEL,
        REQ_ORDER_LIST,
        REQ_CONTRACT_DETAIL,
        REQ_CONTRACT_DESCRIPTION,
        REQ_HISTORICAL_MIDPOINT_TICK,
        REQ_HISTORICAL_BID_ASK_TICK,
        REQ_HISTORICAL_TRADE,
        REQ_HISTORICAL_DATA,
        REQ_ACCOUNT_SUMMARY,
        REQ_MARKET_RULE,
    }

    public class RequestBuilder<T> {
        private RequestRepository.Type type;
        private Consumer<Integer> register;
        private Consumer<Integer> unregister;
        private Object userData;
        private boolean withId = false;
        private Integer id;

        public RequestBuilder<T> type(RequestRepository.Type newType) {
            this.type = newType;
            return this;
        }

        public RequestBuilder<T> register(Consumer<Integer> newRegister) {
            this.register = newRegister;
            withId = true;
            return this;
        }

        public RequestBuilder<T> register(Runnable newRegister) {
            this.register = unused -> newRegister.run();
            return this;
        }

        public RequestBuilder<T> register(int newId, Runnable newRegister) {
            this.id = newId;
            this.register = unused -> newRegister.run();
            return this;
        }

        public RequestBuilder<T> unregister(Consumer<Integer> newUnregister) {
            this.unregister = newUnregister;
            withId = true;
            return this;
        }

        public RequestBuilder<T> unregister(Runnable newUnregister) {
            this.unregister = unused -> newUnregister.run();
            return this;
        }

        public RequestBuilder<T> userData(Object data) {
            this.userData = data;
            return this;
        }

        public RequestBuilder<T> id(int newId) {
            this.id = newId;
            withId = true;
            return this;
        }

        public Flux<T> subscribe() {

            return Flux.create(emitter -> {
                if (register == null) {
                    emitter.error(new IllegalArgumentException("Registration function is mandatory"));
                    return;
                }
                if (type == null) {
                    emitter.error(new IllegalArgumentException("Request type is mandatory"));
                    return;
                }

                Integer requestId;

                if (withId && id == null) {
                    requestId = idGenerator.nextId();
                } else {
                    requestId = id;
                }

                RequestKey key = new RequestKey(type, requestId);
                Request<T> request = new Request<>(emitter, key, register, unregister, userData);

                if (!client.isConnected()) {
                    emitter.error(new IbExceptions.NotConnectedError());
                    return;
                }

                Request<?> old = requests.putIfAbsent(key, request);
                if (old != null) {
                    log.error("Duplicated request: {}", key);
                    emitter.error(new IbExceptions.DuplicatedRequestError(key));
                    return;
                }

                emitter.onDispose(() -> {
                    remove(key);
                    if (client.isConnected()) {
                        log.debug("Unregister from {}", request);
                        request.unregister();
                    } else {
                        log.debug("Have no connection at unregister of {}", key);
                    }
                });

                request.register();
                log.info("Register to {}", request);
            });
        }
    }
}
