package lv.sergluka.ib_client.impl.cache;

import lv.sergluka.ib_client.CacheRepository;
import lv.sergluka.ib_client.types.*;
import com.google.common.collect.ImmutableMap;
import com.ib.client.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class CacheRepositoryImpl implements CacheRepository {

    private static final Logger log = LoggerFactory.getLogger(CacheRepositoryImpl.class);

    private final ConcurrentHashMap<Integer, IbOrder> orders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<PositionKey, IbPosition> positions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, IbTickImpl> ticks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, IbPortfolio> portfolioContracts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, IbAccountsSummary> accountSummaries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, IbExecutionReport> execReports = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Integer, Map<IbMarketDepth.Key, IbMarketDepth>> orderBooks =
            new ConcurrentHashMap<>();

    public boolean addOrder(IbOrder order) {

        final AtomicReference<Boolean> result = new AtomicReference<>(false);
        orders.compute(order.getOrderId(), (key, value) -> {
            if (value != null) {
                log.debug("Order {} already has been added", order.getOrderId());
                result.set(false);
                order.addStatuses(value.getStatuses());
                return order;
            }

            result.set(true);
            return order;
        });

        return result.get();
    }

    @Override
    public Map<Integer, IbOrder> getOrders() {
        return ImmutableMap.copyOf(orders);
    }

    public boolean addNewStatus(IbOrderStatus status) {
        IbOrder order = orders.get(status.getOrderId());
        if (order == null) {
            log.error("Status update for not (yet?) existing order {}: {}", status.getOrderId(), status);
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

    public IbTick updateTick(int tickerId, Consumer<IbTickImpl> consumer) {
        IbTickImpl tick = ticks.computeIfAbsent(tickerId, (key) -> new IbTickImpl());
        tick.refreshUpdateTime();
        consumer.accept(tick);
        return tick;
    }

    public void updateAccountsSummary(int id, String account, String tag, String value, String currency) {
        IbAccountsSummary accountsSummary = accountSummaries.computeIfAbsent(id, (key) -> new IbAccountsSummary());
        try {
            accountsSummary.update(account, tag, value, currency);
        } catch (Exception e) {
            log.error("Cannot update account summary: {}", e.getMessage(), e);
        }
    }

    public IbAccountsSummary popAccountsSummary(int id) {
        return accountSummaries.remove(id);
    }

    @Override
    public Map<IbMarketDepth.Key, IbMarketDepth> getOrderBook(Contract contract) {
        Objects.requireNonNull(contract, "'contract' parameter is null");
        if (contract.conid() == 0) {
            throw new IllegalArgumentException("contract ID is missing");
        }

        return orderBooks.get(contract.conid());
    }

    @Override
    public IbTick getTick(int tickerId) {
        return ticks.get(tickerId);
    }

    @Override
    public Collection<IbPosition> getPositions() {
        return Collections.unmodifiableCollection(positions.values());
    }

    @Override
    public Collection<IbPortfolio> getPortfolio() {
        return Collections.unmodifiableCollection(portfolioContracts.values());
    }

    @Override
    public IbPosition getPosition(String account, Contract contract) {
        Objects.requireNonNull(account);
        Objects.requireNonNull(contract);
        if (contract.conid() == 0) {
            throw new IllegalArgumentException("Contract has a id 0");
        }
        return positions.get(new PositionKey(account, contract.conid()));
    }

    @Override
    public IbPortfolio getPortfolio(Contract contract) {
        Objects.requireNonNull(contract);
        if (contract.conid() == 0) {
            throw new IllegalArgumentException("Contract has a id 0");
        }
        return portfolioContracts.get(contract.conid());
    }

    public void addMarketDepth(Contract contract, IbMarketDepth marketDepth, IbMarketDepth.Operation operation) {

        orderBooks.compute(contract.conid(), (key, value) -> {
            if (value == null) {
                value = new HashMap<>();
            }

            switch (operation) {
                case INSERT:
                    log.trace("Market depth is added: {}", marketDepth);
                    value.put(marketDepth.key(), marketDepth);
                    break;
                case UPDATE:
                    log.trace("Market depth is updated: {}", marketDepth);
                    value.put(marketDepth.key(), marketDepth);
                    break;
                case REMOVE:
                    log.trace("Market depth is removed: {}", marketDepth);
                    value.remove(marketDepth.key());
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + operation);
            }

            return value;
        });
    }

    public void addExecutionReport(IbContract contract, IbExecution execution) {
        IbExecutionReport report = execReports.put(execution.getExecId(), new IbExecutionReport(contract, execution));
        if (report != null) {
            log.warn("Execution info for '{}' is overwritten", execution.getExecId());
        }
    }

    public Optional<IbExecutionReport> updateExecutionReport(IbCommissionReport report) {
        IbExecutionReport execReport = execReports.get(report.getExecId());
        if (execReport != null) {
            execReport.setCommission(report);
        } else {
            log.warn("Commission report for '{}' without execution report", report.getExecId());
        }

        return Optional.ofNullable(execReport);
    }

    @Override
    public void clear() {
        orders.clear();
        positions.clear();
        ticks.clear();
        portfolioContracts.clear();
        orderBooks.clear();
        execReports.clear();

        log.debug("Cache is cleared");
    }
}
