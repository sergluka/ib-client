package lv.sergluka.ib_client;

import java.util.Collection;
import java.util.Map;

import lv.sergluka.ib_client.types.IbMarketDepth;
import lv.sergluka.ib_client.types.IbOrder;
import lv.sergluka.ib_client.types.IbPortfolio;
import lv.sergluka.ib_client.types.IbPosition;
import lv.sergluka.ib_client.types.IbTick;
import com.ib.client.Contract;

@SuppressWarnings("unused")
public interface CacheRepository {
    Map<Integer, IbOrder> getOrders();

    Map<IbMarketDepth.Key, IbMarketDepth> getOrderBook(Contract contract);

    IbTick getTick(int tickerId);

    Collection<IbPortfolio> getPortfolio();

    Collection<IbPosition> getPositions();

    IbPosition getPosition(String account, Contract contract);

    IbPortfolio getPortfolio(Contract contract);

    void clear();
}
