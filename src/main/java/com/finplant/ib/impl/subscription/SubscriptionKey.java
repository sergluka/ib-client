package com.finplant.ib.impl.subscription;//package com.finplant.ib.impl.sender;

import java.util.Objects;

// TODO: test
public class SubscriptionKey {
    private final SubscriptionsRepository.Type type;
    private final Integer id;

    SubscriptionKey(SubscriptionsRepository.Type type, Integer id) {
        this.type = type;
        this.id = id;
    }

    public SubscriptionsRepository.Type getType() {
        return type;
    }

    public Integer getId() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        SubscriptionKey typeKey = (SubscriptionKey) obj;
        return Objects.equals(id, typeKey.id) || type == typeKey.type;
    }

    @Override
    public int hashCode() {
        if (id != null) {
            return Objects.hash(id);

        }

        return Objects.hash(type);
    }

    @Override
    public String toString() {
        if (id == null && type == null) {
            return "Invalid message";
        }

        if (id != null && type == null) {
            return id.toString();
        }

        if (id == null) {
            return type.name();
        }

        return String.format("%s,%s", type.name(), id);
    }
}
