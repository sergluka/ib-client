package lv.sergluka.ib.impl.cache;

import com.google.common.collect.ImmutableMap;
import com.ib.client.Contract;
import lv.sergluka.ib.impl.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

// TODO: some entries can remain forever. We have to cleanup expired entries.
// TODO: Expose repository interface to hide `add` functions
public class CacheRepository {

    private static final Logger log = LoggerFactory.getLogger(CacheRepository.class);

    private final ConcurrentHashMap<Integer, IbOrder> orders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<PositionKey, IbPosition> positions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, IbTick> ticks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, IbPortfolio> portfolioContracts = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<IbOrderBook.Key, IbOrderBook> orderBook = new ConcurrentHashMap<>();

    // After order placing, some statuses goes first, before `openOrder` callback, so storing then separately
    private final LinkedHashMap<Integer, Set<IbOrderStatus>> statuses = new LinkedHashMap<>();

    public boolean addOrder(IbOrder order) {
        final Set<IbOrderStatus> set = statuses.remove(order.getOrderId());
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

    public Map<Integer, IbOrder> getOrders() {
        return ImmutableMap.copyOf(orders);
    }

    public boolean addNewStatus(@NotNull IbOrderStatus status) {
        IbOrder order = orders.get(status.getOrderId());
        if (order == null) {
            log.debug("Status update for not (yet?) existing order {}: {}", status.getOrderId(), status);

            final Set<IbOrderStatus> set = statuses.computeIfAbsent(status.getOrderId(), (key) -> new HashSet<>());
            if (!set.add(status)) {
                log.debug("[{}]: Status '{}' already exists", status.getOrderId(), status.getStatus());
                return false;
            }

            return false;
        }

        return order.addStatus(status);
    }

    public void updatePosition(IbPosition position) {
        positions.put(new PositionKey(position.getAccount(), position.getContract().conid()), position);
    }

    public void updatePortfolio(IbPortfolio portfolio) {
        portfolioContracts.put(portfolio.getContract().conid(), portfolio);
    }

    public IbTick updateTick(int tickerId, Consumer<IbTick> consumer) {
        IbTick tick = ticks.computeIfAbsent(tickerId, (key) -> new IbTick()) ;
        consumer.accept(tick);
        return tick;
    }

    public void addOrderBook(IbOrderBook orderBook) {
        this.orderBook.put(new IbOrderBook.Key(orderBook.getSide(), orderBook.getPosition()), orderBook);
    }

    public Map<IbOrderBook.Key, IbOrderBook> getOrderBook() {
        return Collections.unmodifiableMap(orderBook);
    }

    public IbTick getTick(int tickerId) {
        return ticks.get(tickerId);
    }

    public Collection<IbPosition> getPositions() {
        return Collections.unmodifiableCollection(positions.values());
    }

    public Collection<IbPortfolio> getPortfolio() {
        return Collections.unmodifiableCollection(portfolioContracts.values());
    }

    @Nullable
    public IbPosition getPosition(String account, Contract contract) {
        Objects.requireNonNull(account);
        Objects.requireNonNull(contract);
        if (contract.conid() == 0) {
            throw new IllegalArgumentException("Contract has a id 0");
        }
        return positions.get(new PositionKey(account, contract.conid()));
    }

    @Nullable
    public IbPortfolio getPortfolio(Contract contract) {
        Objects.requireNonNull(contract);
        if (contract.conid() == 0) {
            throw new IllegalArgumentException("Contract has a id 0");
        }
        return portfolioContracts.get(contract.conid());
    }
}
