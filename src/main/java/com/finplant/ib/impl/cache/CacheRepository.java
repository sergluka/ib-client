package com.finplant.ib.impl.cache;

import java.util.Collection;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.finplant.ib.impl.types.IbMarketDepth;
import com.finplant.ib.impl.types.IbOrder;
import com.finplant.ib.impl.types.IbPortfolio;
import com.finplant.ib.impl.types.IbPosition;
import com.finplant.ib.impl.types.IbTick;
import com.ib.client.Contract;

public interface CacheRepository {
    Map<Integer, IbOrder> getOrders();

    Map<IbMarketDepth.Key, IbMarketDepth> getOrderBook(Contract contract);

    IbTick getTick(int tickerId);

    Collection<IbPortfolio> getPortfolio();

    Collection<IbPosition> getPositions();

    @Nullable
    IbPosition getPosition(String account, Contract contract);

    @Nullable
    IbPortfolio getPortfolio(Contract contract);

    void clear();
}
