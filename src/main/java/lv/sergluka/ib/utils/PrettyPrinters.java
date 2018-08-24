package lv.sergluka.ib.utils;

import com.ib.client.Contract;
import com.ib.client.Order;

public class PrettyPrinters {

    static public String contractToString(Contract contract) {
        final StringBuilder buffer = new StringBuilder("{");
        buffer.append("conid=").append(contract.conid());
        buffer.append(", symbol=").append(contract.symbol());
        buffer.append(", currency=").append(contract.currency());
        buffer.append(", exchange=").append(contract.exchange());
        buffer.append(", multiplier=").append(contract.multiplier());
        buffer.append(", lastTradeDateOrContractMonth=").append(contract.lastTradeDateOrContractMonth());
        buffer.append(", localSymbol=").append(contract.localSymbol());
        buffer.append("}");
        return buffer.toString();
    }

    static public String orderToString(Order order) {

        double UNDEFINED_DBL = 1.7976931348623157E308D;

        final StringBuilder buffer = new StringBuilder("{");
        buffer.append("id=").append(order.orderId());
        buffer.append(", action=").append(order.action());

        if (order.orderType() != null) {
            buffer.append(", orderType=").append(order.orderType());
        }
        if (order.auxPrice() != UNDEFINED_DBL) {
            buffer.append(", auxPrice=").append(order.auxPrice());
        }
        if (order.lmtPrice() != UNDEFINED_DBL) {
            buffer.append(", lmtPrice=").append(order.lmtPrice());
        }
        if (order.tif() != null) {
            buffer.append(", tif=").append(order.tif());
        }

        buffer.append(", totalQuantity=").append(order.totalQuantity());
        buffer.append(", outsideRth=").append(order.outsideRth());
        buffer.append("}");
        return buffer.toString();
    }

}
