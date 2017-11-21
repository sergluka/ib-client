package lv.sergluka.tws.impl.sender;

import lv.sergluka.tws.impl.types.TwsOrderStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

public class OrderStatusesRepository {

    private static final Logger log = LoggerFactory.getLogger(OrderStatusesRepository.class);

    private final LinkedHashMap<Integer, Set<TwsOrderStatus>> statuses = new LinkedHashMap<>();

    public OrderStatusesRepository() {
    }

    public boolean addNewStatus(@NotNull TwsOrderStatus status) {
        final Set<TwsOrderStatus> set = statuses.computeIfAbsent(status.getOrderId(), (key) -> new HashSet<>());
        if (!set.add(status)) {
            log.debug("[{}]: Status '{}' already exists", status.getOrderId(), status.getStatus());
            return false;
        }

        return true;
    }
}
