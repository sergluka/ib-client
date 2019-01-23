package com.finplant.ib.impl.request;

import java.util.Objects;

public class RequestKey {
    private final RequestRepository.Type type;
    private final Integer id;

    RequestKey(RequestRepository.Type type, Integer id) {
        this.type = type;
        this.id = id;
    }

    public RequestRepository.Type getType() {
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

        RequestKey typeKey = (RequestKey) obj;
        return (type == null || type == typeKey.type) && Objects.equals(id, typeKey.id);
    }

    @Override
    public int hashCode() {
        return 0;
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
