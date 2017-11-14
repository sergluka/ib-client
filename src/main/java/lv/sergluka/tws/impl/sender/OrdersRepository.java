package lv.sergluka.tws.impl.sender;

import lv.sergluka.tws.impl.types.TwsOrder;
import lv.sergluka.tws.impl.types.TwsOrderStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class OrdersRepository {

    private static final Logger log = LoggerFactory.getLogger(OrdersRepository.class);

    private final ConcurrentHashMap<Integer, TwsOrder> orders = new ConcurrentHashMap<>();
    private final BiConsumer<Integer, TwsOrderStatus> onNewStatus;

    public OrdersRepository(BiConsumer<Integer, TwsOrderStatus> onNewStatus) {
        this.onNewStatus = onNewStatus;
    }

    public boolean addNewStatus(int orderId, @NotNull TwsOrderStatus status) {
        final TwsOrder order = orders.computeIfAbsent(orderId, TwsOrder::new);
        if (order.statusExists(status)) {
            log.debug("[{}]: Status '{}' already exists", orderId, status.getStatus());
            return false;
        }

        order.addStatus(status);
        onNewStatus.accept(orderId, status);
        return true;
    }
}
