package lv.sergluka.tws.impl.sender;

import com.google.common.collect.ImmutableList;
import lv.sergluka.tws.impl.types.TwsOrder;
import lv.sergluka.tws.impl.types.TwsOrderStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class CacheRepository {

    private static final Logger log = LoggerFactory.getLogger(CacheRepository.class);

    public List<TwsOrder> getOrders() {
        return ImmutableList.copyOf(orders.values());
    }

    private final ConcurrentHashMap<Integer, TwsOrder> orders = new ConcurrentHashMap<>();

    // AFter order placing, some statuses goes first, before `openOrder` callback, so storing then separately
    private final LinkedHashMap<Integer, Set<TwsOrderStatus>> statuses = new LinkedHashMap<>();

    public CacheRepository() {
    }

    public boolean addOrder(TwsOrder order) {
        final Set<TwsOrderStatus> set = statuses.remove(order.getOrderId());
        if (set != null) {
            order.addStatuses(set);
        }
        final AtomicReference<Boolean> result = new AtomicReference<>(false);
        orders.compute(order.getOrderId(), (key, value)-> {
            if (value != null) {
                log.debug("Order {} already has been added", order.getOrderId());
                result.set(false);
                return value;
            }

            result.set(true);
            return order;
        });

        return result.get();
    }

    public boolean addNewStatus(@NotNull TwsOrderStatus status) {
        TwsOrder order = orders.get(status.getOrderId());
        if (order == null) {
            log.debug("Status update for not (yet?) existing order {}: {}", status.getOrderId(), status);

            final Set<TwsOrderStatus> set = statuses.computeIfAbsent(status.getOrderId(), (key) -> new HashSet<>());
            if (!set.add(status)) {
                log.debug("[{}]: Status '{}' already exists", status.getOrderId(), status.getStatus());
                return false;
            }

            return false;
        }

        return order.addStatus(status);
    }
}
