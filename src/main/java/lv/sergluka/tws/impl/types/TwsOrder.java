package lv.sergluka.tws.impl.types;

import com.ib.client.Order;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class TwsOrder {

    private final int id;
    private List<TwsOrderStatus> statuses = new LinkedList<>();

    public TwsOrder(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public TwsOrderStatus getStatus() {
        return statuses.get(statuses.size()-1);
    }

    public List<TwsOrderStatus> getStatuses() {
        return statuses;
    }

    public void addStatus(TwsOrderStatus status) {
        this.statuses.add(status);
    }

    public boolean statusExists(TwsOrderStatus status) {
        return statuses.stream().anyMatch(it -> Objects.equals(it, status));
    }
}
