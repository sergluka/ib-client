//package lv.sergluka.ib.impl.subscription;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.util.Map;
//import java.util.function.Consumer;
//import java.util.function.Function;
//
//public class SubscriptionImpl<Param, RegResult> implements IbSubscription {
//
//    private static final Logger log = LoggerFactory.getLogger(SubscriptionImpl.class);
//
//    private final Map<SubscriptionsRepository.Key, SubscriptionImpl> subscriptions;
//    private final SubscriptionsRepository.Key key;
//    private final Consumer<Param> callbackFn;
//    private final Function<Integer, RegResult> registrationFn;
//    private final Consumer<Integer> unregistrationFn;
//
//    SubscriptionImpl(Map<SubscriptionsRepository.Key, SubscriptionImpl> subscriptions,
//                     SubscriptionsRepository.Key key,
//                     Consumer<Param> callbackFn,
//                     Function<Integer, RegResult> registrationFn,
//                     Consumer<Integer> unregistrationFn) {
//        this.subscriptions = subscriptions;
//        this.key = key;
//        this.callbackFn = callbackFn;
//        this.registrationFn = registrationFn;
//        this.unregistrationFn = unregistrationFn;
//    }
//
//    Integer getId() {
//        return key.id;
//    }
//
//    RegResult subscribe(Integer id) {
//        if (registrationFn != null) {
//            return registrationFn.apply(id);
//        }
//        return null;
//    }
//
//    public void call(Param data) {
//        callbackFn.accept(data);
//    }
//
//    public void unsubscribe() {
//        try {
//            if (subscriptions.remove(key) == null) {
//                log.error("Cannot locate subscription to unsubscribe: {}", this);
//            }
//            if (unregistrationFn != null) {
//                unregistrationFn.accept(key.id);
//            }
//            log.info("Has been unsubscribed from {}", this);
//        } catch (Exception e) {
//            log.error("Error unsubscribe for subscription: {}. {}", this, e.getMessage());
//        }
//    }
//
//    @Override
//    public String toString() {
//        final StringBuffer buffer = new StringBuffer("{");
//        buffer.append("id=").append(key.id);
//        buffer.append(", type=").append(key.type);
//        buffer.append('}');
//        return buffer.toString();
//    }
//}
