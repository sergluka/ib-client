package lv.sergluka.ib_client.types;

import com.ib.client.DepthMktDataDescription;

@SuppressWarnings({"WeakerAccess", "unused"})
public class IbDepthMktDataDescription {
    private final DepthMktDataDescription parent;

    public IbDepthMktDataDescription(DepthMktDataDescription parent) {
        this.parent = parent;
    }

    public String getExchange() {
        return parent.exchange();
    }

    public String getSecType() {
        return parent.secType();
    }

    public String getListingExch() {
        return parent.listingExch();
    }

    public String getServiceDataType() {
        return parent.serviceDataType();
    }

    public int getAggGroup() {
        return parent.aggGroup();
    }

    public void setExchange(String exchange) {
        parent.exchange(exchange);
    }

    public void setSecType(String secType) {
        parent.secType(secType);
    }

    public void setListingExch(String listingExch) {
        parent.listingExch(listingExch);
    }

    public void setServiceDataType(String serviceDataType) {
        parent.serviceDataType(serviceDataType);
    }

    public void setAggGroup(int aggGroup) {
        parent.aggGroup(aggGroup);
    }

    @Override
    public String toString() {
        final StringBuilder buffer = new StringBuilder("{");
        buffer.append("exchange='").append(getExchange()).append('\'');
        buffer.append(", secType='").append(getSecType()).append('\'');
        buffer.append(", listingExch='").append(getListingExch()).append('\'');
        buffer.append(", serviceDataType='").append(getServiceDataType()).append('\'');
        buffer.append(", aggGroup=").append(getAggGroup());
        buffer.append('}');
        return buffer.toString();
    }
}
