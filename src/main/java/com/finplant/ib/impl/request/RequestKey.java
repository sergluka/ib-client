package com.finplant.ib.impl.request;

import java.util.Comparator;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;

import com.google.common.collect.ComparisonChain;

public class RequestKey implements Comparable<RequestKey> {
    private final RequestRepository.Type type;
    private final Integer id;

    RequestKey(RequestRepository.Type type, Integer id) {
        this.type = type;
        this.id = id;
    }

    @Override
    public int compareTo(@NotNull RequestKey rhs) {
        if (this == rhs) {
            return 0;
        }

        ComparisonChain chain = ComparisonChain.start();
        if (type != null) {
            chain = chain.compare(type, rhs.type, Comparator.nullsFirst(Comparator.naturalOrder()));
        }
        chain = chain.compare(id, rhs.id, Comparator.nullsFirst(Comparator.naturalOrder()));

        return chain.result();
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

    public RequestRepository.Type getType() {
        return type;
    }

    public Integer getId() {
        return id;
    }
}
